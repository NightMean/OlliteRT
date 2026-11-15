#!/usr/bin/env python3
"""Full lifecycle battery for build 1233 / b59a2f97 (2026-11-14 rebuild).

Topology on the phone: slot 1 (primary) = EmbeddingGemma-2-740M,
slot 2 (secondary) = Gemma-4-E2B-it (2 instances).

T0 baseline chat+embed both 200 (warm)
T1 unload secondary via API -> /v1/models = 1, chat to it -> clean 400
T2 load secondary via API -> IMMEDIATE chat, NO retry (first-request-500 regression)
T3 concurrent: solo 150-tok chat vs chat+embed batch-4 same instant (pass criterion 5)
T4 /v1/server/reload -> poll /v1/models ready -> IMMEDIATE chat, NO retry
   (if the secondary does not come back with the reload, record that and
   re-load it via API, then do the first-request check on that load)

State changes use the patient-runner pattern (pre-ping, retries, backoff).
The two first-request checks are strict: exactly one attempt.
"""
import json, time, threading, urllib.request

BASE = "http://192.168.0.35:8000"
CHAT = "Gemma-4-E2B-it"
EMBED = "EmbeddingGemma-2-740M"
OUT = "/home/mintvm/.hermes/cache/scratch/ollitert/lifecycle_1233_results.json"

results = {}

def req(path, body=None, timeout=300, method=None):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(BASE + path, data=data,
                               headers={"Content-Type": "application/json"},
                               method=method or ("POST" if data else "GET"))
    t0 = time.time()
    try:
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            return resp.status, json.loads(resp.read()), time.time() - t0
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            payload = json.loads(raw or b"{}")
        except Exception:
            payload = {"raw": raw.decode(errors="replace")[:200]}
        return e.code, payload, time.time() - t0
    except (urllib.error.URLError, ConnectionResetError, OSError) as e:
        return 0, {"error": str(e)}, time.time() - t0

def patient(fn, attempts=8, what="op"):
    """State-change op: pre-ping, retries with growing backoff (server drops TCP)."""
    last = None
    for i in range(attempts):
        req("/ping", timeout=5)
        st, p, dt = fn()
        if st != 0:
            return st, p, dt
        last = p
        sleep = 5 * (i + 1) + (10 if i >= 2 else 0)
        print(f"    {what}: drop ({str(last)[:80]}), retry {i+1}/{attempts-1} in {sleep}s", flush=True)
        time.sleep(sleep)
    return 0, last, -1

def models():
    st, p, _ = req("/v1/models", timeout=10)
    if st == 200:
        return [m.get("id") for m in p.get("data", []) if isinstance(m, dict)]
    return None

def chat(mlx, prompt="Reply with the single word: pong", max_tokens=20):
    return req("/v1/chat/completions",
               {"model": mlx, "messages": [{"role": "user", "content": prompt}],
                "max_tokens": max_tokens, "temperature": 0}, timeout=300)

def embed(n=4):
    texts = ["turn on the kitchen light", "open the garage door",
             "what is the weather like outside", "lock the front door"][:n]
    return req("/v1/embeddings", {"model": EMBED, "input": texts, "dimensions": 768}, timeout=120)

# ---------------- T0 baseline ----------------
print("T0: baseline warm chat (E2B) + embed (740M)", flush=True)
st, p, dt = patient(lambda: chat(CHAT), what="T0 chat")
t0_chat = {"status": st, "wall_s": round(dt, 1),
           "content": (p.get("choices", [{}])[0].get("message", {}).get("content") or str(p))[:60]}
print(f"  chat: {st} in {dt:.1f}s | {t0_chat['content']}")
st, p, dt = patient(lambda: embed(), what="T0 embed")
vecs = p.get("data", [])
norms = [round(sum(v*v for v in d["embedding"]) ** 0.5, 3) for d in vecs]
t0_embed = {"status": st, "wall_s": round(dt, 2), "n_vectors": len(vecs), "norms": norms}
print(f"  embed: {st} in {dt:.2f}s, {len(vecs)} vecs, norms {norms}")
results["T0_baseline"] = {"chat": t0_chat, "embed": t0_embed,
                          "pass": t0_chat["status"] == 200 and t0_embed["status"] == 200 and len(vecs) == 4}

# ---------------- T1 unload secondary ----------------
print("T1: unload secondary (Gemma-4-E2B-it) via API", flush=True)
st, p, dt = patient(lambda: req("/v1/server/models/unload", {"name": CHAT}), what="T1 unload")
print(f"  unload: {st} in {dt:.1f}s {json.dumps(p)[:100]}")
time.sleep(3)
ms = models()
print(f"  /v1/models now: {ms}")
st, p, dt = chat(CHAT)
print(f"  chat to unloaded model: {st} in {dt:.1f}s | {json.dumps(p)[:140]}")
st2, p2, _ = req("/v1/model/2", timeout=10)
print(f"  slot-2 probe: {st2} {json.dumps(p2)[:80]}")
results["T1_unload"] = {"unload_status": st, "models_after": ms,
                        "chat_unloaded_status": st,
                        "chat_unloaded_body": json.dumps(p)[:160],
                        "slot2_probe": {"status": st2, "body": json.dumps(p2)[:80]},
                        "pass": st == 200 and ms == [EMBED] and st == 400}

# ---------------- T2 load secondary -> IMMEDIATE chat, no retry ----------------
print("T2: load secondary via API, IMMEDIATE first chat (no retry)", flush=True)
st, p, dt = patient(lambda: req("/v1/server/models/load", {"name": CHAT}), what="T2 load")
print(f"  load: {st} in {dt:.1f}s {json.dumps(p)[:100]}")
st, p, dt = chat(CHAT)
c = (p.get("choices", [{}])[0].get("message", {}).get("content") or str(p))[:60]
print(f"  FIRST chat after load: {st} in {dt:.1f}s | {c}")
results["T2_load_first_request"] = {"load_status": st, "first_chat_status": st,
                                    "first_chat_wall_s": round(dt, 1), "content": c,
                                    "pass": st == 200}

# ---------------- T3 concurrent (pass criterion 5) ----------------
print("T3: solo chat 150-tok vs concurrent chat+embed batch-4", flush=True)
def chat150():
    t0 = time.time()
    st, p, _ = patient(lambda: req("/v1/chat/completions",
                                   {"model": CHAT,
                                    "messages": [{"role": "user",
                                                  "content": "Write a short descriptive paragraph (about 150 words) about a lighthouse keeper on a stormy night. Then list 3 things he would check before the storm hits."}],
                                    "max_tokens": 150, "temperature": 0.8}), what="T3 chat")
    wall = time.time() - t0
    content = (p.get("choices", [{}])[0].get("message", {}).get("content") or "")
    tok = p.get("usage", {}).get("completion_tokens") or len(content.split())
    return {"status": st, "wall_s": round(wall, 1), "tokens": tok,
            "tok_s": round(tok / wall, 2) if wall > 0 else 0,
            "content_len": len(content), "valid": st == 200 and len(content) > 100}

def embed4():
    t0 = time.time()
    st, p, _ = patient(lambda: embed(4), what="T3 embed")
    wall = time.time() - t0
    vecs = p.get("data", [])
    return {"status": st, "wall_s": round(wall, 2), "n_vectors": len(vecs),
            "dims": len(vecs[0]["embedding"]) if vecs else 0,
            "valid": st == 200 and len(vecs) == 4 and vecs[0]["embedding"].__len__() == 768}

solo = chat150()
print(f"  solo: {solo}")
time.sleep(3)
out = {}
def do_c(): out["chat"] = chat150()
def do_e(): out["embed"] = embed4()
t0 = time.time()
t1, t2 = threading.Thread(target=do_c), threading.Thread(target=do_e)
t1.start(); t2.start(); t1.join(); t2.join()
conc_wall = round(time.time() - t0, 1)
delta = round((solo["tok_s"] - out["chat"]["tok_s"]) / solo["tok_s"] * 100, 1) if solo["tok_s"] else 0
print(f"  concurrent wall {conc_wall}s: chat {out['chat']}")
print(f"  concurrent: embed {out['embed']}")
print(f"  throughput delta: {delta}%")
results["T3_concurrent"] = {"solo": solo, "concurrent_wall_s": conc_wall,
                            "chat": out["chat"], "embed": out["embed"],
                            "throughput_delta_pct": delta,
                            "pass": solo["valid"] and out["chat"]["valid"] and out["embed"]["valid"] and delta <= 20.0}

# ---------------- T4 primary reload -> IMMEDIATE chat, no retry ----------------
print("T4: /v1/server/reload, poll ready, IMMEDIATE first chat (no retry)", flush=True)
st, p, dt = patient(lambda: req("/v1/server/reload", {}), what="T4 reload")
print(f"  reload: {st} in {dt:.1f}s {json.dumps(p)[:80]}")
ready, ms = False, None
for i in range(60):  # up to 5 min
    ms = models()
    if ms and CHAT in ms:
        ready = True
        break
    time.sleep(5)
time.sleep(3)
note = ""
if not ready:
    note = f"secondary NOT in /v1/models after reload (models={ms}) — re-loading via API"
    print("  " + note, flush=True)
    st, p, dt = patient(lambda: req("/v1/server/models/load", {"name": CHAT}), what="T4 re-load")
    print(f"  re-load: {st} in {dt:.1f}s")
    time.sleep(2)
st, p, dt = chat(CHAT)
c = (p.get("choices", [{}])[0].get("message", {}).get("content") or str(p))[:60]
print(f"  FIRST chat after reload: {st} in {dt:.1f}s | {c}")
results["T4_reload_first_request"] = {"reload_status": st, "ready_via_poll": ready,
                                      "note": note, "models_after": ms,
                                      "first_chat_status": st, "first_chat_wall_s": round(dt, 1),
                                      "content": c, "pass": st == 200}

results["summary"] = {k: v.get("pass") for k, v in results.items() if isinstance(v, dict) and "pass" in v}
print(json.dumps(results["summary"], indent=2))
with open(OUT, "w") as f:
    json.dump(results, f, indent=2)
print("saved", OUT)

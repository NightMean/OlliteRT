#!/usr/bin/env python3
"""OlliteRT model lifecycle battery: load/unload, dual-load, second-model verification.
Stdlib only. Patient runner: retries on connection drops, logs uptime to detect crashes.
Phases 0-5 (API only). UI Replace phase done separately via uiautomator.
"""
import json, time, threading, urllib.request, urllib.error, sys

BASE = "http://192.168.0.35:8000"
RESULTS = []

def log(phase, check, ok, detail=""):
    RESULTS.append({"phase": phase, "check": check, "ok": bool(ok), "detail": str(detail)[:300]})
    print(f"[{phase}] {'PASS' if ok else 'FAIL'}  {check}  {str(detail)[:160]}", flush=True)

def req(method, path, body=None, timeout=180, attempts=8):
    """Returns (status, parsed_json). Retries only on transport-level drops."""
    last = None
    for i in range(attempts):
        try:
            r = urllib.request.Request(BASE + path,
                                       data=json.dumps(body).encode() if body is not None else None,
                                       method=method)
            r.add_header("Content-Type", "application/json")
            with urllib.request.urlopen(r, timeout=timeout) as resp:
                raw = resp.read().decode()
                return resp.status, (json.loads(raw) if raw else None)
        except urllib.error.HTTPError as e:
            raw = e.read().decode()
            try:
                return e.code, json.loads(raw)
            except Exception:
                return e.code, raw
        except Exception as e:
            last = e
            time.sleep(5 * (i + 1))
    return None, f"transport failure after {attempts} tries: {last}"

def get_state():
    st, data = req("GET", "/")
    return data

def uptime():
    d = get_state()
    return d.get("uptime_seconds") if isinstance(d, dict) else None

def models():
    st, data = req("GET", "/v1/models")
    return [m["id"] for m in data.get("data", [])] if isinstance(data, dict) else []

def chat(model, prompt, max_tokens=40, timeout=300):
    t0 = time.time()
    st, data = req("POST", "/v1/chat/completions",
                   {"model": model, "messages": [{"role": "user", "content": prompt}],
                    "max_tokens": max_tokens, "temperature": 0}, timeout=timeout)
    dt = time.time() - t0
    content = ""
    if isinstance(data, dict) and data.get("choices"):
        content = data["choices"][0].get("message", {}).get("content", "")
    return st, content, dt

def embed(texts, timeout=120):
    t0 = time.time()
    st, data = req("POST", "/v1/embeddings",
                   {"model": "EmbeddingGemma-2-740M", "input": texts}, timeout=timeout)
    dt = time.time() - t0
    n = len(data.get("data", [])) if isinstance(data, dict) else 0
    dim = len(data["data"][0]["embedding"]) if n else 0
    return st, n, dim, dt

def unload(name):
    return req("POST", "/v1/server/models/unload", {"name": name}, timeout=600)

def load(name):
    t0 = time.time()
    st, data = req("POST", "/v1/server/models/load", {"name": name}, timeout=900)
    return st, data, time.time() - t0

print("=== PHASE 0: baseline ===", flush=True)
u0 = uptime()
m0 = models()
print("baseline models:", m0, "uptime:", u0)
st, n, dim, dt = embed(["hello world"])
log("P0", "baseline embed", st == 200 and n == 1 and dim == 768, f"st={st} n={n} dim={dim} {dt:.2f}s")
st, c, dt = chat("Gemma-4-E2B-it", "Say the word: pong")
log("P0", "baseline chat E2B (slot2)", st == 200 and "pong" in c.lower(), f"st={st} {dt:.1f}s {c[:60]!r}")

print("=== T1: unload secondary (E2B), verify, reload ===", flush=True)
st, data = unload("Gemma-4-E2B-it")
log("T1", "unload E2B accepted", st == 200, f"st={st} {str(data)[:100]}")
time.sleep(3)
m = models()
log("T1", "E2B gone from /v1/models", m == ["EmbeddingGemma-2-740M"], m)
d = get_state()
log("T1", "secondary_model null in GET /", isinstance(d, dict) and d.get("secondary_model") in (None, ""), d.get("secondary_model"))
st, c, dt = chat("Gemma-4-E2B-it", "Say pong")
log("T1", "chat to unloaded model -> 400", st == 400, f"st={st} {str(c)[:80] if isinstance(c,str) else str(data)[:80]}")
st, n, dim, dt = embed(["still here"])
log("T1", "embed primary still serves", st == 200 and n == 1, f"st={st} {dt:.2f}s")
u1 = uptime()
log("T1", "server survived (uptime monotonic)", u1 and u0 and u1 > u0, f"{u0}->{u1}")

st, data, dt = load("Gemma-4-E2B-it")
log("T1", "reload E2B as second", st == 200, f"st={st} {dt:.1f}s {str(data)[:80]}")
m = models()
log("T1", "both resident after reload", set(m) == {"EmbeddingGemma-2-740M", "Gemma-4-E2B-it"}, m)
st, c, dt = chat("Gemma-4-E2B-it", "Say the word: pong")
log("T1", "E2B answers after reload", st == 200 and "pong" in c.lower(), f"st={st} {dt:.1f}s {c[:50]!r}")

print("=== T3: swap second model to E4B (cold load) ===", flush=True)
st, data = unload("Gemma-4-E2B-it")
log("T3", "unload E2B again", st == 200, f"st={st}")
t0 = time.time()
st, data, dt = load("Gemma-4-E4B-it")
log("T3", "cold-load E4B as second", st == 200, f"st={st} {dt:.1f}s {str(data)[:80]}")
m = models()
log("T3", "embed+E4B resident", set(m) == {"EmbeddingGemma-2-740M", "Gemma-4-E4B-it"}, m)

print("=== T4: verify second model (E4B) ===", flush=True)
st, data = req("GET", "/v1/model/2")
log("T4", "slot probe /v1/model/2 -> E4B", st == 200 and isinstance(data, dict) and data.get("model") == "Gemma-4-E4B-it", str(data)[:100])
st, c, dt = chat("Gemma-4-E4B-it", "Say the word: pong", max_tokens=32)
log("T4", "chat by full name -> E4B", st == 200 and "pong" in c.lower(), f"st={st} {dt:.1f}s {c[:60]!r}")
st, c, dt = chat("totally-bogus-model", "Say the word: pong", max_tokens=32)
ok = st == 400 and isinstance(c, str) and "Gemma-4-E4B-it" in c
log("T4", "bogus name -> 400 listing both", ok, f"st={st} {str(c)[:150]}")
# slot route with deliberately-wrong model field (slot must win)
t0 = time.time()
st, data = req("POST", "/v1/model/2/chat/completions",
               {"model": "not-the-real-name", "messages": [{"role": "user", "content": "Say the word: pong"}],
                "max_tokens": 32, "temperature": 0})
dt = time.time() - t0
content = data["choices"][0]["message"]["content"] if isinstance(data, dict) and data.get("choices") else ""
log("T4", "slot-2 route wins over model field", st == 200 and "pong" in content.lower(), f"st={st} {dt:.1f}s {content[:50]!r}")

# concurrent: embed batch + E4B chat, same instant
res = {}
def do_embed():
    res["embed"] = embed(["alpha beta gamma", "delta epsilon zeta"])
def do_chat():
    res["chat"] = chat("Gemma-4-E4B-it", "Count from 1 to 30, one per line.", max_tokens=120, timeout=300)
t0 = time.time()
t1 = threading.Thread(target=do_embed); t2 = threading.Thread(target=do_chat)
t1.start(); t2.start(); t1.join(); t2.join()
wall = time.time() - t0
e = res.get("embed", (None,)); c = res.get("chat", (None,))
log("T4", "concurrent embed+chat both 200", e[0] == 200 and c[0] == 200,
    f"embed {e[1]}/{e[2]}dim {e[3]:.2f}s | chat st={c[0]} {c[2]:.1f}s | wall {wall:.1f}s")

# busy-guard: unload E4B while a long chat is in flight
res2 = {}
def long_chat():
    res2["chat"] = chat("Gemma-4-E4B-it", "Count from 1 to 120, one number per line.", max_tokens=400, timeout=600)
t = threading.Thread(target=long_chat); t.start()
time.sleep(6)
st, data = unload("Gemma-4-E4B-it")
log("T4", "unload during in-flight chat -> 400 busy", st == 400, f"st={st} {str(data)[:120]}")
t.join()
stc = res2.get("chat", (None,))
log("T4", "long chat completed anyway", stc[0] == 200, f"st={stc[0]} {stc[2]:.1f}s len={len(stc[1]) if stc[1] else 0}")
st, data = unload("Gemma-4-E4B-it")
log("T4", "unload E4B now idle", st == 200, f"st={st} {str(data)[:80]}")
m = models()
log("T4", "E4B gone", m == ["EmbeddingGemma-2-740M"], m)

u_end = uptime()
log("FINAL", "no crash across all phases (uptime monotonic)", u_end and u0 and u_end > u1, f"{u0}->{u1}->{u_end}")

with open("/home/mintvm/.hermes/cache/scratch/ollitert/lifecycle_results.json", "w") as f:
    json.dump(RESULTS, f, indent=1)
fails = [r for r in RESULTS if not r["ok"]]
print(f"\n=== DONE: {len(RESULTS) - len(fails)}/{len(RESULTS)} passed ===")
for r in fails:
    print("FAIL:", r["phase"], r["check"], r["detail"][:120])

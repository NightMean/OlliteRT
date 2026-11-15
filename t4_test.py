#!/usr/bin/env python3
"""T4: second-model (E2B) verification suite on embed+E2B 1+1."""
import json, time, threading, urllib.request, urllib.error

BASE = "http://192.168.0.35:8000"
R = []

def log(check, ok, detail=""):
    R.append({"check": check, "ok": bool(ok), "detail": str(detail)[:200]})
    print(f"{'PASS' if ok else 'FAIL'}  {check}  {str(detail)[:140]}", flush=True)

def req(method, path, body=None, timeout=300, attempts=6):
    last = None
    for i in range(attempts):
        try:
            r = urllib.request.Request(BASE + path, data=json.dumps(body).encode() if body is not None else None, method=method)
            r.add_header("Content-Type", "application/json")
            with urllib.request.urlopen(r, timeout=timeout) as resp:
                raw = resp.read().decode()
                return resp.status, (json.loads(raw) if raw else None)
        except urllib.error.HTTPError as e:
            raw = e.read().decode()
            try: return e.code, json.loads(raw)
            except Exception: return e.code, raw
        except Exception as e:
            last = e; time.sleep(4 * (i + 1))
    return None, f"transport failure: {last}"

def chat(model, prompt, max_tokens=40, timeout=300):
    t0 = time.time()
    st, data = req("POST", "/v1/chat/completions",
                   {"model": model, "messages": [{"role": "user", "content": prompt}],
                    "max_tokens": max_tokens, "temperature": 0}, timeout=timeout)
    dt = time.time() - t0
    c = ""
    if isinstance(data, dict) and data.get("choices"):
        c = data["choices"][0].get("message", {}).get("content", "")
    return st, c, dt

def embed(texts):
    t0 = time.time()
    st, data = req("POST", "/v1/embeddings", {"model": "EmbeddingGemma-2-740M", "input": texts})
    dt = time.time() - t0
    n = len(data.get("data", [])) if isinstance(data, dict) else 0
    dim = len(data["data"][0]["embedding"]) if n else 0
    return st, n, dim, dt

print("=== T4: second-model verification (E2B) ===", flush=True)

st, d = req("GET", "/v1/model/2")
log("slot probe /v1/model/2 -> E2B", st == 200 and isinstance(d, dict) and d.get("model") == "Gemma-4-E2B-it", str(d)[:90])
st, d = req("GET", "/v1/model/1")
log("slot probe /v1/model/1 -> embed", st == 200 and isinstance(d, dict) and d.get("model") == "EmbeddingGemma-2-740M", str(d)[:90])

st, c, dt = chat("Gemma-4-E2B-it", "Say the word: pong")
log("chat by full name -> 200", st == 200 and "pong" in c.lower(), f"{dt:.1f}s {c[:50]!r}")

st, c, dt = chat("gemma-4-e2b", "Say the word: pong")
log("short/case-variant name -> 400 (exact match rule)", st == 400, f"st={st} {str(c)[:100]}")

st, c, dt = chat("totally-bogus-model", "Say the word: pong")
ok = st == 400 and isinstance(c, str) and "Gemma-4-E2B-it" in c and "EmbeddingGemma" in c
log("bogus name -> 400 listing BOTH models", ok, f"st={st} {str(c)[:140]}")

st, data = req("POST", "/v1/model/2/chat/completions",
               {"model": "wrong-name-on-purpose", "messages": [{"role": "user", "content": "Say the word: pong"}], "max_tokens": 32, "temperature": 0})
c = data["choices"][0]["message"]["content"] if isinstance(data, dict) and data.get("choices") else ""
log("slot-2 route wins over model field", st == 200 and "pong" in c.lower(), f"{c[:50]!r}")

st, data = req("POST", "/v1/model/1/embeddings", {"model": "also-wrong-name", "input": ["slot test doc"]})
ok = st == 200 and isinstance(data, dict) and len(data.get("data", [])) == 1
log("slot-1 embed route wins over model field", ok, f"st={st}")

res = {}
def do_embed(): res["e"] = embed(["alpha beta gamma", "delta epsilon zeta"])
def do_chat(): res["c"] = chat("Gemma-4-E2B-it", "Count from 1 to 30, one per line.", max_tokens=120)
t0 = time.time()
t1, t2 = threading.Thread(target=do_embed), threading.Thread(target=do_chat)
t1.start(); t2.start(); t1.join(); t2.join()
wall = time.time() - t0
e, c = res.get("e", (None,)), res.get("c", (None,))
log("concurrent embed+chat both 200", e[0] == 200 and c[0] == 200, f"embed {e[1]}/{e[2]}dim {e[3]:.2f}s | chat st={c[0]} {c[2]:.1f}s | wall {wall:.1f}s")

res2 = {}
def long_chat(): res2["c"] = chat("Gemma-4-E2B-it", "Count from 1 to 120, one number per line.", max_tokens=400, timeout=600)
t = threading.Thread(target=long_chat); t.start()
time.sleep(6)
st, data = req("POST", "/v1/server/models/unload", {"name": "Gemma-4-E2B-it"})
log("unload during in-flight chat -> 400 busy", st == 400, f"st={st} {str(data)[:110]}")
t.join()
c2 = res2.get("c", (None,))
log("in-flight chat completed", c2[0] == 200, f"st={c2[0]} {c2[2]:.1f}s len={len(c2[1]) if c2[1] else 0}")
st, data = req("POST", "/v1/server/models/unload", {"name": "Gemma-4-E2B-it"})
log("unload when idle -> 200", st == 200, f"st={st} {str(data)[:80]}")
st, d = req("GET", "/v1/models")
log("E2B gone from /v1/models", [m["id"] for m in d.get("data", [])] == ["EmbeddingGemma-2-740M"], str(d)[:90])

# reload for the remaining tests
st, data = req("POST", "/v1/server/models/load", {"name": "Gemma-4-E2B-it"}, timeout=300)
log("reload E2B (final state)", st == 200, f"st={st}")

u1 = req("GET", "/")[1].get("uptime_seconds")
log("server uptime healthy", isinstance(u1, int) and u1 > 0, f"uptime={u1}s")

json.dump(R, open("/home/mintvm/.hermes/cache/scratch/ollitert/t4_results.json", "w"), indent=1)
print(f"\n=== T4 DONE: {sum(1 for r in R if r['ok'])}/{len(R)} passed ===")

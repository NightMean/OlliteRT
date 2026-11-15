#!/usr/bin/env python3
"""Characterize the intermittent secondary-model Invalid-context 500.
Cycles: load E2B -> chat (immediate / after 60s idle) -> record status -> unload.
"""
import json, time, urllib.request, urllib.error

BASE = "http://192.168.0.35:8000"

def req(method, path, body=None, timeout=300):
    for i in range(5):
        try:
            r = urllib.request.Request(BASE + path, data=json.dumps(body).encode() if body is not None else None, method=method)
            r.add_header("Content-Type", "application/json")
            with urllib.request.urlopen(r, timeout=timeout) as resp:
                raw = resp.read().decode()
                return resp.status, (json.loads(raw) if raw else None)
        except urllib.error.HTTPError as e:
            return e.code, e.read().decode()[:150]
        except Exception as e:
            time.sleep(3 * (i + 1))
    return None, "transport failure"

def chat_once(tag):
    t0 = time.time()
    st, data = req("POST", "/v1/chat/completions",
                   {"model": "Gemma-4-E2B-it", "messages": [{"role": "user", "content": "Say the word: pong"}],
                    "max_tokens": 32, "temperature": 0})
    dt = time.time() - t0
    ok = st == 200 and "pong" in json.dumps(data).lower()
    print(f"  {tag}: HTTP {st} in {dt:.1f}s {'OK' if ok else 'FAIL'}", flush=True)
    return ok

results = []
for cycle in range(3):
    print(f"=== cycle {cycle+1}: load -> immediate chat ===", flush=True)
    st, d = req("POST", "/v1/server/models/unload", {"name": "Gemma-4-E2B-it"})
    time.sleep(3)
    st, d = req("POST", "/v1/server/models/load", {"name": "Gemma-4-E2B-it"}, timeout=300)
    print(f"  load: {st}", flush=True)
    time.sleep(4)  # let warmup finish
    ok = chat_once(f"cycle{cycle+1}-immediate")
    results.append({"cycle": cycle + 1, "mode": "immediate", "ok": ok})
    # keep loaded for next cycle's unload

print("=== cycle 4: load -> 60s idle -> chat ===", flush=True)
st, d = req("POST", "/v1/server/models/unload", {"name": "Gemma-4-E2B-it"})
time.sleep(3)
st, d = req("POST", "/v1/server/models/load", {"name": "Gemma-4-E2B-it"}, timeout=300)
print("  load OK, idling 60s...", flush=True)
time.sleep(60)
ok = chat_once("cycle4-idle60")
results.append({"cycle": 4, "mode": "idle60", "ok": ok})
if not ok:
    ok2 = chat_once("cycle4-idle60-retry")
    results.append({"cycle": 4, "mode": "idle60-retry", "ok": ok2})

json.dump(results, open("/home/mintvm/.hermes/cache/scratch/ollitert/intermittent_results.json", "w"), indent=1)
print(f"\n=== {sum(1 for r in results if r['ok'])}/{len(results)} ok ===")

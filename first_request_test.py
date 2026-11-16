#!/usr/bin/env python3
"""First-request-after-load test (no retries). The 2026-11-14 bug: first chat
request after a model (re)load -> 500 'Failed to invoke the compiled model'.
Fix under test: warmup burns the failure (reset + retry inside warmup)."""
import json, time, urllib.request

BASE = "http://192.168.0.35:8000"

def req(path, body=None, timeout=300):
    r = urllib.request.Request(
        BASE + path,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"Content-Type": "application/json"},
    )
    t0 = time.time()
    try:
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            return resp.status, json.loads(resp.read()), time.time() - t0
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b"{}"), time.time() - t0
    except (urllib.error.URLError, ConnectionResetError, OSError) as e:
        # Server down mid-reload etc. — report as status 0, never crash the run.
        return 0, {"error": str(e)}, time.time() - t0

results = []

def record(name, st, payload, dt, first_ok):
    results.append({"test": name, "status": st, "first_request_ok": first_ok, "wall_s": round(dt, 2)})
    content = ""
    try:
        content = payload["choices"][0]["message"]["content"][:80]
    except Exception:
        content = json.dumps(payload)[:120]
    print(f"  {name}: HTTP {st} in {dt:.1f}s first_ok={first_ok} | {content}")

# --- Test 1: second model load (API) -> immediate first chat request, NO retry
print("T1: load second model via API, first request immediately, no retry")
st, p, dt = req("/v1/server/models/load", {"name": "Gemma-4-E2B-it"}, timeout=300)
print(f"  load: HTTP {st} in {dt:.1f}s {json.dumps(p)[:100]}")
results.append({"test": "load_secondary", "status": st})

st, p, dt = req("/v1/chat/completions",
                {"model": "Gemma-4-E2B-it", "messages": [{"role": "user", "content": "Reply with the single word: pong"}],
                 "max_tokens": 20, "temperature": 0})
record("T1 first chat after 2nd-model load", st, p, dt, st == 200)

# --- Test 2: primary model reload (stop+start not possible via API; use /v1/server/reload)
print("T2: primary model reload, first request immediately, no retry")
st, p, dt = req("/v1/server/reload", {}, timeout=30)
print(f"  reload: HTTP {st} {json.dumps(p)[:80]}")
# Wait until the model is back AND ready (ping alone returns while models
# are unloading — a request in that window legitimately gets 503).
for i in range(90):
    st2, p2, _ = req("/v1/models", timeout=5)
    if st2 == 200:
        ids = [m.get("id") for m in p2.get("data", []) if isinstance(m, dict)]
        if "Gemma-4-E2B-it" in ids:
            break
    time.sleep(5)
time.sleep(3)
st, p, dt = req("/v1/chat/completions",
                {"model": "Gemma-4-E2B-it", "messages": [{"role": "user", "content": "Reply with the single word: pong"}],
                 "max_tokens": 20, "temperature": 0})
record("T2 first chat after primary reload", st, p, dt, st == 200)

with open("/home/mintvm/.hermes/cache/scratch/ollitert/first_request_test_results.json", "w") as f:
    json.dump(results, f, indent=2)
print(json.dumps(results, indent=2))
fails = [r for r in results if r.get("status") == 500]
print("VERDICT:", "PASS - no first-request 500s" if not fails else f"FAIL - {len(fails)} first-request 500s")

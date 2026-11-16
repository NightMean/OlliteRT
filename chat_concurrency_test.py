#!/usr/bin/env python3
"""
Concurrent-chat throughput test: does OlliteRT run N parallel /v1/chat/completions
on the phone, or serialize them? (Gemma-4-E4B on CPU)

Metrics (all vs solo T1):
  seq2    : 2 requests SEQUENTIALLY  -> sanity, expect ~2*T1
  conc2   : 2 requests IN PARALLEL   -> ~2*T1 = serialized | ~1*T1 = parallel
  conc4   : 4 requests IN PARALLEL   -> ~4*T1 = serialized | ~1*T1 = parallel

VERDICT on conc4/T1:  <1.6 PARALLEL | 1.6-3.0 PARTIAL | >3.0 SERIALIZED
Stdlib only.
"""
import json, time, urllib.request, sys, concurrent.futures

BASE = "http://192.168.0.35:8000"
MODEL = "Gemma-4-E4B-it"
PROMPT = "Count from 1 to 20, one number per line. Nothing else."
MAX_TOKENS = 40
TEMP = 0

def body():
    return {"model": MODEL,
            "messages": [{"role": "user", "content": PROMPT}],
            "max_tokens": MAX_TOKENS, "temperature": TEMP, "stream": False}

def one_request(timeout=180):
    t0 = time.time()
    req = urllib.request.Request(BASE + "/v1/chat/completions",
        data=json.dumps(body()).encode(),
        headers={"Content-Type": "application/json"}, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            out = json.loads(r.read().decode())
        wall = time.time() - t0
        txt = out["choices"][0]["message"]["content"]
        return {"wall": round(wall, 2), "ok": True, "chars": len(txt),
                "ctok": out.get("usage", {}).get("completion_tokens")}
    except Exception as e:
        return {"wall": round(time.time() - t0, 2), "ok": False,
                "error": f"{type(e).__name__}: {e}"}

def ping():
    try:
        urllib.request.urlopen(BASE + "/ping", timeout=5).read(); return True
    except Exception:
        return False

def parallel(n, timeout=240):
    t0 = time.time()
    with concurrent.futures.ThreadPoolExecutor(max_workers=n) as ex:
        results = list(ex.map(lambda _: one_request(timeout), range(n)))
    wall = time.time() - t0
    return {"wall": round(wall, 2), "ok": all(r["ok"] for r in results),
            "n": n, "results": results}

def main():
    if not ping():
        print(json.dumps({"ok": False, "error": "server not reachable"})); return
    one_request(); time.sleep(2)      # warmup (discard)
    r1 = one_request(); time.sleep(2) # solo baseline
    t0 = time.time(); a = one_request(); b = one_request(); seq2 = time.time() - t0
    time.sleep(3)
    c2 = parallel(2); time.sleep(4)
    c4 = parallel(4)
    T1 = r1["wall"]
    res = {"model": MODEL, "prompt": PROMPT, "max_tokens": MAX_TOKENS, "temp": TEMP,
           "solo_T1": r1,
           "seq2": {"wall": round(seq2, 2), "a": a, "b": b},
           "conc2": c2, "conc4": c4}
    if T1:
        res["seq2"]["ratio_vs_T1"] = round(seq2 / T1, 2)
        res["conc2"]["ratio_vs_T1"] = round(c2["wall"] / T1, 2)
        res["conc4"]["ratio_vs_T1"] = round(c4["wall"] / T1, 2)
        r4 = c4["wall"] / T1
        res["VERDICT"] = ("PARALLEL (concurrent requests overlap)" if r4 < 1.6
                          else "PARTIAL overlap" if r4 < 3.0
                          else "SERIALIZED (requests queue, ~= N x solo)")
    print(json.dumps(res, indent=2))

if __name__ == "__main__":
    main()

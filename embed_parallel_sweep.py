#!/usr/bin/env python3
"""846-short-doc batch at explicit parallel values (1/4/8) — resolves the
41.9s-baseline ambiguity (baseline likely ran with parallel>1). Stdlib only."""
import json, time, urllib.request

BASE = "http://192.168.0.35:8000"
TAGS = ["alpha","bravo","charlie","delta","echo","foxtrot","golf","hotel",
        "india","juliet","kilo","lima","mike","november","oscar","papa"]
shorts = [f"{TAGS[i % len(TAGS)]} subsystem note {i}: check the status before the next rotation."
          for i in range(846)]

def timed(parallel, tries=2):
    body = {"model": "EmbeddingGemma-2-740M", "input": shorts}
    if parallel is not None:
        body["parallel"] = parallel
    last = None
    for i in range(tries):
        t0 = time.time()
        try:
            req = urllib.request.Request(BASE + "/v1/embeddings", data=json.dumps(body).encode(),
                                         headers={"Content-Type": "application/json"}, method="POST")
            with urllib.request.urlopen(req, timeout=900) as r:
                out = json.loads(r.read().decode())
            wall = time.time() - t0
            n = len(out.get("data", []))
            return {"parallel": parallel, "wall": round(wall, 2), "n": n,
                    "per_doc": round(wall / n, 4), "tokens": out.get("usage", {}).get("prompt_tokens")}
        except Exception as e:
            last = f"{type(e).__name__}: {e}"
            time.sleep(5 * (i + 1))
    return {"parallel": parallel, "wall": None, "error": last}

timed(1)  # warm
time.sleep(2)
res = [timed(p) for p in (1, 4, 8)]
print(json.dumps(res, indent=2))
print("baseline to match: 41.9s for 846 short docs (2026-11-11)")

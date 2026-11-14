#!/usr/bin/env python3
"""
Multi-instance embedding test with LONG (1024-token-class) docs — the RAG-realistic
case where single-engine serialization is worst (measured 2026-11-11: solo 0.79s,
2 concurrent = 1.45s = 1.84x on one engine).

Also does the REST config round-trip: model_instance_count 2 -> 1 -> 2 with reload.
Stdlib only.
"""
import json, time, math, urllib.request, urllib.error, sys
from concurrent.futures import ThreadPoolExecutor

BASE = "http://192.168.0.35:8000"
TAGS = ["alpha","bravo","charlie","delta","echo","foxtrot","golf","hotel"]

def post(path, body, timeout=120, tries=6):
    last = None
    for i in range(tries):
        t0 = time.time()
        try:
            req = urllib.request.Request(BASE + path, data=json.dumps(body).encode(),
                                         headers={"Content-Type": "application/json"}, method="POST")
            with urllib.request.urlopen(req, timeout=timeout) as r:
                out = json.loads(r.read().decode())
            return {"wall": round(time.time() - t0, 3), "ok": True, "out": out}
        except urllib.error.HTTPError as e:
            last = f"HTTP {e.code}: {e.read().decode()[:200]}"
        except Exception as e:
            last = f"{type(e).__name__}: {e}"
        time.sleep(3 * (i + 1))
    return {"wall": None, "ok": False, "error": last}

def get(path, timeout=15, tries=4):
    last = None
    for i in range(tries):
        try:
            with urllib.request.urlopen(BASE + path, timeout=timeout) as r:
                return json.loads(r.read().decode())
        except Exception as e:
            last = str(e)
        time.sleep(2 * (i + 1))
    return {"error": last}

def ping():
    try:
        urllib.request.urlopen(BASE + "/ping", timeout=5).read()
        return True
    except Exception:
        return False

def wait_server(attempts=30):
    for i in range(attempts):
        if ping():
            return True
        time.sleep(4 * (i + 1) + (10 if i >= 2 else 0))
    return False

# ~780 words of natural prose ≈ ~1000 engine tokens (safely under the 1024 cap)
PARA = ("The retrieval system indexes each document chunk into a vector store so that "
        "semantic search can rank the most relevant passages for a given query. Embeddings "
        "capture meaning at the token level, allowing near duplicate detection and clustering "
        "across a large corpus without exact string matching. The pipeline tokenizes the raw "
        "text, pads short segments, and forwards each batch to the on device model, which "
        "returns a normalized vector that downstream services compare with cosine similarity "
        "to decide which passages to pass to the answering model as context for generation.")

def longdoc(i, paras=9):
    seed = f"Appendix {i} of the systems manual covers the {TAGS[i % len(TAGS)]} subsystem in depth. "
    return seed + " ".join(PARA for _ in range(paras))

def norm(v):
    return math.sqrt(sum(x * x for x in v))

def main():
    if not wait_server():
        print(json.dumps({"ok": False, "error": "server not reachable"})); return

    info = get("/")
    emod = "EmbeddingGemma-2-740M"
    print("model_instances (GET /):", info.get("model_instances"), file=sys.stderr)

    d = [longdoc(i) for i in range(4)]
    def body(docs):
        return {"input": docs, "model": emod}

    w = post("/v1/embeddings", body(["warmup"]))
    print("warmup:", "ok" if w["ok"] else w.get("error"), file=sys.stderr)
    time.sleep(2)

    results = {"model_instances": info.get("model_instances")}

    s1 = post("/v1/embeddings", body([d[0]]))
    time.sleep(1)
    solo = post("/v1/embeddings", body([d[0]]))
    t1 = solo["wall"]
    results["solo"] = {"wall": t1, "norm": round(norm(solo["out"]["data"][0]["embedding"]), 3) if solo["ok"] else None}

    with ThreadPoolExecutor(max_workers=2) as ex:
        f1 = ex.submit(post, "/v1/embeddings", body([d[1]]))
        f2 = ex.submit(post, "/v1/embeddings", body([d[2]]))
        c1, c2 = f1.result(), f2.result()
    results["conc2"] = {"req1": c1["wall"], "req2": c2["wall"], "ok": c1["ok"] and c2["ok"],
                        "wall_max": max(c1["wall"] or 0, c2["wall"] or 0)}
    time.sleep(2)

    with ThreadPoolExecutor(max_workers=4) as ex:
        fs = [ex.submit(post, "/v1/embeddings", body([d[i]])) for i in range(4)]
        rs = [f.result() for f in fs]
    walls = [r["wall"] for r in rs]
    results["conc4"] = {"walls": walls, "ok": all(r["ok"] for r in rs),
                        "errors": [r.get("error") for r in rs if not r["ok"]],
                        "wall_max": max(w for w in walls if w is not None) if any(w is not None for w in walls) else None}

    if t1 and results["conc2"]["wall_max"] and results["conc4"]["wall_max"]:
        results["conc2_ratio"] = round(results["conc2"]["wall_max"] / t1, 2)
        results["conc4_ratio"] = round(results["conc4"]["wall_max"] / t1, 2)
        results["VERDICT"] = (
            "TRUE OVERLAP (conc2 < 1.5x solo)" if results["conc2_ratio"] < 1.5
            else ("PARTIAL OVERLAP (GPU time-sliced)" if results["conc2_ratio"] < 1.9
                  else "SERIALIZED (conc2 ~= 2x solo)"))

    # REST round-trip: count 2 -> 1 (reload) -> 2 (reload)
    rt = {}
    r = post("/v1/server/config", {"model_instance_count": 1})
    rt["set1_ok"] = r["ok"]
    for i in range(30):
        time.sleep(4)
        if ping(): break
    time.sleep(3)
    rt["after_set1"] = get("/").get("model_instances")
    time.sleep(2)
    r = post("/v1/server/config", {"model_instance_count": 2})
    rt["set2_ok"] = r["ok"]
    for i in range(30):
        time.sleep(4)
        if ping(): break
    time.sleep(3)
    rt["after_set2"] = get("/").get("model_instances")
    results["rest_roundtrip"] = rt

    with open("/home/mintvm/.hermes/cache/scratch/ollitert/multi_instance_long_results.json", "w") as f:
        json.dump(results, f, indent=2, default=str)
    print(json.dumps(results, indent=2, default=str))

if __name__ == "__main__":
    main()

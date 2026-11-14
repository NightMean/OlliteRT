#!/usr/bin/env python3
"""
Measure whether OlliteRT /v1/embeddings `parallel` (computeEmbeddingBatch) gives
true concurrency on the phone GPU, or just serializes (GPU-bound).

Key ratios (all on 1024-token docs unless noted):
  solo        : 1 doc,  parallel=1   -> T1 (baseline)
  b2_p1       : 2 docs, parallel=1   -> ~2*T1  (the OLD sequential behavior)
  b2_p2       : 2 docs, parallel=2   -> ~1*T1  (TRUE parallel) or ~2*T1 (GPU-serialized)
  b4_p4       : 4 docs, parallel=4   -> ~1*T1  (TRUE)   or ~4*T1 (serialized)
  b4_p2       : 4 docs, parallel=2   -> midpoint

A batch of N docs at parallel=N finishing near T1 (not N*T1) is the win.
Stdlib only. Survives the flaky server with pre-ping + growing backoff.
"""
import json, time, urllib.request, sys

BASE = "http://192.168.0.35:8000"
TAGS = ["alpha","bravo","charlie","delta","echo","foxtrot","golf","hotel",
        "india","juliet","kilo","lima","mike","november","oscar","papa"]

def post(path, body, timeout=120):
    req = urllib.request.Request(
        BASE + path, data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode())

def get(path, timeout=15):
    with urllib.request.urlopen(BASE + path, timeout=timeout) as r:
        return json.loads(r.read().decode())

def ping():
    try:
        urllib.request.urlopen(BASE + "/ping", timeout=5).read()
        return True
    except Exception:
        return False

def wait_server(attempts=40):
    for i in range(attempts):
        if ping():
            return True
        time.sleep(5 * (i + 1) + (10 if i >= 2 else 0))
    return False

PARA = ("The retrieval system indexes each document chunk into a vector store so that "
        "semantic search can rank the most relevant passages for a given query. Embeddings "
        "capture meaning at the token level, allowing near duplicate detection and clustering "
        "across a large corpus without exact string matching. The pipeline tokenizes the raw "
        "text, pads short segments, and forwards each batch to the on device model, which "
        "returns a normalized vector that downstream services compare with cosine similarity "
        "to decide which passages to pass to the answering model as context.")

def doc(i, paras=9):
    """~630 words of natural prose (≈750 engine tokens, safely under the 1024 cap)."""
    seed = f"Document {i} focuses on the {TAGS[i % len(TAGS)]} domain and its subsystems. "
    return seed + " ".join(PARA for _ in range(paras))

def timed(path, body, tries=6):
    last = None
    for i in range(tries):
        t0 = time.time()
        try:
            out = post(path, body)
            wall = time.time() - t0
            n = len(out.get("data", []))
            dim = len(out["data"][0]["embedding"]) if n else 0
            import math
            v0 = out["data"][0]["embedding"]
            norm = math.sqrt(sum(x * x for x in v0)) if v0 else 0
            idx = [d["index"] for d in out["data"]]
            return {"wall": round(wall, 3), "n": n, "dim": dim,
                    "norm0": round(norm, 3), "idx_ok": idx == list(range(n)),
                    "usage": out.get("usage", {})}
        except Exception as e:
            last = f"{type(e).__name__}: {e}"
            time.sleep(3 * (i + 1))
    return {"wall": None, "error": last}

def main():
    if not wait_server():
        print(json.dumps({"ok": False, "error": "server not reachable"})); return
    models = get("/v1/models")
    mids = [m.get("id") for m in models.get("data", [])]
    # pick an embedding model if we can tell; else let the server resolve
    emod = next((m for m in mids if "embed" in m.lower()), None)
    print("models:", mids, "| embedding pick:", emod, file=sys.stderr)

    d = [doc(i) for i in range(4)]
    def body(docs, parallel):
        b = {"input": docs}
        if emod: b["model"] = emod
        if parallel is not None: b["parallel"] = parallel
        return b

    # warmup (first request after load is slower; discard)
    warm = timed("/v1/embeddings", body([d[0]], 1))
    time.sleep(2)

    results = {
        "models": mids,
        "warmup": warm,
        "solo_1doc_p1":  timed("/v1/embeddings", body([d[0]], 1)),
        "b2_p1":         timed("/v1/embeddings", body(d[:2], 1)),
        "b2_p2":         timed("/v1/embeddings", body(d[:2], 2)),
        "b4_p4":         timed("/v1/embeddings", body(d[:4], 4)),
        "b4_p2":         timed("/v1/embeddings", body(d[:4], 2)),
        "short4_p4":     timed("/v1/embeddings", body([f"short text {i}" for i in range(4)], 4)),
    }

    t1 = results["solo_1doc_p1"]["wall"]
    if t1:
        for k in ["b2_p1", "b2_p2", "b4_p4", "b4_p2"]:
            w = results[k]["wall"]
            results[k]["ratio_vs_solo"] = round(w / t1, 2) if w else None
        results["VERDICT"] = (
            "PARALLEL (batch wins)" if (results["b2_p2"]["wall"] and
            results["b2_p2"]["wall"] < 1.5 * t1)
            else "GPU-SERIALIZED (batch ~= N x solo)")
    print(json.dumps(results, indent=2))

if __name__ == "__main__":
    main()

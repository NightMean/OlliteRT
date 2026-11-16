#!/usr/bin/env python3
"""
Single-model EMBEDDING regression (EmbeddingGemma alone) — new multi-instance build.

Baselines (2026-11-11/12, single model):
  1024-token doc solo        : ~0.79s
  846 short-docs (1 batch)   : 41.9s  (1.02s/doc)
  224KB batch (1 request)    : 55.3s

Stdlib only.
"""
import json, time, math, sys, urllib.request

BASE = "http://192.168.0.35:8000"
MODEL = "EmbeddingGemma-2-740M"
TAGS = ["alpha","bravo","charlie","delta","echo","foxtrot","golf","hotel",
        "india","juliet","kilo","lima","mike","november","oscar","papa"]

PARA = ("The retrieval system indexes each document chunk into a vector store so that "
        "semantic search can rank the most relevant passages for a given query. Embeddings "
        "capture meaning at the token level, allowing near duplicate detection and clustering "
        "across a large corpus without exact string matching. The pipeline tokenizes the raw "
        "text, pads short segments, and forwards each batch to the on device model, which "
        "returns a normalized vector that downstream services compare with cosine similarity "
        "to decide which passages to pass to the answering model as context.")

def doc1024(i):
    seed = f"Document {i} focuses on the {TAGS[i % len(TAGS)]} domain and its subsystems. "
    return seed + " ".join(PARA for _ in range(9))  # ~770 words, <1024 engine tokens

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

def get(path, timeout=20):
    with urllib.request.urlopen(BASE + path, timeout=timeout) as r:
        return json.loads(r.read().decode())

def timed(docs, tries=4):
    body = {"model": MODEL, "input": docs}
    last = None
    for i in range(tries):
        t0 = time.time()
        try:
            req = urllib.request.Request(BASE + "/v1/embeddings", data=json.dumps(body).encode(),
                                         headers={"Content-Type": "application/json"}, method="POST")
            with urllib.request.urlopen(req, timeout=600) as r:
                out = json.loads(r.read().decode())
            wall = time.time() - t0
            n = len(out.get("data", []))
            v0 = out["data"][0]["embedding"]
            norm = math.sqrt(sum(x * x for x in v0)) if v0 else 0
            return {"wall": round(wall, 3), "n": n, "dim": len(v0),
                    "norm0": round(norm, 3),
                    "per_doc": round(wall / n, 3) if n else None,
                    "usage": out.get("usage", {})}
        except Exception as e:
            last = f"{type(e).__name__}: {e}"
            time.sleep(3 * (i + 1))
    return {"wall": None, "error": last}

def main():
    if not wait_server():
        print(json.dumps({"ok": False, "error": "server not reachable"})); return

    info = get("/")
    models = [m["id"] for m in get("/v1/models").get("data", [])]
    results = {
        "model": info.get("model"),
        "secondary": info.get("secondary_model"),
        "model_instances": info.get("model_instances"),
        "models_listed": models,
        "SINGLE_MODEL": (info.get("model") == MODEL and not info.get("secondary_model")
                         and info.get("model_instances") == 1),
    }

    warm = timed([doc1024(0)])
    results["warmup"] = warm
    time.sleep(2)

    results["doc_1024_solo"] = timed([doc1024(1)])
    time.sleep(2)

    shorts = [f"{TAGS[i % len(TAGS)]} subsystem note {i}: check the status before the next rotation."
              for i in range(846)]
    results["batch_846_short"] = timed(shorts)
    time.sleep(3)

    kb_docs = [doc1024(i) for i in range(16)]  # 16 x ~5.2KB ≈ 224KB total
    total_kb = sum(len(d) for d in kb_docs) / 1024
    results["batch_224kb_size_kb"] = round(total_kb, 1)
    results["batch_224kb"] = timed(kb_docs)

    base = {"doc_1024_solo": 0.79, "batch_846_short": 41.9, "batch_224kb": 55.3}
    cmp = {}
    for k in base:
        w = results.get(k, {}).get("wall")
        if w:
            cmp[k] = f'{w}s vs {base[k]}s ({round((w/base[k]-1)*100)}%)'
    results["vs_baseline"] = cmp
    bad = [k for k in base if results.get(k, {}).get("wall") and results[k]["wall"] > base[k] * 1.25]
    results["REGRESSION"] = "NONE" if not bad else f"POSSIBLE in {bad}"

    with open("/home/mintvm/.hermes/cache/scratch/ollitert/embed_regression_results.json", "w") as f:
        json.dump(results, f, indent=2, default=str)
    print(json.dumps(results, indent=2, default=str))

if __name__ == "__main__":
    main()

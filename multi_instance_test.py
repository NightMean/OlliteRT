#!/usr/bin/env python3
"""
Multi-instance (N engines of the SAME model) behavioral test for OlliteRT on P8P.

Axes: does count=2 produce TRUE concurrency, or does it degrade to the
single-engine serialized behavior?

Battery (all ~750-token docs, 768-dim):
  warm      : 1 short embed (discard)
  solo      : 1 doc in one request          -> T1
  conc2     : 2 SEPARATE requests, same instant, 1 doc each
              2 engines: wall ~= T1 (overlap)   1 engine: wall ~= 2*T1
  conc4     : 4 separate requests, same instant
              2 engines: wall ~= 2*T1           1 engine: wall ~= 4*T1
  sanity    : cosine(paraphrase) vs cosine(unrelated)
  slots     : GET /v1/model/1 + GET / (model_instances field)

Stdlib only. Retries with growing backoff (the server drops connections).
"""
import json, time, math, urllib.request, urllib.error, sys
from concurrent.futures import ThreadPoolExecutor

BASE = "http://192.168.0.35:8000"
TAGS = ["alpha","bravo","charlie","delta","echo","foxtrot","golf","hotel",
        "india","juliet","kilo","lima","mike","november","oscar","papa"]

def post(path, body, timeout=120, tries=6):
    last = None
    for i in range(tries):
        t0 = time.time()
        try:
            req = urllib.request.Request(BASE + path, data=json.dumps(body).encode(),
                                         headers={"Content-Type": "application/json"}, method="POST")
            with urllib.request.urlopen(req, timeout=timeout) as r:
                out = json.loads(r.read().decode())
            wall = time.time() - t0
            return {"wall": round(wall, 3), "ok": True, "out": out}
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

PARA = ("The retrieval system indexes each document chunk into a vector store so that "
        "semantic search can rank the most relevant passages for a given query. Embeddings "
        "capture meaning at the token level, allowing near duplicate detection and clustering "
        "across a large corpus without exact string matching. The pipeline tokenizes the raw "
        "text, pads short segments, and forwards each batch to the on device model, which "
        "returns a normalized vector that downstream services compare with cosine similarity "
        "to decide which passages to pass to the answering model as context.")

def doc(i, paras=9):
    seed = f"Document {i} focuses on the {TAGS[i % len(TAGS)]} domain and its subsystems. "
    return seed + " ".join(PARA for _ in range(paras))

def vec(out):
    return out["data"][0]["embedding"]

def norm(v):
    return math.sqrt(sum(x * x for x in v))

def cos(a, b):
    na, nb = norm(a), norm(b)
    return sum(x * y for x, y in zip(a, b)) / (na * nb) if na and nb else 0.0

def main():
    if not wait_server():
        print(json.dumps({"ok": False, "error": "server not reachable"})); return

    info = get("/")
    models = get("/v1/models")
    mids = [m.get("id") for m in models.get("data", [])] if "data" in models else models
    emod = next((m for m in mids if "embed" in m.lower()), None)
    print("models:", mids, "| model_instances (GET /):",
          info.get("model_instances"), "| active:", info.get("model"), file=sys.stderr)

    d = [doc(i) for i in range(4)]
    def body(docs):
        b = {"input": docs}
        if emod: b["model"] = emod
        return b

    # warmup
    w = post("/v1/embeddings", body(["warmup text"]))
    print("warmup:", "ok" if w["ok"] else w.get("error"), file=sys.stderr)
    time.sleep(2)

    results = {"models": mids, "model_instances_reported": info.get("model_instances")}

    # solo baseline (2 runs, use 2nd)
    s1 = post("/v1/embeddings", body([d[0]]))
    time.sleep(1)
    solo = post("/v1/embeddings", body([d[0]]))
    results["solo_1st"] = {"wall": s1["wall"]}
    results["solo"] = {"wall": solo["wall"]}
    t1 = solo["wall"]

    # conc2: two separate requests, same instant
    with ThreadPoolExecutor(max_workers=2) as ex:
        f1 = ex.submit(post, "/v1/embeddings", body([d[1]]))
        f2 = ex.submit(post, "/v1/embeddings", body([d[2]]))
        c1, c2 = f1.result(), f2.result()
    results["conc2"] = {"req1": c1["wall"], "req2": c2["wall"],
                        "ok": c1["ok"] and c2["ok"],
                        "wall_max": max(c1["wall"] or 0, c2["wall"] or 0)}
    time.sleep(2)

    # conc4
    with ThreadPoolExecutor(max_workers=4) as ex:
        fs = [ex.submit(post, "/v1/embeddings", body([d[i % 4]])) for i in range(4)]
        rs = [f.result() for f in fs]
    walls = [r["wall"] for r in rs]
    results["conc4"] = {"walls": walls, "ok": all(r["ok"] for r in rs),
                        "wall_max": max(walls) if walls else None}

    if t1:
        results["conc2_ratio"] = round(results["conc2"]["wall_max"] / t1, 2)
        results["conc4_ratio"] = round(results["conc4"]["wall_max"] / t1, 2)
        results["VERDICT"] = (
            "TRUE 2-ENGINE OVERLAP (conc2 ~= 1x solo)" if results["conc2_ratio"] < 1.5
            else "SINGLE-ENGINE / GPU-SERIALIZED (conc2 ~= 2x solo)")

    # semantic sanity: paraphrase vs unrelated
    p1 = post("/v1/embeddings", body(["The garage door is open and the lights in the kitchen are on."]))
    p2 = post("/v1/embeddings", body(["My kitchen light is on and the garage door stands open."]))
    u  = post("/v1/embeddings", body(["Quarterly financial projections for semiconductor supply chains."]))
    if p1["ok"] and p2["ok"] and u["ok"]:
        results["cos_paraphrase"] = round(cos(vec(p1["out"]), vec(p2["out"])), 3)
        results["cos_unrelated"] = round(cos(vec(p1["out"]), vec(u["out"])), 3)
        results["norm0"] = round(norm(vec(p1["out"])), 3)

    # slot probe
    results["slot1"] = get("/v1/model/1")

    with open("/home/mintvm/.hermes/cache/scratch/ollitert/multi_instance_results.json", "w") as f:
        json.dump(results, f, indent=2, default=str)
    print(json.dumps(results, indent=2, default=str))

if __name__ == "__main__":
    main()

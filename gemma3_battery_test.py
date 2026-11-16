#!/usr/bin/env python3
"""Gemma3-1B + EmbeddingGemma dual-model test battery (P8P, 192.168.0.35:8000).

Config under test: EmbeddingGemma-2-740M x2 (primary, 1024-tok cap) +
Gemma3-1B-IT x2 (secondary, 2048 ctx). Measures:
  1. embedding: solo short / solo 1024-tok / 2-parallel short / 2-parallel long
  2. chat: streaming TTFT + E2E + tok/s (short + long gen, 2 runs each)
  3. parallel: chat+embed same instant; 2 chats same instant
Results -> gemma3_battery_results.json
"""
import json, time, http.client, statistics, sys, concurrent.futures as cf

BASE = "192.168.0.35"
PORT = 8000
E_MODEL = "EmbeddingGemma-2-740M"
C_MODEL = "Gemma3-1B-IT"

# ~800 words of natural prose ~= under the 1024 engine-tok cap
LONG_DOC = (
    "The old lighthouse kept its vigil on the northern coast through decades of "
    "storm and calm, its keeper a quiet man who measured his life in tides and "
    "the slow rotation of the great lens above him. Each evening he climbed the "
    "winding stair, polished the glass until it sang under his cloth, and lit the "
    "flame that guided ships away from the black teeth of the reef. Fishermen in "
    "the harbor town below knew the rhythm of the beam as well as they knew their "
    "own heartbeats, and when it faltered, even on a clear night, a hush would fall "
    "over the market square, because the light was a promise older than any of "
    "them. Stories accumulated around it: the storm of the winter the sea took "
    "three boats, the night a whale surfaced close enough that the keeper could "
    "count the barnacles on its flank, the summer a child pressed her face to the "
    "gallery rail and asked what the darkness was for. The keeper answered that "
    "the light was not against the darkness, only a conversation with it. When the "
    "automation crews finally came, they found his logbook full of neat entries, "
    "each one ending with the same small phrase, the sea is patient today. The "
    "machines needed no logbook, and for a while the lamp turned alone, faithful "
    "and unobserved, until one gale night a sensor failed and the beam went dark "
    "for forty seconds that the harbor master later called the longest of his "
    "life. They restored the old stair, the old glass, and the old habit of "
    "checking by hand, because some promises, it turned out, required a witness. "
    "The new keeper, a young woman from the mainland, wrote in the logbook on her "
    "first night, the sea is patient today, and added below it in smaller letters, "
    "so am i, and the lighthouse kept its vigil as it always had, translating the "
    "language of the deep into a single sweeping sentence of light, readable from "
    "a great distance by anyone who knew how to listen to the dark. "
)
LONG_DOC = (LONG_DOC * 3)[:9000]  # pad to ~1300 words, then trim safe below cap

def req(path, body, timeout=120):
    c = http.client.HTTPConnection(BASE, PORT, timeout=timeout)
    c.request("POST", path, body=json.dumps(body), headers={"Content-Type": "application/json"})
    r = c.getresponse()
    data = r.read()
    c.close()
    return r.status, data

def get(path, timeout=15):
    c = http.client.HTTPConnection(BASE, PORT, timeout=timeout)
    c.request("GET", path)
    r = c.getresponse()
    data = r.read()
    c.close()
    return r.status, data

def embed(doc, dims=768):
    t0 = time.time()
    st, data = req("/v1/model/1/embeddings",
                   {"model": E_MODEL, "input": [doc], "dimensions": dims})
    dt = time.time() - t0
    ok = st == 200
    if ok:
        e = json.loads(data)["data"][0]["embedding"]
        assert len(e) == dims
        ok = abs(sum(x*x for x in e) ** 0.5 - 1.0) < 0.01
    return dt, ok, st

def chat_stream(prompt, max_tokens, temperature=0.0):
    """Returns (ttft, e2e, tokens, ok, status)."""
    c = http.client.HTTPConnection(BASE, PORT, timeout=300)
    c.request("POST", "/v1/model/2/chat/completions", body=json.dumps({
        "model": C_MODEL,
        "messages": [{"role": "user", "content": prompt}],
        "max_tokens": max_tokens, "temperature": temperature, "stream": True,
    }), headers={"Content-Type": "application/json"})
    r = c.getresponse()
    if r.status != 200:
        err = r.read()[:200]
        c.close()
        return None, None, 0, False, r.status, err
    t0 = time.time()
    ttft = None
    content = ""
    for raw in r:
        line = raw.decode().strip()
        if not line.startswith("data:"):
            continue
        payload = line[5:].strip()
        if payload == "[DONE]":
            break
        try:
            j = json.loads(payload)
            delta = j.get("choices", [{}])[0].get("delta", {})
            piece = delta.get("content")
            if piece:
                if ttft is None:
                    ttft = time.time() - t0
                content += piece
        except Exception:
            pass
    e2e = time.time() - t0
    c.close()
    tokens = max(1, len(content.split()))
    return ttft, e2e, tokens, True, 200, content[:120]

def main():
    results = {}
    st, data = get("/")
    info = json.loads(data)
    results["server"] = {k: info.get(k) for k in ("model", "secondary_model", "model_instances", "uptime_seconds")}
    print("SERVER:", results["server"], flush=True)
    if info.get("secondary_model") != C_MODEL:
        print("ABORT: secondary model is not Gemma3-1B-IT", file=sys.stderr)
        sys.exit(1)

    # warmup (cold start of both engines)
    print("WARMUP...", flush=True)
    embed("warmup", 256)
    chat_stream("Say OK", 8)
    time.sleep(1)

    # 1. EMBEDDING
    e_short = []
    for _ in range(3):
        dt, ok, st = embed("The quick brown fox jumps over the lazy dog.")
        e_short.append(dt)
        time.sleep(0.5)
    dt_long, ok_long, st_long = embed(LONG_DOC)
    # 2-parallel short
    with cf.ThreadPoolExecutor(2) as ex:
        f1 = ex.submit(embed, "First parallel document about the ocean.", 768)
        f2 = ex.submit(embed, "Second parallel document about mountains and snow.", 768)
        p_short = [f.result() for f in (f1, f2)]
    # 2-parallel long
    with cf.ThreadPoolExecutor(2) as ex:
        f1 = ex.submit(embed, LONG_DOC)
        f2 = ex.submit(embed, LONG_DOC + " An extra sentence to make it distinct.")
        p_long = [f.result() for f in (f1, f2)]
    results["embedding"] = {
        "short_solo_ms": [round(x*1000) for x in e_short],
        "short_solo_median_ms": round(statistics.median(e_short)*1000),
        "long_1024_solo_ms": round(dt_long*1000),
        "long_ok": ok_long,
        "parallel2_short_walls_ms": [round(x[0]*1000) for x in p_short],
        "parallel2_long_walls_ms": [round(x[0]*1000) for x in p_long],
        "parallel2_long_makespan_ms": round(max(x[0] for x in p_long)*1000),
        "all_ok": all(x[1] for x in p_short + p_long) and ok_long,
    }
    print("EMBED:", json.dumps(results["embedding"]), flush=True)

    # 2. CHAT speed/latency (run twice, use 2nd for medians; report both)
    chat_runs = {"short": [], "long": []}
    for run in range(2):
        ttft, e2e, tok, ok, st, snippet = chat_stream(
            "Count from 1 to 20, one number per line.", 40)
        chat_runs["short"].append({"ttft_ms": round(ttft*1000) if ttft else None,
                                   "e2e_ms": round(e2e*1000), "tokens": tok,
                                   "tok_s": round(tok/e2e, 2), "ok": ok, "status": st,
                                   "snippet": snippet})
        time.sleep(1)
        ttft, e2e, tok, ok, st, snippet = chat_stream(
            "Write a detailed two-paragraph description of a small coastal village in autumn. Be specific and vivid.", 200)
        chat_runs["long"].append({"ttft_ms": round(ttft*1000) if ttft else None,
                                  "e2e_ms": round(e2e*1000), "tokens": tok,
                                  "tok_s": round(tok/e2e, 2), "ok": ok, "status": st,
                                  "snippet": snippet})
        time.sleep(1)
    results["chat"] = chat_runs

    # 3. PARALLEL: chat + embed same instant
    with cf.ThreadPoolExecutor(2) as ex:
        fc = ex.submit(chat_stream, "Tell me a short story about a lighthouse keeper, three sentences.", 60)
        fe = ex.submit(lambda: [embed(f"concurrent embed doc {i}", 768)[0] for i in range(4)])
        c_res, e_res = fc.result(), fe.result()
    results["parallel_chat_plus_embed"] = {
        "chat": {"e2e_ms": round(c_res[1]*1000), "tokens": c_res[2], "ok": c_res[3]},
        "embed4_walls_ms": [round(x*1000) for x in e_res],
    }
    # 3b. 2 chats same instant
    with cf.ThreadPoolExecutor(2) as ex:
        f1 = ex.submit(chat_stream, "Say the word apple exactly once.", 16)
        f2 = ex.submit(chat_stream, "Say the word banana exactly once.", 16)
        c1, c2 = f1.result(), f2.result()
    results["parallel_2_chats"] = {
        "wall1_ms": round(c1[1]*1000), "wall2_ms": round(c2[1]*1000),
        "ok": c1[3] and c2[3], "statuses": [c1[4], c2[4]],
    }

    with open("/home/mintvm/.hermes/cache/scratch/gemma3_battery_results.json", "w") as f:
        json.dump(results, f, indent=1)
    print(json.dumps(results["chat"], indent=1), flush=True)
    print("PARALLEL:", json.dumps(results["parallel_chat_plus_embed"]), flush=True)
    print("2CHATS:", json.dumps(results["parallel_2_chats"]), flush=True)
    print("DONE", flush=True)

if __name__ == "__main__":
    main()

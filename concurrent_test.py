#!/usr/bin/env python3
"""Dual-model concurrent pass-criteria test.

Fires a chat completion (E4B) and an embedding batch (EmbeddingGemma) at the
same instant; compares against a solo chat baseline.
PASS = both 200 with valid payloads, no 503/native race, concurrent chat
throughput within ~20% of solo.
"""
import json
import threading
import time
import urllib.request

BASE = "http://192.168.0.35:8000"
CHAT_MODEL = "Gemma-4-E4B-it"
EMBED_MODEL = "EmbeddingGemma-2-740M"
CHAT_PROMPT = (
    "Write a short descriptive paragraph (about 150 words) about a lighthouse "
    "keeper on a stormy night. Then list 3 things he would check before the "
    "storm hits."
)
EMBED_TEXTS = [
    "turn on the kitchen light",
    "open the garage door",
    "what is the weather like outside",
    "lock the front door",
]


def post(path, body, timeout=300):
    req = urllib.request.Request(
        BASE + path,
        data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.status, json.loads(r.read())


def with_retries(fn, attempts=4):
    last = None
    for i in range(attempts):
        try:
            return fn()
        except Exception as e:  # flaky TCP on this server
            last = e
            time.sleep(3 * (i + 1))
    raise last


def chat_run(max_tokens=150):
    t0 = time.time()
    status, data = with_retries(lambda: post(
        "/v1/chat/completions",
        {"model": CHAT_MODEL, "messages": [{"role": "user", "content": CHAT_PROMPT}],
         "max_tokens": max_tokens, "temperature": 0.8},
    ))
    wall = time.time() - t0
    content = data["choices"][0]["message"]["content"]
    tok = data.get("usage", {}).get("completion_tokens") or len(content.split())
    return {
        "status": status,
        "wall_s": round(wall, 1),
        "tokens": tok,
        "tok_s": round(tok / wall, 2) if wall > 0 else 0,
        "content_len": len(content),
        "valid": status == 200 and len(content) > 100,
    }


def embed_run():
    t0 = time.time()
    status, data = with_retries(lambda: post(
        "/v1/embeddings",
        {"model": EMBED_MODEL, "input": EMBED_TEXTS, "dimensions": 768},
    ))
    wall = time.time() - t0
    vecs = data.get("data", [])
    norms = [round(sum(v * v for v in d["embedding"]) ** 0.5, 3) for d in vecs]
    return {
        "status": status,
        "wall_s": round(wall, 2),
        "n_vectors": len(vecs),
        "dims": vecs[0]["embedding"].__len__() if vecs else 0,
        "norms": norms,
        "valid": status == 200 and len(vecs) == 4 and vecs[0]["embedding"].__len__() == 768,
    }


results = {}

# Warm-up (first request after idle is slow)
print("warmup...", flush=True)
chat_run(max_tokens=20)

print("RUN A: solo chat (150 tok)", flush=True)
results["solo_chat"] = chat_run()
time.sleep(3)

print("RUN B: concurrent chat (150 tok) + embed batch-4, fired same instant", flush=True)
out = {}
def do_chat(): out["chat"] = chat_run()
def do_embed(): out["embed"] = embed_run()
t0 = time.time()
c1, c2 = threading.Thread(target=do_chat), threading.Thread(target=do_embed)
c1.start(); c2.start()
c1.join(); c2.join()
results["concurrent_wall_s"] = round(time.time() - t0, 1)
results["concurrent_chat"] = out["chat"]
results["concurrent_embed"] = out["embed"]

solo_tps = results["solo_chat"]["tok_s"]
conc_tps = results["concurrent_chat"]["tok_s"]
delta = (solo_tps - conc_tps) / solo_tps * 100 if solo_tps else 0

results["throughput_delta_pct"] = round(delta, 1)
results["PASS"] = (
    results["solo_chat"]["valid"]
    and results["concurrent_chat"]["valid"]
    and results["concurrent_embed"]["valid"]
    and delta <= 20.0
)

print(json.dumps(results, indent=2))
with open("/home/mintvm/.hermes/cache/scratch/ollitert/concurrent_results.json", "w") as f:
    json.dump(results, f, indent=2)

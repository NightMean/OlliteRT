#!/usr/bin/env python3
"""
Single-model CHAT regression (E4B alone) — new multi-instance build (876afa49).

Baseline to beat (2026-11-11, single model, same prompt size):
  4K benchmark (2455 words, max_tokens=160): TTFT 3.3s, E2E 19.7s, ~8.4 tok/s

Measures via streaming: TTFT (first chunk), E2E, completion tokens, tok/s.
Runs the 4K prompt twice (warm / steady). Stdlib only.
"""
import json, time, sys, urllib.request, urllib.error

BASE = "http://192.168.0.35:8000"
MODEL = "Gemma-4-E4B-it"

SENT = ("The retrieval system indexes each document chunk into a vector store so that "
        "semantic search can rank the most relevant passages for a given query. Embeddings "
        "capture meaning at the token level, allowing near duplicate detection and clustering "
        "across a large corpus without exact string matching. The pipeline tokenizes the raw "
        "text, pads short segments, and forwards each batch to the on device model which "
        "returns a normalized vector that downstream services compare with cosine similarity "
        "to decide which passages to pass to the answering model as context.")
WORDS = SENT.split()  # 73 words

def build_prompt(n_words):
    out = []
    i = 0
    while len(out) < n_words:
        w = WORDS[i % len(WORDS)]
        out.append(w)
        i += 1
    # make it a question so the model has something to say
    return " ".join(out[:n_words]) + " In one short sentence, what is the main idea of this text?"

PROMPT_4K = build_prompt(2455)

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

def get(path, timeout=15):
    with urllib.request.urlopen(BASE + path, timeout=timeout) as r:
        return json.loads(r.read().decode())

def stream_chat(prompt, max_tokens, timeout=300):
    body = {"model": MODEL, "max_tokens": max_tokens, "stream": True,
            "messages": [{"role": "user", "content": prompt}]}
    req = urllib.request.Request(BASE + "/v1/chat/completions",
                                 data=json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json"}, method="POST")
    t0 = time.time()
    ttft = None
    n_tokens = 0
    last = None
    with urllib.request.urlopen(req, timeout=timeout) as r:
        for raw in r:
            line = raw.decode().strip()
            if not line.startswith("data:"):
                continue
            payload = line[5:].strip()
            if payload == "[DONE]":
                break
            try:
                j = json.loads(payload)
            except Exception:
                continue
            ch = j.get("choices") or []
            if ch and ch[0].get("delta", {}).get("content"):
                if ttft is None:
                    ttft = time.time() - t0
                n_tokens += 1
            if j.get("usage", {}).get("completion_tokens"):
                n_tokens = j["usage"]["completion_tokens"]
            last = j
    e2e = time.time() - t0
    return {"ttft": round(ttft, 3) if ttft else None,
            "e2e": round(e2e, 3),
            "tokens": n_tokens,
            "tok_s": round(n_tokens / (e2e - ttft), 2) if (ttft and e2e > ttft and n_tokens) else None}

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

    # warmup: small request, discard
    warm = stream_chat("Say the word lighthouse exactly once.", 16, timeout=120)
    results["warmup"] = warm
    time.sleep(2)

    results["run1"] = stream_chat(PROMPT_4K, 160)
    time.sleep(3)
    results["run2"] = stream_chat(PROMPT_4K, 160)

    base = {"ttft": 3.3, "e2e": 19.7, "tok_s": 8.4}
    r = results["run2"]
    if r["e2e"]:
        results["vs_baseline"] = {
            "ttft": f'{r["ttft"]}s vs 3.3s ({round((r["ttft"]/base["ttft"]-1)*100)}%)',
            "e2e": f'{r["e2e"]}s vs 19.7s ({round((r["e2e"]/base["e2e"]-1)*100)}%)',
            "tok_s": f'{r["tok_s"]} vs 8.4 ({round((r["tok_s"]/base["tok_s"]-1)*100)}%)',
        }
        ok = (r["ttft"] or 99) <= base["ttft"] * 1.25 and r["e2e"] <= base["e2e"] * 1.25
        results["REGRESSION"] = "NONE" if ok else "POSSIBLE — inspect"

    with open("/home/mintvm/.hermes/cache/scratch/ollitert/chat_regression_results.json", "w") as f:
        json.dump(results, f, indent=2, default=str)
    print(json.dumps(results, indent=2, default=str))

if __name__ == "__main__":
    main()

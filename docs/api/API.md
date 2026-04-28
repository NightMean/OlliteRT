# API Reference

OlliteRT exposes an OpenAI-compatible HTTP API on your local network. Default port is `8000` (configurable in Settings).

## Table of Contents

- [Endpoints](#endpoints)
- [Authentication](#authentication)
- [Chat Completions](#chat-completions--post-v1chatcompletions)
- [Text Completions](#text-completions--post-v1completions)
- [Responses API](#responses-api--post-v1responses)
- [Audio Transcriptions](#audio-transcriptions--post-v1audiotranscriptions)
- [Models](#models--get-v1models)
- [Model Detail](#model-detail--get-v1modelsid)
- [Health](#health--get-health)
- [Error Responses](#error-responses)
- [Server Info](#server-info--get--or-get-v1)
- [Prometheus Metrics](#prometheus-metrics--get-metrics)

---

## Endpoints

| Method | Endpoint | Description |
|:-------|:---------|:------------|
| `POST` | `/v1/chat/completions` | OpenAI Chat Completions API (streaming + non-streaming) |
| `POST` | `/v1/completions` | OpenAI Text Completions API |
| `POST` | `/v1/responses` | OpenAI Responses API |
| `POST` | `/v1/audio/transcriptions` | Audio transcription |
| `GET`  | `/v1/models` | List available models |
| `GET`  | `/v1/models/{id}` | Get detail for a specific model |
| `GET`  | `/` or `/v1` | Server info (version, status, endpoints) |
| `GET`  | `/health` | Health check (add `?metrics=true` for detailed JSON stats) |
| `GET`  | `/metrics` | Prometheus metrics (exposition format) |
| `GET`  | `/ping` | Simple liveness check — returns `{"status":"ok"}` |

## Authentication

Bearer token authentication is **optional** and disabled by default. When disabled, all endpoints are open — no API key or header is needed.

To enable authentication, go to Settings → Server Configuration and toggle **Require Bearer Token**. When enabled, include the token in the `Authorization` header:

```
Authorization: Bearer your-token
```

Some clients (Home Assistant integrations, Open WebUI) require an API key field even when auth is disabled — enter any non-empty value (e.g. `unused`). OlliteRT ignores the header entirely when bearer auth is not enabled.

See the [Security Guide](../SECURITY.md) for details on network exposure and credential storage.

> [!TIP]
> All inference endpoints accept the same core parameters (`temperature`, `top_p`, `top_k`, `max_tokens`, `stream`). The parameter tables below document each endpoint's full set.

## Chat Completions — `POST /v1/chat/completions`

### Request Body

| Parameter | Type | Required | Description |
|:----------|:-----|:--------:|:------------|
| `model` | string | Yes | Model name (e.g. `Gemma-4-E2B-it`) |
| `messages` | array | Yes | Array of message objects (`role` + `content`) |
| `stream` | boolean | No | Enable SSE streaming (default: `false`) |
| `stream_options` | object | No | Streaming options. Set `{"include_usage": true}` to receive a usage chunk before `[DONE]` |
| `temperature` | number | No | Sampling temperature (0.0 - 2.0) |
| `top_p` | number | No | Nucleus sampling threshold |
| `top_k` | integer | No | Top-k sampling |
| `max_tokens` | integer | No | Maximum tokens to generate |
| `max_completion_tokens` | integer | No | Alias for `max_tokens` |
| `stop` | string or array | No | Stop sequence(s) |
| `tools` | array | No | Tool/function definitions for [tool calling](../TROUBLESHOOTING.md#tool-calling-experimental) |
| `tool_choice` | string or object | No | Tool selection strategy (`auto`, `none`, or specific tool) |
| `response_format` | object | No | Response format (`{"type": "json_object"}` for JSON mode) |

### Message Object

| Field | Type | Description |
|:------|:-----|:------------|
| `role` | string | `system`, `user`, `assistant`, or `tool` |
| `content` | string or array | Text content, or array of content parts for multimodal |
| `tool_call_id` | string | Required for `role: "tool"` — references the tool call being responded to |
| `name` | string | Function name (for tool messages) |

### Multimodal Content

For vision and audio input, use content parts:

**Image:**
```json
{
  "role": "user",
  "content": [
    {"type": "text", "text": "What's in this image?"},
    {"type": "image_url", "image_url": {"url": "data:image/png;base64,..."}}
  ]
}
```

**Audio:**
```json
{
  "role": "user",
  "content": [
    {"type": "input_audio", "input_audio": {"data": "<base64-encoded-audio>", "format": "wav"}}
  ]
}
```

Supported audio formats: `wav`, `mp3`, `ogg`, `flac`. Audio must be mono — stereo is automatically downmixed.

> [!TIP]
> For dedicated audio transcription, use the [`/v1/audio/transcriptions`](#audio-transcriptions--post-v1audiotranscriptions) endpoint instead.

### Response

```json
{
  "id": "chatcmpl-...",
  "object": "chat.completion",
  "created": 1234567890,
  "model": "Gemma-4-E2B-it",
  "system_fingerprint": null,
  "choices": [{
    "index": 0,
    "message": {
      "role": "assistant",
      "content": "Hello! How can I help you?"
    },
    "finish_reason": "stop"
  }],
  "usage": {
    "prompt_tokens": 10,
    "completion_tokens": 8,
    "total_tokens": 18
  }
}
```

`finish_reason` values: `"stop"` (natural end or stop sequence), `"length"` (output truncated by `max_tokens`), `"tool_calls"` (model invoked a tool).

> **Note:** The `system_fingerprint` field is always `null`. The LiteRT runtime does not expose a tokenizer or model configuration hash, so there is no meaningful fingerprint to generate. Clients that check this field should treat `null` as "unknown configuration."

### Streaming Response

When `stream: true`, the response is sent as Server-Sent Events:

```
data: {"id":"chatcmpl-...","choices":[{"index":0,"delta":{"role":"assistant","content":""},"finish_reason":null}]}

data: {"id":"chatcmpl-...","choices":[{"index":0,"delta":{"content":"Hello"},"finish_reason":null}]}

data: {"id":"chatcmpl-...","choices":[{"index":0,"delta":{"content":"!"},"finish_reason":null}]}

data: {"id":"chatcmpl-...","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

data: [DONE]
```

When `stream_options: {"include_usage": true}` is set, a usage chunk is emitted before `[DONE]`:

```
data: {"id":"chatcmpl-...","choices":[],"usage":{"prompt_tokens":10,"completion_tokens":8,"total_tokens":18}}

data: [DONE]
```

Without `stream_options` (the default), no usage chunk is emitted — the stream ends with the `finish_reason` chunk followed by `[DONE]`.

## Text Completions — `POST /v1/completions`

| Parameter | Type | Required | Description |
|:----------|:-----|:--------:|:------------|
| `model` | string | Yes | Model name |
| `prompt` | string | Yes | Text prompt |
| `stream` | boolean | No | Enable SSE streaming |
| `temperature` | number | No | Sampling temperature |
| `max_tokens` | integer | No | Maximum tokens to generate |

## Responses API — `POST /v1/responses`

Alternative API format. Accepts either `messages` (array) or `input` (string) field.

| Parameter | Type | Required | Description |
|:----------|:-----|:--------:|:------------|
| `model` | string | Yes | Model name |
| `input` | string or array | Yes | Input text or messages array |
| `stream` | boolean | No | Enable SSE streaming |
| `tools` | array | No | Tool definitions |
| `tool_choice` | string or object | No | Tool selection strategy (`auto`, `none`, or specific tool) |
| `temperature` | number | No | Sampling temperature |
| `top_p` | number | No | Nucleus sampling threshold |
| `top_k` | integer | No | Top-k sampling |
| `max_output_tokens` | integer | No | Maximum tokens to generate |

### Streaming Response

When `stream: true`, the Responses API uses typed Server-Sent Events with an `event:` prefix (unlike Chat Completions which uses `data:`-only lines). Each SSE frame has the format:

```
event: <event-type>
data: <JSON payload>
```

The full event sequence for a text response:

```
event: response.created
data: {"type":"response.created","response":{"id":"resp-...","object":"response","created_at":1234567890,"status":"in_progress","model":"Gemma-4-E2B-it","output":[]}}

event: response.in_progress
data: {"type":"response.in_progress","response":{"id":"resp-...","object":"response","created_at":1234567890,"status":"in_progress","model":"Gemma-4-E2B-it","output":[]}}

event: response.output_item.added
data: {"type":"response.output_item.added","item":{"id":"msg-...","type":"message","status":"in_progress","content":[],"role":"assistant"},"output_index":0,"sequence_number":0}

event: response.content_part.added
data: {"type":"response.content_part.added","content_index":0,"item_id":"msg-...","output_index":0,"part":{"type":"output_text","annotations":[],"logprobs":[],"text":""}}

event: response.output_text.delta
data: {"type":"response.output_text.delta","content_index":0,"delta":"Hello","item_id":"msg-...","output_index":0}

event: response.output_text.delta
data: {"type":"response.output_text.delta","content_index":0,"delta":"!","item_id":"msg-...","output_index":0}

event: response.output_text.done
data: {"type":"response.output_text.done","content_index":0,"item_id":"msg-...","output_index":0,"text":"Hello!"}

event: response.content_part.done
data: {"type":"response.content_part.done","content_index":0,"item_id":"msg-...","output_index":0,"part":{"type":"output_text","annotations":[],"logprobs":[],"text":"Hello!"}}

event: response.output_item.done
data: {"type":"response.output_item.done","item":{"id":"msg-...","type":"message","status":"completed","content":[{"type":"output_text","annotations":[],"logprobs":[],"text":"Hello!"}],"role":"assistant"},"output_index":0}

event: response.completed
data: {"type":"response.completed","response":{"id":"resp-...","object":"response","created_at":1234567890,"status":"completed","model":"Gemma-4-E2B-it","output":[...],"usage":{"input_tokens":10,"output_tokens":2,"total_tokens":12}}}

data: [DONE]
```

The final `data: [DONE]` line has no `event:` prefix — it signals the end of the stream (same as Chat Completions).

## Audio Transcriptions — `POST /v1/audio/transcriptions`

Accepts an audio file via multipart/form-data and returns a text transcription.

Requires a model with audio capability (e.g. Gemma 4, Gemma 3n).

### Request Body (multipart/form-data)

| Field | Type | Required | Description |
|:------|:-----|:--------:|:------------|
| `file` | file | Yes | Audio file to transcribe (max 25 MB) |
| `model` | string | No | Model name (ignored — uses the currently loaded model) |
| `language` | string | No | Language hint (e.g. `en`, `de`, `ja`) |
| `prompt` | string | No | Context hint to guide transcription |
| `temperature` | number | No | Sampling temperature override |
| `response_format` | string | No | `json` (default), `text`, or `verbose_json` |

Supported audio formats: **WAV**, **MP3**, **OGG** (Vorbis), **FLAC**. Stereo WAV (16-bit PCM) is automatically downmixed to mono; other formats should be mono before sending.

### Response Formats

**`json`** (default) — `Content-Type: application/json`

```json
{"text": "The transcribed text from the audio file."}
```

**`text`** — `Content-Type: text/plain`

```
The transcribed text from the audio file.
```

**`verbose_json`** — `Content-Type: application/json`

```json
{
  "task": "transcribe",
  "language": "en",
  "duration": 3.456,
  "text": "The transcribed text from the audio file.",
  "segments": [{
    "id": 0,
    "seek": 0,
    "start": 0.0,
    "end": 3.456,
    "text": "The transcribed text from the audio file."
  }]
}
```

> `duration` reflects LLM inference time, not audio length. The model returns raw text without word-level timing, so the output contains a single segment spanning the full duration.

> **Note:** `srt` and `vtt` formats are not supported — the LiteRT runtime does not provide word-level timing data required for subtitle generation. Requesting these formats returns HTTP 400.

### Example (curl)

```bash
curl http://PHONE_IP:8000/v1/audio/transcriptions \
  -H "Authorization: Bearer your-token" \
  -F file=@recording.wav \
  -F response_format=json
```

## Models — `GET /v1/models`

Returns a list of available models with their capabilities and update status.

```json
{
  "object": "list",
  "data": [{
    "id": "Gemma-4-E2B-it",
    "object": "model",
    "created": 1234567890,
    "owned_by": "ollitert",
    "capabilities": {
      "image": true,
      "audio": true,
      "thinking": true
    },
    "update_available": false
  }]
}
```

| Field | Type | Description |
|:------|:-----|:------------|
| `id` | string | Model name |
| `object` | string | Always `"model"` |
| `created` | integer | Unix timestamp |
| `owned_by` | string | Always `"ollitert"` |
| `capabilities` | object | `image`, `audio`, `thinking` booleans. `thinking` indicates the model supports chain-of-thought AND it is currently enabled in settings (not just model capability). |
| `update_available` | boolean | `true` if a newer version of this model is available in the allowlist |

## Model Detail — `GET /v1/models/{id}`

Returns detail for a specific model by name. The model ID is case-insensitive. Returns `404` if the model is not loaded (or not idle-unloaded by keep-alive).

The response has the same shape as a single entry from the `/v1/models` list.

## Health — `GET /health`

Returns server health status. Also available at `/v1/health`.

### Base Response

```json
{
  "status": "ok",
  "model": "Gemma-4-E2B-it",
  "uptime_seconds": 3600,
  "update_available": false
}
```

| Field | Type | Description |
|:------|:-----|:------------|
| `status` | string | `ok`, `idle` (keep-alive unloaded), `loading`, `stopped`, `error` |
| `model` | string | Currently loaded (or idle-unloaded) model name. Omitted if no model. |
| `uptime_seconds` | integer | Seconds since server entered RUNNING state. Omitted if not running. |
| `update_available` | boolean | `true` if a newer OlliteRT version exists |

### Extended Response — `GET /health?metrics=true`

Appends server info and a `metrics` object to the base response:

| Field | Type | Description |
|:------|:-----|:------------|
| `version` | string | OlliteRT version string |
| `thinking_enabled` | boolean | Whether chain-of-thought mode is active |
| `accelerator` | string | `gpu`, `cpu`, or `gpu,cpu` |
| `is_idle_unloaded` | boolean | `true` if model was unloaded by keep-alive timeout |
| `metrics.requests_total` | integer | Total requests processed |
| `metrics.errors_total` | integer | Total request errors |
| `metrics.prompt_tokens_total` | integer | Total prompt tokens (estimated) |
| `metrics.generation_tokens_total` | integer | Total generated tokens (estimated) |
| `metrics.requests_text` | integer | Total text-only requests |
| `metrics.requests_image` | integer | Total image multimodal requests |
| `metrics.requests_audio` | integer | Total audio multimodal requests |
| `metrics.ttfb_last_ms` | number | Last request time to first token (ms) |
| `metrics.ttfb_avg_ms` | number | Average time to first token (ms) |
| `metrics.decode_tokens_per_second` | number | Last request decode throughput (tokens/s) |
| `metrics.decode_tokens_per_second_peak` | number | Peak decode throughput since start |
| `metrics.prefill_tokens_per_second` | number | Last request prefill throughput (tokens/s) |
| `metrics.inter_token_latency_ms` | number | Last inter-token latency (ms) |
| `metrics.request_latency_last_ms` | number | Last request total latency (ms) |
| `metrics.request_latency_avg_ms` | number | Average request latency (ms) |
| `metrics.request_latency_peak_ms` | number | Peak request latency (ms) |
| `metrics.context_utilization_percent` | number | Last request context window usage (%) |
| `metrics.model_load_time_seconds` | number | Model load/warmup time (seconds) |
| `metrics.is_inferring` | boolean | `true` if a request is currently being processed |

## Server Info — `GET /` or `GET /v1`

Returns server identity, version, status, update availability, and the full list of supported endpoints. Does not require authentication.

```json
{
  "name": "OlliteRT",
  "version": "1.2.0",
  "build": 42,
  "git_hash": "abc1234",
  "status": "ok",
  "model": "Gemma-4-E2B-it",
  "uptime_seconds": 3600,
  "update_available": false,
  "allowlist_content_version": "2026.04.20",
  "allowlist_source": "Official",
  "model_update_available": false,
  "compatibility": "openai",
  "endpoints": ["/v1/models", "/v1/completions", "/v1/chat/completions", "..."]
}
```

| Field | Type | Description |
|:------|:-----|:------------|
| `name` | string | Always `"OlliteRT"` |
| `version` | string | App version (e.g. `"1.2.0"`) |
| `build` | integer | Version code |
| `git_hash` | string | Build git commit hash |
| `status` | string | `ok`, `idle`, `loading`, `stopped` |
| `model` | string | Currently loaded model name (omitted if none) |
| `uptime_seconds` | integer | Seconds since RUNNING state (omitted if not running) |
| `update_available` | boolean | `true` if a newer OlliteRT version exists |
| `latest_version` | string | Newest available version (only present when `update_available` is `true`) |
| `release_url` | string | GitHub release URL (only present when `update_available` is `true`) |
| `allowlist_content_version` | string | Version of the model allowlist currently cached |
| `allowlist_source` | string | Name of the active model source repository |
| `model_update_available` | boolean | `true` if the currently loaded model has a newer version in the allowlist |
| `compatibility` | string | Always `"openai"` |
| `endpoints` | array | List of supported endpoint paths |

## Error Responses

> [!NOTE]
> All errors follow the standard OpenAI error format, so existing client libraries handle them correctly.

```json
{
  "error": {
    "message": "Model is not loaded",
    "type": "server_error",
    "param": null,
    "code": null
  }
}
```

| Status | When |
|:-------|:-----|
| `400` | Malformed request, missing required fields |
| `401` | Missing or invalid bearer token |
| `404` | Not Found — model or endpoint doesn't exist |
| `405` | Method Not Allowed — wrong HTTP method for endpoint |
| `413` | Payload Too Large — request body exceeds size limit |
| `500` | Internal server error |
| `503` | Model not loaded or server not ready |

See [Troubleshooting → Connection Issues](../TROUBLESHOOTING.md#connection-issues) for detailed explanations of each error code.

## Prometheus Metrics — `GET /metrics`

Returns server metrics in [Prometheus exposition format](https://prometheus.io/docs/instrumenting/exposition_formats/) (`text/plain; version=0.0.4`). Includes 10 counters and 19 gauges covering throughput, latency, token counts, memory, and more.

For the full list of metrics and Grafana setup, see the [Prometheus Integration Guide](../integrations/PROMETHEUS.md).
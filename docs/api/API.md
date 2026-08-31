# API Reference

OlliteRT exposes an OpenAI-compatible HTTP API. The listener address, client IP access policy, and default port `8000` are configurable in Settings.

## Table of Contents

- [Endpoints](#endpoints)
- [Authentication](#authentication)
- [Chat Completions](#chat-completions--post-v1chatcompletions)
- [Text Completions](#text-completions--post-v1completions)
- [Responses API](#responses-api--post-v1responses)
- [Anthropic Messages](#anthropic-messages--post-v1messages)
- [Anthropic Token Counter](#anthropic-token-counter--post-v1messagescount_tokens)
- [Audio Transcriptions](#audio-transcriptions--post-v1audiotranscriptions)
- [Models](#models--get-v1models)
- [Model Detail](#model-detail--get-v1modelsid)
- [Health](#health--get-health)
- [Server Configuration](#server-configuration--get--post-v1serverconfig)
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
| `POST` | `/v1/messages` | Anthropic Messages API (streaming + non-streaming) |
| `POST` | `/v1/messages/count_tokens` | Anthropic input-token estimator |
| `POST` | `/v1/audio/transcriptions` | Audio transcription |
| `GET`  | `/v1/models` | List available models |
| `GET`  | `/v1/models/{id}` | Get detail for a specific model |
| `GET`  | `/` or `/v1` | Server info (version, status, endpoints) |
| `GET`  | `/health` | Health check (add `?metrics=true` for detailed JSON stats) |
| `GET`  | `/metrics` | Prometheus metrics (exposition format) |
| `GET`  | `/ping` | Simple liveness check — returns `{"status":"ok"}` |
| `GET`  | `/v1/server/config` | Read model and server configuration |
| `POST` | `/v1/server/config` | Update model and server configuration |

## Authentication

Bearer token authentication is **optional** and disabled by default. When disabled, all endpoints are open — no API key or header is needed.

To enable authentication, go to Settings → Server Configuration and toggle **Require Bearer Token**. When enabled, include the token in the `Authorization` header:

```
Authorization: Bearer your-token
```

Anthropic SDK clients (Claude Code, the official Python/TypeScript SDKs) send credentials in `x-api-key` instead. OlliteRT accepts either header — `x-api-key` carries the raw token with no `Bearer` prefix:

```
x-api-key: your-token
```

In every example below the literal string `your-token` is purely a placeholder — when auth is disabled (the default) OlliteRT ignores the header value entirely, so any non-empty string works. When auth is enabled, the value must match the token configured in **Settings → Server Configuration**. The phone never relays credentials to the real OpenAI or Anthropic APIs.

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

## Anthropic Messages — `POST /v1/messages`

Anthropic-compatible Messages API. Lets Claude Code and the official Anthropic SDKs (Python, TypeScript) target the phone directly with no proxy. The handler translates the Anthropic request into the internal chat-completion pipeline and re-shapes the response into Anthropic's `content`-block format.

> [!WARNING]
> **Experimental.** Wire-level support for the Messages API is implemented and stable, but on-device models in the Gemma-4-E2B / 3n class do not have the context budget or instruction-following headroom to drive Claude Code (large system prompt, dense tool surface) reliably. Expect long prefill, frequent tool-call mistakes, and the LiteRT-LM #2418 parse failures noted below. Use the OpenAI-compatible endpoints for production workflows; treat this surface as a smoke test for the Anthropic API.

### Request Body

| Parameter | Type | Required | Description |
|:----------|:-----|:--------:|:------------|
| `model` | string | Yes | Model name (e.g. `Gemma-4-E2B-it`) |
| `messages` | array | Yes | Array of message objects (`role` + `content`) |
| `max_tokens` | integer | Yes | Maximum tokens to generate |
| `system` | string or array | No | System prompt — string for the simple form, or an array of `{type:"text", text:"..."}` blocks |
| `stream` | boolean | No | Enable SSE streaming (default: `false`) |
| `temperature` | number | No | Sampling temperature |
| `top_p` | number | No | Nucleus sampling threshold |
| `top_k` | integer | No | Top-k sampling |
| `stop_sequences` | array | No | Stop strings |
| `tools` | array | No | Tool definitions in Anthropic shape (`{name, description, input_schema}`) |
| `tool_choice` | object | No | `{type:"auto"}`, `{type:"any"}`, `{type:"none"}`, or `{type:"tool", name:"..."}` |
| `thinking` | object | No | `{type:"enabled"}` / `{type:"disabled"}` — per-request override of the model's persisted thinking setting (only applied when the model supports thinking) |

The following Anthropic features are accepted on the wire but silently dropped because LiteRT-LM has no equivalent: `metadata`, `service_tier`, `cache_control`, `parallel_tool_calls`, echoed `thinking` blocks. URL-sourced images, document blocks, and `computer_*` / `text_editor_*` / `bash_*` tool types return HTTP 400.

### Response (non-streaming)

```json
{
  "id": "msg_...",
  "type": "message",
  "role": "assistant",
  "model": "Gemma-4-E2B-it",
  "content": [
    {"type": "text", "text": "Hello!"}
  ],
  "stop_reason": "end_turn",
  "stop_sequence": null,
  "usage": {"input_tokens": 12, "output_tokens": 4}
}
```

`stop_reason` is one of `end_turn`, `max_tokens`, `stop_sequence`, or `tool_use`. When `stop_sequence` fires, `stop_sequence` echoes the matched string. Tool calls produce `{type:"tool_use", id, name, input}` content blocks.

### Streaming

When `stream: true`, the response is a Server-Sent Events stream that follows Anthropic's documented event sequence:

```
event: message_start
data: {"type":"message_start","message":{...}}

event: content_block_start
data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

event: content_block_delta
data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}

event: content_block_stop
data: {"type":"content_block_stop","index":0}

event: message_delta
data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{...}}

event: message_stop
data: {"type":"message_stop"}
```

OlliteRT also emits `event: ping` events every 10 s while the model is still in prefill so SDK clients don't time out on long on-device prefill (Gemma-4-E2B routinely takes 30–60 s to first token). Errors mid-stream surface as `event: error` with `{"type":"error","error":{"type","message"}}`.

### Known Issues

> [!WARNING]
> **Gemma 4 native tool calling is unreliable.** When a tool argument is a string containing quoted content (Bash command, Edit `old_string`, WebFetch URL, JSON-in-a-string), Gemma-4 emits its trained `<|"|>` quote delimiter for the inner quotes. LiteRT-LM 0.11.0 / 0.12.0's ANTLR function-call parser does not understand this token and raises `INVALID_ARGUMENT`, which surfaces as a 500 to the client. Affects every Anthropic tool-using client (notably Claude Code, which always sends Bash / Edit / Read tool definitions). Tracking upstream: <https://github.com/google-ai-edge/LiteRT-LM/issues/2418>. Workaround: turn off **Settings → Schema Injection** so tool calls go through the text-mode parser instead.

### Example (curl, non-streaming)

```bash
curl http://PHONE_IP:8000/v1/messages \
  -H "x-api-key: your-token" \
  -H "anthropic-version: 2023-06-01" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "Gemma-4-E2B-it",
    "max_tokens": 256,
    "messages": [{"role": "user", "content": "Say hello"}]
  }'
```

### Example (Claude Code)

```bash
ANTHROPIC_BASE_URL=http://PHONE_IP:8000 \
ANTHROPIC_AUTH_TOKEN=your-token \
claude
```

Claude Code maps `ANTHROPIC_AUTH_TOKEN` to the `x-api-key` header. The `/v1` segment is appended automatically.

## Anthropic Token Counter — `POST /v1/messages/count_tokens`

Estimates the input-token count for a Messages-shaped request without running inference. Works even when no model is loaded.

The body accepts the same fields as `/v1/messages`; `max_tokens` is optional here. The response is:

```json
{"input_tokens": 1042}
```

Counts are estimated as `chars / 4` (the same heuristic OlliteRT uses across the request log). This is not a tokenizer-exact count — there is no public LiteRT tokenizer API — but it tracks within ±20% of the runtime count for English chat traffic.

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
      "thinking": true,
      "speculative_decoding": true
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
| `capabilities` | object | `image`, `audio`, `thinking`, `speculative_decoding` booleans. `thinking` indicates the model supports chain-of-thought AND it is currently enabled in settings (not just model capability). `speculative_decoding` indicates MTP is supported AND enabled. |
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
| `speculative_decoding_enabled` | boolean | Whether speculative decoding (MTP) is active |
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

## Server Configuration — `GET` / `POST /v1/server/config`

This management endpoint is available when **REST API Integration** is enabled
in Settings → Home Assistant; otherwise it returns `404`. It requires bearer
authentication when bearer authentication is enabled, and requires a loaded or
idle-unloaded model context.

Use `GET` to read the active model's effective configuration. `POST` with an
empty body is retained as a compatibility read. Send any subset of the
following fields in a non-empty `POST` body to update them:

| Field | Type | Access | Description |
|:------|:-----|:------:|:------------|
| `temperature` | number | Read/write | Sampling temperature |
| `max_tokens` | integer | Read/write | Maximum generated tokens |
| `top_k` | integer | Read/write | Top-k sampling |
| `top_p` | number | Read/write | Nucleus sampling threshold |
| `default_seed` | integer or `null` | Read/write | Server-wide non-negative seed override; `null` restores random seeding |
| `accelerator` | string | Read-only | Active model accelerator (`cpu`, `gpu`, or `npu`) |
| `thinking_enabled` | boolean | Read/write | Chain-of-thought mode when the model supports it |
| `thinking_budget` | integer | Read/write | Token budget for thinking; `0` clears the override |
| `speculative_decoding_enabled` | boolean | Read-only | Whether speculative decoding is enabled |
| `auto_truncate_history` | boolean | Read/write | Drop older messages to fit context |
| `auto_trim_prompts` | boolean | Read/write | Hard-trim prompts when context overflows |
| `warmup_enabled` | boolean | Read/write | Run warmup inference after model load |
| `keep_alive_enabled` | boolean | Read/write | Keep the model loaded while idle |
| `keep_alive_minutes` | integer | Read/write | Idle unload timeout, from 1 to 7200 minutes |
| `custom_prompts_enabled` | boolean | Read/write | Enable custom system prompts |
| `system_prompt` | string | Read/write | Per-model system instruction text |

`default_seed` is always present in a configuration response: it is a number
when configured and `null` when random seeding is active. Configuration updates
are validated as a unit; an invalid request changes no configuration fields.

```json
{
  "model": "Gemma-4-E2B-it",
  "model_loaded": true,
  "accelerator": "cpu",
  "temperature": 1.0,
  "default_seed": null,
  "keep_alive_minutes": 5
}
```

## Server Info — `GET /` or `GET /v1`

Returns server identity, version, status, update availability, and the full list of supported endpoints. Does not require authentication.

```json
{
  "name": "OlliteRT",
  "version": "1.2.0",
  "build": 42,
  "git_hash": "abc1234",
  "status": "running",
  "model": "Gemma-4-E2B-it",
  "uptime_seconds": 3600,
  "update_available": false,
  "allowlist_content_version": 3,
  "allowlist_source": "asset",
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
| `status` | string | `running`, `idle` (keep-alive unloaded), `loading`, `stopped`, `error` |
| `model` | string | Currently loaded model name (omitted if none) |
| `uptime_seconds` | integer | Seconds since RUNNING state (omitted if not running) |
| `update_available` | boolean | `true` if a newer OlliteRT version exists |
| `latest_version` | string | Newest available version (only present when `update_available` is `true`) |
| `release_url` | string | GitHub release URL (only present when `update_available` is `true`) |
| `allowlist_content_version` | integer | Version number of the model allowlist currently cached |
| `allowlist_source` | string | Source of the active allowlist: `"asset"`, `"external:<path>"`, `"empty"`, or `"error"` |
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
| `403` | Client IP access policy rejected the direct network peer |
| `404` | Not Found — model or endpoint doesn't exist |
| `405` | Method Not Allowed — wrong HTTP method for endpoint |
| `413` | Payload Too Large — request body exceeds size limit |
| `500` | Internal server error |
| `503` | Model not loaded or server not ready |

See [Troubleshooting → Connection Issues](../TROUBLESHOOTING.md#connection-issues) for detailed explanations of each error code.

## Prometheus Metrics — `GET /metrics`

Returns server metrics in [Prometheus exposition format](https://prometheus.io/docs/instrumenting/exposition_formats/) (`text/plain; version=0.0.4`). Includes 10 counters and 19 gauges covering throughput, latency, token counts, memory, and more.

For the full list of metrics and Grafana setup, see the [Prometheus Integration Guide](../integrations/PROMETHEUS.md).

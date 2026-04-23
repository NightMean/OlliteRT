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
- [Health](#health--get-health)
- [Error Responses](#error-responses)
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
| `GET`  | `/metrics` | Prometheus metrics (exposition format) |
| `GET`  | `/health` | Health check (add `?metrics=true` for detailed JSON stats) |

## Authentication

Bearer token authentication is optional and can be enabled in the app's Settings screen. See the [Security Guide](../SECURITY.md) for details on authentication and network exposure.

When enabled, include the token in the `Authorization` header:

```
Authorization: Bearer your-token
```

> [!TIP]
> All inference endpoints accept the same core parameters (`temperature`, `top_p`, `top_k`, `max_tokens`, `stream`). The parameter tables below document each endpoint's full set.

## Chat Completions — `POST /v1/chat/completions`

### Request Body

| Parameter | Type | Required | Description |
|:----------|:-----|:--------:|:------------|
| `model` | string | Yes | Model name (e.g. `Gemma-4-E2B-it`) |
| `messages` | array | Yes | Array of message objects (`role` + `content`) |
| `stream` | boolean | No | Enable SSE streaming (default: `false`) |
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

### Streaming Response

When `stream: true`, the response is sent as Server-Sent Events:

```
data: {"id":"chatcmpl-...","choices":[{"delta":{"content":"Hello"},"index":0}]}

data: {"id":"chatcmpl-...","choices":[{"delta":{"content":"!"},"index":0,"finish_reason":"stop"}]}

data: [DONE]
```

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
| `temperature` | number | No | Sampling temperature |
| `max_output_tokens` | integer | No | Maximum tokens to generate |

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
| `response_format` | string | No | `json` (default) or `text` |

Supported audio formats: **WAV**, **MP3**, **OGG** (Vorbis), **FLAC**. Stereo WAV (16-bit PCM) is automatically downmixed to mono; other formats should be mono before sending.

### Response (`response_format: "json"`)

```json
{"text": "The transcribed text from the audio file."}
```

### Response (`response_format: "text"`)

```
The transcribed text from the audio file.
```

### Example (curl)

```bash
curl http://PHONE_IP:8000/v1/audio/transcriptions \
  -H "Authorization: Bearer your-token" \
  -F file=@recording.wav \
  -F response_format=json
```

## Models — `GET /v1/models`

Returns a list of available models.

```json
{
  "object": "list",
  "data": [{
    "id": "Gemma-4-E2B-it",
    "object": "model",
    "owned_by": "ollitert"
  }]
}
```

## Health — `GET /health`

Returns server health status.

```json
{"status": "ok"}
```

With `?metrics=true`, returns detailed JSON metrics including throughput, latency, token counts, and more.

## Error Responses

> [!NOTE]
> All errors follow the standard OpenAI error format, so existing client libraries handle them correctly.

```json
{
  "error": {
    "message": "Model is not loaded",
    "type": "server_error",
    "code": 503
  }
}
```

| Status | When |
|:-------|:-----|
| `400` | Malformed request, missing required fields |
| `401` | Missing or invalid bearer token |
| `503` | Model not loaded or server not ready |
| `500` | Internal server error |

See [Troubleshooting → Connection Issues](../TROUBLESHOOTING.md#connection-issues) for detailed explanations of each error code.

## Prometheus Metrics — `GET /metrics`

Returns server metrics in [Prometheus exposition format](https://prometheus.io/docs/instrumenting/exposition_formats/) (`text/plain; version=0.0.4`). Includes 9 counters and 13 gauges covering throughput, latency, token counts, and more.

For the full list of metrics and Grafana setup, see the [Prometheus Integration Guide](../integrations/PROMETHEUS.md).
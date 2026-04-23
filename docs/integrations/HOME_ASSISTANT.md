# Home Assistant Integration

OlliteRT provides a built-in REST API for monitoring and controlling the server directly from Home Assistant — no HACS, no custom components, just `configuration.yaml`.

## Table of Contents

- [Overview](#overview)
- [REST Sensors](#rest-sensors)
- [REST Commands (Server Control)](#rest-commands-server-control)
- [Automation Examples](#automation-examples)
- [Auto-Generated Config](#auto-generated-config)

---

## Overview

Two ways to use OlliteRT with Home Assistant:

1. **As an LLM brain** — Use OlliteRT as a conversation agent for voice assistants and automations via [Extended OpenAI Conversation](https://github.com/jekalmin/extended_openai_conversation) or [Local OpenAI LLM](https://github.com/skye-harris/hass_local_openai_llm). See the [Client Setup Guide](../CLIENT_SETUP.md#home-assistant) for setup.

2. **As a monitored server** — Poll OlliteRT's health endpoint for status, metrics, and control via REST sensors and commands (documented below).

## REST Sensors

OlliteRT's `GET /health?metrics=true` endpoint returns a JSON payload with server status and performance metrics — perfect for HA's REST sensor platform.

### Available Sensor Data

| Field | Path | Description |
|:------|:-----|:------------|
| Status | `status` | `ok`, `idle` (keep-alive unloaded), `loading`, `stopped` |
| Model | `model` | Currently loaded (or idle-unloaded) model name |
| Uptime | `uptime_seconds` | Seconds since server entered RUNNING state |
| Update Available | `update_available` | `true` if a newer OlliteRT version exists |
| Thinking | `thinking_enabled` | Whether chain-of-thought mode is active |
| Accelerator | `accelerator` | `gpu`, `cpu`, or `gpu,cpu` |
| Idle Unloaded | `is_idle_unloaded` | `true` if model was unloaded by keep-alive |
| Requests | `metrics.requests_total` | Total requests processed |
| Errors | `metrics.errors_total` | Total request errors |
| TTFB (avg) | `metrics.ttfb_avg_ms` | Average time to first token (ms) |
| Decode Speed | `metrics.decode_tokens_per_second` | Last request decode throughput (tokens/s) |
| Peak Decode Speed | `metrics.decode_tokens_per_second_peak` | Peak decode throughput since start |
| Prefill Speed | `metrics.prefill_tokens_per_second` | Last request prefill throughput (tokens/s) |
| Inter-Token Latency | `metrics.inter_token_latency_ms` | Last inter-token latency (ms) |
| Request Latency | `metrics.request_latency_last_ms` | Last request total latency (ms) |
| Avg Latency | `metrics.request_latency_avg_ms` | Average request latency (ms) |
| Peak Latency | `metrics.request_latency_peak_ms` | Peak request latency (ms) |
| Context Usage | `metrics.context_utilization_percent` | Last request context window usage (%) |
| Model Load Time | `metrics.model_load_time_seconds` | Model load/warmup time (seconds) |
| Is Inferring | `metrics.is_inferring` | `true` if a request is currently being processed |
| Text Requests | `metrics.requests_text` | Total text-only requests |
| Image Requests | `metrics.requests_image` | Total image multimodal requests |
| Audio Requests | `metrics.requests_audio` | Total audio multimodal requests |
| Prompt Tokens | `metrics.prompt_tokens_total` | Total prompt tokens (estimated) |
| Generation Tokens | `metrics.generation_tokens_total` | Total generated tokens (estimated) |

### Example `configuration.yaml`

```yaml
rest:
  - resource: "http://PHONE_IP:8000/health?metrics=true"
    scan_interval: 30
    # Uncomment if bearer auth is enabled:
    # headers:
    #   Authorization: "Bearer your-token"
    sensor:
      - name: "OlliteRT Status"
        value_template: "{{ value_json.status }}"
      - name: "OlliteRT Model"
        value_template: "{{ value_json.model | default('none') }}"
      - name: "OlliteRT Uptime"
        value_template: "{{ value_json.uptime_seconds | default(0) }}"
        unit_of_measurement: "s"
      - name: "OlliteRT Thinking"
        value_template: "{{ value_json.thinking_enabled | default(false) }}"
      - name: "OlliteRT Accelerator"
        value_template: "{{ value_json.accelerator | default('unknown') }}"
      - name: "OlliteRT Idle"
        value_template: "{{ value_json.is_idle_unloaded | default(false) }}"
      - name: "OlliteRT Requests"
        value_template: "{{ value_json.metrics.requests_total | default(0) }}"
      - name: "OlliteRT Errors"
        value_template: "{{ value_json.metrics.errors_total | default(0) }}"
      - name: "OlliteRT TTFB"
        value_template: "{{ value_json.metrics.ttfb_avg_ms | default(0) }}"
        unit_of_measurement: "ms"
      - name: "OlliteRT Decode Speed"
        value_template: "{{ value_json.metrics.decode_tokens_per_second | default(0) | round(1) }}"
        unit_of_measurement: "t/s"
      - name: "OlliteRT Context Usage"
        value_template: "{{ value_json.metrics.context_utilization_percent | default(0) | round(1) }}"
        unit_of_measurement: "%"
```

## REST Commands (Server Control)

Control OlliteRT remotely from HA automations and scripts.

### Available Commands

| Endpoint | Method | Description | Payload |
|:---------|:-------|:------------|:--------|
| `/v1/server/stop` | `POST` | Stop the server | None |
| `/v1/server/reload` | `POST` | Reload the current model | None |
| `/v1/server/thinking` | `POST` | Toggle thinking mode | `{"enabled": true}` or `{"enabled": false}` |
| `/v1/server/config` | `POST` | Update inference and behavior settings | Any subset of fields (see below) |

#### `/v1/server/config` Fields

Send an empty body to read current config, or any subset of fields to update:

| Field | Type | Description |
|:------|:-----|:------------|
| `temperature` | number | Sampling temperature (0.0 - 2.0) |
| `max_tokens` | integer | Maximum tokens to generate |
| `top_k` | integer | Top-k sampling |
| `top_p` | number | Nucleus sampling threshold |
| `thinking_enabled` | boolean | Enable chain-of-thought mode |
| `auto_truncate_history` | boolean | Auto-drop older messages when context is full |
| `auto_trim_prompts` | boolean | Hard-cut prompts as last resort when context overflows |
| `compact_tool_schemas` | boolean | Reduce tool definition sizes in prompts |
| `warmup_enabled` | boolean | Run warmup inference on model load |
| `keep_alive_enabled` | boolean | Enable idle timeout auto-unload |
| `keep_alive_minutes` | integer | Idle timeout duration (1 - 7200) |
| `custom_prompts_enabled` | boolean | Enable custom system prompts |
| `system_prompt` | string | Per-model system instruction text |

> [!IMPORTANT]
> If enabled - All server control endpoints require bearer token authentication. Without auth, anyone on your network can control the server. See the [Security Guide](../SECURITY.md) for details.

### Example `configuration.yaml`

```yaml
rest_command:
  ollitert_stop:
    url: "http://PHONE_IP:8000/v1/server/stop"
    method: POST
    # headers:
    #   Authorization: "Bearer your-token"
    content_type: "application/json"

  ollitert_reload:
    url: "http://PHONE_IP:8000/v1/server/reload"
    method: POST
    # headers:
    #   Authorization: "Bearer your-token"
    content_type: "application/json"

  ollitert_thinking:
    url: "http://PHONE_IP:8000/v1/server/thinking"
    method: POST
    # headers:
    #   Authorization: "Bearer your-token"
    content_type: "application/json"
    payload: '{"enabled": {{ enabled }}}'

  ollitert_config:
    url: "http://PHONE_IP:8000/v1/server/config"
    method: POST
    # headers:
    #   Authorization: "Bearer your-token"
    content_type: "application/json"
    payload: '{{ payload }}'
```

### Automation Examples

> [!TIP]
> Instead of automating temperature changes, you can enable **Ignore Client Sampler Parameters** in OlliteRT Settings → Advanced to always use your own per-model inference settings regardless of what HA sends.

**Lower temperature when switching to a tool-calling automation** (see [Troubleshooting → Tool Calling](../TROUBLESHOOTING.md#tool-calling-experimental) for more tips):

```yaml
automation:
  - alias: "OlliteRT: Set low temperature for HA tools"
    trigger:
      - platform: state
        entity_id: input_boolean.ha_tool_mode
        to: "on"
    action:
      - service: rest_command.ollitert_config
        data:
          payload: '{"temperature": 0.78}'
```



**Extend keep-alive timeout when expecting frequent queries:**

```yaml
automation:
  - alias: "OlliteRT: Extend keep-alive during active hours"
    trigger:
      - platform: time
        at: "08:00:00"
    action:
      - service: rest_command.ollitert_config
        data:
          payload: '{"keep_alive_enabled": true, "keep_alive_minutes": 120}'
```

**Set a system prompt for a specific automation context:**

```yaml
automation:
  - alias: "OlliteRT: Configure for home control"
    trigger:
      - platform: state
        entity_id: input_boolean.home_assistant_mode
        to: "on"
    action:
      - service: rest_command.ollitert_config
        data:
          payload: '{"custom_prompts_enabled": true, "system_prompt": "You are a smart home assistant. Answer concisely."}'
```

## Auto-Generated Config

> [!TIP]
> OlliteRT's Settings screen includes a **"Copy Configuration"** button that generates a complete `configuration.yaml` snippet pre-filled with your device's current IP address, port, and bearer token. No manual editing needed — just paste into your `configuration.yaml`.

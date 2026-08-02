# Client Setup

OlliteRT implements the OpenAI Chat Completions / Responses / Audio APIs **and** the Anthropic Messages API. Any tool or library that lets you set a custom base URL will work out of the box — no plugins, no adapters, no cloud required.

## Table of Contents

- [Connection Settings](#connection-settings)
- [Home Assistant](#home-assistant)
  - [Conversation Agent](#conversation-agent)
  - [Voice Transcription (STT)](#voice-transcription-stt)
- [Open WebUI](#open-webui)
- [OpenClaw](#openclaw)
- [Claude Code](#claude-code)
- [Anthropic SDKs](#anthropic-sdks)
- [Python (OpenAI SDK)](#python-openai-sdk)
- [curl](#curl)

---

## Connection Settings

Every integration uses the same three values:

| Setting | Value | Where to find it |
|:--------|:------|:-----------------|
| **Base URL** | `http://PHONE_IP:8000/v1` | Status screen → tap the endpoint to copy |
| **API Key** | Your bearer token (if auth is enabled — enter any value if the client requires it but auth is disabled) | Settings → Server Configuration |
| **Model** | e.g. `Gemma-4-E2B-it` | Any model name shown on the Models screen |

> [!TIP]
> The port (`8000`) is configurable in Settings → Server Configuration. Replace `PHONE_IP` with your device's local IP address shown on the Status screen.

The **Listen on** setting controls which clients can connect. Keep **Local network (all interfaces)** for other devices, use **This device only (loopback)** for apps running on the same phone, or select a numeric custom IPv4/IPv6 address assigned to the phone. Optional **Client IP access** rules accept exact addresses and CIDR networks; the Status screen shows the active policy.

If you're having trouble connecting, see [Troubleshooting → Connection Issues](TROUBLESHOOTING.md#connection-issues).

## Home Assistant

OlliteRT integrates with Home Assistant in two ways — as a [conversation agent](#conversation-agent) for LLM-powered automations, and as a [speech-to-text engine](#voice-transcription-stt) for voice pipelines. Each can be set up independently or combined in the same Assist pipeline.

**See also:** [Home Assistant REST API](integrations/HOME_ASSISTANT.md) — monitor server status, reload models, and adjust inference settings directly from HA automations using built-in REST sensors and commands.

### Conversation Agent

> [!NOTE]
> Home Assistant currently requires a custom integration for OlliteRT to work with conversation. The recommended options are [Custom Conversation](https://github.com/michelle-avery/custom-conversation) (recommended), [Home LLM](https://github.com/acon96/home-llm/), [Local OpenAI LLM](https://github.com/skye-harris/hass_local_openai_llm), and [Extended OpenAI Conversation](https://github.com/jekalmin/extended_openai_conversation).

Pick one of the following integrations, install and configure it, then continue with the [common steps below](#after-installation).

#### Option A — Custom Conversation (recommended)

**Step 1 — Install [Custom Conversation](https://github.com/michelle-avery/custom-conversation) via [HACS](https://hacs.xyz/).**

**Step 2 — Configure the integration** (Settings → Devices & Services → Add Integration → search "Custom Conversation" → Choose **OpenAI** as LLM Provider):

| Field | Value |
|:------|:------|
| **Base URL** | `http://PHONE_IP:8000/v1` |
| **API Key** | Your bearer token (if auth is enabled — enter any value if required but auth is disabled) |
| **Model** | `Gemma-4-E2B-it` (or any model name shown on the Models screen) |

**Step 3 — Open Custom Conversation Integration settings**, click on settings button under Services → Custom Conversation and choose **Assist** as the API to expose to the LLM and select the desired LLM from the dropdown. Then click on save.

**Step 4 — Continue with [After Installation](#after-installation) to set up the Assist pipeline and test.**

> [!NOTE]
> **Custom Conversation** was easiest to setup without any additional configuration and produced the most consistent results during testing. This however, does not mean that it is the best integration for every use case. Your mileage may vary.

#### Option B — Home LLM

[Home LLM](https://github.com/acon96/home-llm/) is built specifically for local/self-hosted models and uses Home Assistant's native Assist tool-calling API for device control.

**Step 1 — Install [Home LLM](https://github.com/acon96/home-llm/) via [HACS](https://hacs.xyz/).**

**Step 2 — Add the integration** (Settings → Devices & Services → Add Integration → search "Local LLM"):

1. Select **"OpenAI Compatible 'Conversations' API"** as the backend
2. Configure the connection:

| Setting | Value |
|:--------|:------|
| **API Hostname** | `PHONE_IP` (without http:// and port number) |
| **API Port** | `8000` |
| **API Key** | Your bearer token (if auth is enabled — enter any value if the client requires it but auth is disabled) |
| **API Path** | `v1` |
| **Model** | e.g. `Gemma-4-E2B-it` |

**Step 3 — Configure the conversation agent** (in the integration options after adding):

Select model from the dropdown (auto-populated from OlliteRT's `/v1/models`) → Press **Next** → Select **Assist** as Selected LLM API(s) → Scroll down and **Enable Legacy Tool Calling** → Press **Submit**.

**Step 4 — Continue with [After Installation](#after-installation) to set up the Assist pipeline and test.**

#### Option C — Local OpenAI LLM

**Step 1 — Install [Local OpenAI LLM](https://github.com/skye-harris/hass_local_openai_llm) via [HACS](https://hacs.xyz/).**

**Step 2 — Configure the integration** (Settings → Devices & Services → Add Integration):

| Field | Value |
|:------|:------|
| **Base / Server URL** | `http://PHONE_IP:8000/v1` |
| **API Key** | Your bearer token (if auth is enabled — enter any value if required but auth is disabled) |
| **Model** | `Gemma-4-E2B-it` (or any model name shown on the Models screen) |

**Step 3 — Add a conversation agent** (in the integration options after adding):

Select model from the dropdown (auto-populated from OlliteRT's `/v1/models`)→ Select **Assist** under Tool Providers → Press **Submit**.

**Step 4 — Continue with [After Installation](#after-installation) to set up the Assist pipeline and test.**

#### Option D — Extended OpenAI Conversation

**Step 1 — Install [Extended OpenAI Conversation](https://github.com/jekalmin/extended_openai_conversation) via [HACS](https://hacs.xyz/).**

**Step 2 — Configure the integration** (Settings → Devices & Services → Add Integration):

| Field | Value |
|:------|:------|
| **Base / Server URL** | `http://PHONE_IP:8000/v1` |
| **API Key** | Your bearer token (if auth is enabled — enter any value if required but auth is disabled) |
| **Model** | `Gemma-4-E2B-it` (or any model name shown on the Models screen) |

> [!IMPORTANT]
> Configuring a model for Extended OpenAI Conversation needs to be done in the Integration settings > Cog Wheel icon > `chat_model` field.
> By default integration uses `gpt-4o-mini` which is not available on OlliteRT. Change it to `Gemma-4-E2B-it` or any other model from the Models screen, otherwise Assist will not work.

**Step 3 — Continue with [After Installation](#after-installation) to set up the Assist pipeline and test.**

---

#### After Installation

These steps apply to all integrations above.

**Create an Assist pipeline** — Settings → Voice assistants → Add Assistant. Select your chosen integration as the **Conversation Agent**.

**Expose entities** — Settings → Voice Assistants → Expose tab. Only exposed entities are available to the model. Keep the number reasonable to avoid exceeding the model's context window.

**Test it** — Open the Assist UI and try "Turn on [Name of exposed entity]" to verify tool calling works. See [Troubleshooting → Tool Calling](TROUBLESHOOTING.md#tool-calling-experimental) if it doesn't respond correctly.

> [!TIP]
> **For better tool calling results:**
> - Keep entity names and aliases short and simple — the model matches concise names (e.g. "Living Room Light") more reliably than long or complex ones
> - Some integrations limit temperature to 0–1, but Gemma models support 0–2. Enable **Ignore Client Sampler Parameters** in Settings → Model Behaviour to discard client-sent values and use your own per-model inference settings instead

### Voice Transcription (STT)

> [!NOTE]
> Home Assistant currently requires a custom integration for Speech-to-Text to work with OlliteRT. The recommended option is [OpenAI STT for Home Assistant](https://github.com/NightMean/OpenAI_STT_HASS).

**Step 1 — Install [OpenAI STT for Home Assistant](https://github.com/NightMean/OpenAI_STT_HASS) via [HACS](https://hacs.xyz/) and Restart Home Assistant**

**Step 2 — Configure the integration** (Settings → Devices & Services → Add Integration → search "OpenAI STT"):

| Field | Value |
|:------|:------|
| **Name** | Any name to identify this STT provider (e.g. `OlliteRT STT`) |
| **API URL** | `http://PHONE_IP:8000/v1` |
| **API Key** | Your bearer token (if auth is enabled — enter any value if required but auth is disabled) |
| **Model** | Auto-discovered from OlliteRT — select the model from the dropdown |

**Step 3 — Enable Force Transcription** in OlliteRT Settings → Home Assistant. This instructs the model to transcribe audio rather than respond to it conversationally.

**Step 4 — Set the STT provider** in your Assist pipeline (Settings → Voice assistants → edit your assistant):

**Step 5 — Test it.** Use the voice input in the Assist UI and verify the transcribed text appears correctly.
See [Troubleshooting → Voice Transcription](TROUBLESHOOTING.md#voice-transcription) if it doesn't respond correctly.

## Open WebUI

[Open WebUI](https://github.com/open-webui/open-webui) is a self-hosted ChatGPT-style interface. OlliteRT appears as an OpenAI-compatible backend.

**Setup:**

1. Go to **Settings → Connections**
2. Under **OpenAI API**, click **+** to add a new connection
3. Set the URL to `http://PHONE_IP:8000/v1`
4. Enter your bearer token as the API key (if auth is enabled)
5. Click the refresh icon — your loaded model will appear in the model selector

> [!TIP]
> Open WebUI supports streaming, markdown rendering, and conversation history — making it a great desktop companion for OlliteRT.
> Voice input and audio transcription work out of the box — no additional configuration needed. Just use the voice mode button in the chat UI.

**Tool Calling:**

> [!IMPORTANT]
> To use tools (function calling) with OlliteRT in Open WebUI, you need to set the Function Calling mode to **Native**. By default, Open WebUI does not send tool calls natively to the backend.

The recommended approach is to set it globally for all models:

1. Go to **Admin Panel → Settings → Models**
2. Click the **Settings button** (top right of the models list)
3. Under **Model Parameters**, set **Function Calling** to `Native`
4. Save

This applies to all existing and future models. You can also override per-model (edit a specific model → Model Parameters → Function Calling → Native) or per-chat (Chat Controls → Advanced Params → Function Calling → Native).

See the [Open WebUI docs on Native mode](https://docs.openwebui.com/features/extensibility/plugin/tools/#how-to-enable-native-mode-agentic-mode) for more details.

## OpenClaw

[OpenClaw](https://github.com/openclaw/openclaw) is a self-hosted personal AI assistant gateway. Instead of a web chat UI, it routes AI responses through messaging platforms you already use — WhatsApp, Telegram, Discord, and 30+ others.

> [!NOTE]
> While OpenClaw works with OlliteRT, results may vary due to the small context windows of on-device models. OpenClaw's multi-platform routing and conversation management can consume significant context, leaving less room for actual responses.
>
> Tool calling will highly likely not work — the tool definitions combined with OpenClaw's own context will exceed most models' context windows.
>
> Using a larger model with a bigger context window can improve the experience. See the [Model Guide](MODELS.md) for context window sizes.

**Connect to OlliteRT** by adding a custom provider in `~/.openclaw/openclaw.json`:

```json5
{
  models: {
    providers: {
      ollitert: {
        baseUrl: "http://PHONE_IP:8000/v1",
        apiKey: "your-token",
        api: "openai-completions"
      }
    }
  }
}
```

OpenClaw has built-in provider plugins for Ollama, vLLM, LM Studio, and others. Any server exposing `/v1/chat/completions` works via the custom provider config above.

## Claude Code

[Claude Code](https://docs.claude.com/en/docs/claude-code/overview) targets the Anthropic Messages API. OlliteRT exposes that API on `/v1/messages`, so Claude Code can drive your phone with no proxy.

> [!WARNING]
> **Experimental — not recommended for daily coding work.** Claude Code ships with a multi-thousand-token system prompt and a large set of tool definitions (Bash, Edit, Read, Write, Grep, Glob, Task, etc.) that it sends on every request. On-device models in the Gemma-4-E2B / 3n class do not have the context budget or instruction-following headroom to drive that workload reliably — expect long prefill, frequent tool-call mistakes, and the LiteRT-LM #2418 parse failures noted below. Coding harnesses with a small system prompt and a narrower tool surface (for example [Pi Agent](https://pi.dev/)) running against the OpenAI-compatible `/v1/chat/completions` endpoint are a much better fit for this hardware. Treat Claude Code support here as a smoke-test for the Anthropic API, not a production workflow.

**Setup** — set two environment variables before launching Claude Code:

```bash
ANTHROPIC_BASE_URL=http://PHONE_IP:8000 \
ANTHROPIC_AUTH_TOKEN=your-token \
claude
```

`ANTHROPIC_AUTH_TOKEN` is mapped to the `x-api-key` header. The `/v1` segment is appended automatically — set the base URL to the host root, not to `…/v1`. By default OlliteRT does not require auth, so the value is ignored — pass any non-empty string (Claude Code requires the variable to be set). If you've turned on **Require Bearer Token** under Settings → Server Configuration, the value must exactly match the token configured there. The phone never relays the token to the real Anthropic API.

**Pick the right model** in OlliteRT first — Claude Code sends a long system prompt and many tools (Bash, Edit, Read, etc.), so a Gemma-4-E2B-it or larger is the practical floor.

> [!WARNING]
> **Tool calls can fail with HTTP 500 on Gemma 4.** When the model emits a tool call whose argument is a string with quoted content (most Bash / Edit calls), LiteRT-LM 0.11.0 / 0.12.0's native function-call parser raises `INVALID_ARGUMENT` and the request errors out. Tracking upstream: <https://github.com/google-ai-edge/LiteRT-LM/issues/2418>. Workaround: turn off **Settings → Schema Injection** in OlliteRT so tool calls flow through the text-mode parser instead.

> [!TIP]
> If a request appears to hang for 30–60 s before producing output, that is on-device prefill — not a network issue. OlliteRT emits Anthropic `ping` events every 10 s during prefill so Claude Code's SSE timeout doesn't trigger; you can confirm the stream is alive in **Settings → Logs**.

## Anthropic SDKs

The official [Anthropic Python SDK](https://github.com/anthropics/anthropic-sdk-python) and [TypeScript SDK](https://github.com/anthropics/anthropic-sdk-typescript) both accept a `base_url` override and work without modification.

**Python:**

```python
from anthropic import Anthropic

client = Anthropic(
    base_url="http://PHONE_IP:8000",
    api_key="your-token",  # ignored when auth is disabled; must match the configured token when auth is enabled in Settings
)

resp = client.messages.create(
    model="Gemma-4-E2B-it",
    max_tokens=256,
    messages=[{"role": "user", "content": "Say hello"}],
)
print(resp.content[0].text)
```

**TypeScript:**

```typescript
import Anthropic from "@anthropic-ai/sdk";

const client = new Anthropic({
  baseURL: "http://PHONE_IP:8000",
  apiKey: "your-token", // ignored when auth is disabled; must match the configured token when auth is enabled in Settings
});

const msg = await client.messages.create({
  model: "Gemma-4-E2B-it",
  max_tokens: 256,
  messages: [{ role: "user", content: "Say hello" }],
});
console.log(msg.content[0].type === "text" ? msg.content[0].text : "");
```

Streaming works via `client.messages.stream(...)` (Python) and `client.messages.stream({...})` (TypeScript) without further configuration.

## Python (OpenAI SDK)

The official [OpenAI Python SDK](https://github.com/openai/openai-python) works with OlliteRT by pointing it at your device's URL.

**Install:**

```bash
pip install openai
```

**Streaming:**

```python
from openai import OpenAI

client = OpenAI(
    base_url="http://PHONE_IP:8000/v1",
    api_key="your-token"  # if bearer auth is enabled
)

for chunk in client.chat.completions.create(
    model="Gemma-4-E2B-it",
    messages=[{"role": "user", "content": "Explain quantum computing in simple terms"}],
    stream=True
):
    print(chunk.choices[0].delta.content or "", end="")
```

**Non-streaming:**

```python
response = client.chat.completions.create(
    model="Gemma-4-E2B-it",
    messages=[{"role": "user", "content": "Hello!"}]
)
print(response.choices[0].message.content)
```

**Tool calling:**

```python
response = client.chat.completions.create(
    model="Gemma-4-E2B-it",
    messages=[{"role": "user", "content": "What time is it?"}],
    tools=[{
        "type": "function",
        "function": {
            "name": "get_current_time",
            "description": "Get the current date and time",
            "parameters": {"type": "object", "properties": {}}
        }
    }]
)

message = response.choices[0].message
if message.tool_calls:
    print(f"Tool call: {message.tool_calls[0].function.name}")
```

> [!NOTE]
> Tool calling is experimental. By default it uses SDK schema injection for structured output; disable **Tool Schema Injection** in Settings → Model Behaviour to fall back to prompt-based parsing. See [Troubleshooting → Tool Calling](TROUBLESHOOTING.md#tool-calling-experimental) if results are unexpected.

## curl

**Non-streaming:**

```bash
curl http://PHONE_IP:8000/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "Gemma-4-E2B-it",
    "messages": [{"role": "user", "content": "Hello!"}]
  }'
```

**Streaming:**

```bash
curl http://PHONE_IP:8000/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "Gemma-4-E2B-it",
    "messages": [{"role": "user", "content": "Hello!"}],
    "stream": true
  }'
```

**With bearer auth:**

```bash
curl http://PHONE_IP:8000/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your-token" \
  -d '{
    "model": "Gemma-4-E2B-it",
    "messages": [{"role": "user", "content": "Hello!"}]
  }'
```

**List models:**

```bash
curl http://PHONE_IP:8000/v1/models
```

**Health check:**

```bash
curl http://PHONE_IP:8000/health?metrics=true
```

See the [API Reference](api/API.md) for the full list of endpoints and parameters.


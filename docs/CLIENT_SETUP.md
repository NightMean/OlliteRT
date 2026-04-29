# Client Setup

OlliteRT implements the standard OpenAI API. Any tool or library that lets you set a custom base URL will work out of the box — no plugins, no adapters, no cloud required.

## Table of Contents

- [Connection Settings](#connection-settings)
- [Home Assistant](#home-assistant)
  - [Conversation Agent](#conversation-agent)
  - [Voice Transcription (STT)](#voice-transcription-stt)
- [Open WebUI](#open-webui)
- [OpenClaw](#openclaw)
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

If you're having trouble connecting, see [Troubleshooting → Connection Issues](TROUBLESHOOTING.md#connection-issues).

## Home Assistant

OlliteRT integrates with Home Assistant in two ways — as a [conversation agent](#conversation-agent) for LLM-powered automations, and as a [speech-to-text engine](#voice-transcription-stt) for voice pipelines. Each can be set up independently or combined in the same Assist pipeline.

**See also:** [Home Assistant REST API](integrations/HOME_ASSISTANT.md) — monitor server status, reload models, and adjust inference settings directly from HA automations using built-in REST sensors and commands.

### Conversation Agent

> [!NOTE]
> Home Assistant currently requires a custom integration for OlliteRT to work with conversation. The two recommended options are [Extended OpenAI Conversation](https://github.com/jekalmin/extended_openai_conversation) and [Local OpenAI LLM](https://github.com/skye-harris/hass_local_openai_llm).

**Step 1 — Install [Extended OpenAI Conversation](https://github.com/jekalmin/extended_openai_conversation) or [Local OpenAI LLM](https://github.com/skye-harris/hass_local_openai_llm) via [HACS](https://hacs.xyz/).**

**Step 2 — Configure the integration** (Settings → Devices & Services → Add Integration):

| Field | Value |
|:------|:------|
| **Base / Server URL** | `http://PHONE_IP:8000/v1` |
| **API Key** | Your bearer token (if auth is enabled — enter any value if required but auth is disabled) |
| **Model** | `Gemma-4-E2B-it` (or any model name shown on the Models screen) |

> [!IMPORTANT]
> Configuring a model for Extended OpenAI Conversation needs to be done in the Integration settings > Cog Wheel icon > `chat_model` field.
> By default integration uses `gpt-4o-mini` which is not available on OlliteRT.

**Step 3 — Create an Assist pipeline** (Settings → Voice assistants → Add Assistant):

| Field | Value |
|:------|:------|
| **Conversation agent** | Your chosen integration |
| **Model** | `Gemma-4-E2B-it` (or any model name shown on the Models screen) |

**Step 4 — Test it.** Open the Assist UI and try "Turn on the living room lights" to verify tool calling works. See [Troubleshooting → Tool Calling](TROUBLESHOOTING.md#tool-calling-experimental) if it doesn't respond correctly.

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


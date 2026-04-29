# Troubleshooting

- [Connection Issues](#connection-issues)
- [Performance](#performance)
- [Models](#models)
- [Multimodal](#multimodal)
- [Thinking Mode](#thinking-mode)
- [Server](#server)
- [Model Sources & Updates](#model-sources--updates)
- [Tool Calling](#tool-calling-experimental)
- [Voice Transcription](#voice-transcription)

## Connection Issues

### My client can't connect to OlliteRT

See the [Client Setup Guide](CLIENT_SETUP.md) for step-by-step connection instructions for each client.

1. **Check the IP address** — The Status screen shows the server's IP and port. Make sure your client is using exactly that URL (e.g. `http://192.168.1.100:8000/v1`)
2. **Same network** — Your client and phone must be on the same Wi-Fi network
3. **Server is running** — Check the Status screen shows "Running" (not "Loading" or "Stopped")
4. **Port not blocked** — Some routers block inter-device communication (AP isolation). Check your router settings
5. **Bearer token** — If authentication is enabled, make sure your client includes the `Authorization: Bearer your-token` header
6. **Firewall** — Your router's firewall may block inter-device communication on the local network

### "Connection refused" error

The server isn't running or is on a different port. Check the Status screen and verify the port in Settings.

### "401 Unauthorized" error

Bearer token authentication is enabled but your client isn't sending the correct token. Add the `Authorization: Bearer your-token` header to your requests. See the [Security Guide](SECURITY.md) for details on authentication.

### "503 Model is reloading after idle timeout, please retry"

The model was unloaded by [keep-alive](FAQ.md#what-is-keep-alive--idle-unload) and is currently reloading because another request triggered the reload. Retry after a few seconds — the model will be ready once the reload completes.

### "503 No model is currently loaded"

The server is running but no model is available. This can happen in rare edge cases (e.g. model failed to load). Check the Logs screen for details and try reloading the model from the Models screen.

### "400 No model loaded"

A server control endpoint (`/v1/server/reload`, `/v1/server/thinking`, `/v1/server/config`) was called but no model is loaded. Load a model first.

### "500 Internal Error"

An unexpected error occurred while generating a response. Common causes:
- **Context window exceeded** — the conversation is too long for the model. Prompt compaction can be enabled in Settings → Context Management to handle this automatically, but it may reduce response quality by dropping older context. See the [FAQ](FAQ.md#what-is-prompt-compaction) for details
- **Model crash** — the LiteRT runtime encountered an error. Try reloading the model
- Check the Logs screen for the full error message. If the issue persists, [open a bug report](https://github.com/NightMean/OlliteRT/issues/new?template=01_bug_report.yml) — the template includes instructions for exporting logs

## Performance

### Why is it so slow?

Phone GPUs are powerful for their size but much slower than desktop GPUs or cloud AI services. Performance varies significantly between devices — a recent flagship (2023+) with a high-end GPU will generate responses noticeably faster than an older or budget phone.

Tips to improve speed:
- Use the GPU accelerator (default) instead of CPU
- Use a smaller model if speed matters more than quality
- Close other apps to free RAM
- Keep the phone cool — thermal throttling reduces performance

### Why is the first response slow?

The first response after starting the server or after a keep-alive unload includes model loading time (5-30 seconds depending on model size and device speed). Subsequent responses are much faster because the model stays in memory.

Larger inputs also slow down responses — the more text sent in a request (whether a long message or accumulated conversation history), the longer the model takes to process it before generating a reply.

## Models

### "Storage Full" but I have space

OlliteRT reserves 3 GB of free space for Android system operations. If you have 5 GB free and try to download a 3 GB model, the check will warn you because only 2 GB would remain after download (below the 3 GB reserve). You can tap "Download Anyway" to bypass this at your own risk.

### The model keeps unloading

The [keep-alive](FAQ.md#what-is-keep-alive--idle-unload) feature automatically unloads the model after a configurable idle period to free RAM. Adjust or disable this in Settings → Auto-Launch & Behavior under "Keep Alive Timeout."

### Model download fails

- **Gated models** — some models require accepting a license agreement on HuggingFace before downloading. Add your HuggingFace token in Settings (or sign in via OAuth when prompted during download) and accept the agreement on the model's HuggingFace page
- **Network errors** — if a download fails mid-way, try again. Downloads resume from where they left off unless being cancelled by user
- **No internet** — the app needs an internet connection to download models. After downloading, the app works fully offline.

### Model import fails

See [Importing Your Own Models](MODELS.md#importing-your-own-models) for the full import guide.

- **Wrong file format** — only `.litertlm` files are supported. GGUF, ONNX, and other formats cannot be used (LiteRT runtime limitation)
- **Not enough storage** — imported models are copied to app storage, so you need at least double the model's file size in free space. The original file can be safely deleted after import
- **Corrupted file** — if the model fails to load after import, the file may be corrupted or incomplete. Try re-downloading it from the source or re-importing it if it was manually imported.

### Long conversations fail / context window exceeded

When a conversation exceeds the model's context window, the request may fail with an error. By default, OlliteRT does not automatically handle this. Enable prompt compaction in Settings → Context Management to automatically truncate older messages when the context is full. See the [FAQ](FAQ.md#what-is-prompt-compaction) for details on the three available strategies.

## Multimodal

### Images not working

- **Check model capabilities** — only models with Vision capability support image input. See the [Model Guide](MODELS.md#supported-models) for which models support what
- **Imported models** — capabilities are not auto-detected for imported models. Tap the edit icon on the model card to enable vision. The model itself must actually support the capability
- **Correct API format** — images must be sent as base64-encoded `data:image/...;base64,...` URIs in the API request content array. Clients like Open WebUI handle this automatically, but if you're using a custom client, make sure it sends images in the correct format

### Audio not working

- **Check model capabilities** — only models with Audio capability support audio input (Gemma 4, Gemma 3n). See the [Model Guide](MODELS.md#supported-models) for which models support what
- **Imported models** — capabilities are not auto-detected for imported models. Tap the edit icon on the model card to enable audio. The model itself must actually support the capability
- **Supported formats** — WAV, MP3, OGG (Vorbis), and FLAC are supported. WebM and other formats will be rejected with a descriptive error
- **Mono audio only** — the LiteRT runtime only processes mono audio. Stereo WAV files (16-bit PCM) are automatically downmixed to mono. Non-WAV stereo files (MP3, OGG, FLAC) are passed through unchanged — ensure they are mono before sending
- **File size limit** — audio files must be under 25 MB for the `/v1/audio/transcriptions` endpoint (multipart form data). For inline audio in chat completions, use base64-encoded `input_audio` content parts
- **Two ways to send audio** — inline via `input_audio` content parts in `/v1/chat/completions` (for conversational audio — text is optional, audio-only requests are valid), or via the dedicated `/v1/audio/transcriptions` endpoint (for transcription). See the [API Reference](api/API.md#multimodal-content) for format details
- **Model responds instead of transcribing** — multimodal models may reply conversationally to audio instead of transcribing it. This is expected behavior — the model treats audio as conversational input. Enable **Force Transcription** in Settings → Home Assistant to instruct the model to output only the transcribed text. This is primarily needed for voice assistants (e.g. Home Assistant STT) that expect plain transcription. Note: the setting applies to all clients using the `/v1/audio/transcriptions` endpoint, which may affect non-voice-assistant workflows
- **Log card doesn't show "my voice" / request body** — audio requests send binary audio data, not text. Unlike chat requests where the prompt is readable text, the input for audio endpoints is raw audio bytes that can't be displayed. The log card shows request metadata (file size, format, parameters) instead. The "Response" or "Transcription" section shows the model's text output — this is the only text produced during the request

## Thinking Mode

### Thinking doesn't work / no reasoning output

- **Check model support** — only Gemma 4 E2B and E4B support thinking mode. Other models will ignore the setting
- **Enable per model** — thinking is toggled per model in the inference settings (tap the gear icon on a model card), not in the global Settings screen
- **Responses are slower** — this is expected. The model generates its reasoning process before the final answer, which takes longer. The reasoning appears inside `<think>...</think>` tags in the response

## Server

### The server crashes / stops unexpectedly

- **Out of memory** — Android kills background processes when RAM is low. Try a smaller model or close other apps. See [RAM Requirements](MODELS.md#ram-requirements) for per-model recommendations
- **Thermal shutdown** — prolonged heavy use can overheat the phone. In mild cases Android throttles the CPU/GPU (slower responses), in severe cases it shuts down the device entirely to protect the battery
- **Battery optimization** — OlliteRT requests battery optimization exemption during initial setup, but if it was denied or later revoked, Android may kill the server in the background. Re-enable it in Android Settings → Apps → OlliteRT → Battery → Unrestricted

> [!TIP]
> OlliteRT shows a warning banner at the top of the Models screen if battery optimization exemption is missing. Tap the banner to go directly to the setting.

### Auto-start on boot doesn't work

1. Make sure the toggle is enabled in OlliteRT Settings
2. Make sure battery optimization is disabled for OlliteRT — the app requests this during initial setup, but if it was denied or revoked, re-enable it in Android Settings → Apps → OlliteRT → Battery → Unrestricted
3. A default model must be selected for auto-start to know which model to load

> [!TIP]
> Some manufacturers (Xiaomi, Huawei, Samsung) have additional app-killing features. Search your phone brand on [Don't kill my app!](https://dontkillmyapp.com/) for device-specific instructions.

### No notification showing

OlliteRT needs notification permission to show the foreground service notification. If you denied the permission:

1. Go to Android Settings → Apps → OlliteRT → Notifications
2. Enable notifications
3. Make sure the server notification channel is not muted

> [!TIP]
> OlliteRT shows a warning banner at the top of the Models screen if notification permission is missing. Tap the banner to go directly to the setting.

> [!NOTE]
> The server will still run without the notification, but Android may kill it in the background more aggressively.

## Model Sources & Updates

### Custom model source not loading / shows no models

- **Check the URL** — the URL must point directly to a JSON file following the [Model Allowlist Schema](MODEL_ALLOWLIST_SCHEMA.md). If using GitHub, use the **raw** URL (e.g. `https://raw.githubusercontent.com/...`), not the GitHub page URL
- **Check JSON format** — the JSON must have a top-level `models` array. See [MODEL_ALLOWLIST_SCHEMA.md](MODEL_ALLOWLIST_SCHEMA.md) for the required structure
- **Source is disabled** — disabled model sources don't appear on the Models screen. Check Settings → Model Sources and make sure the source is toggled on
- **Network error** — the source URL may be unreachable. OlliteRT shows a banner on the Models screen when sources are offline (e.g. "2 of 3 model sources are unreachable"). Pull-to-refresh to retry

### Model update notifications not appearing

- **Notification permission** — make sure OlliteRT has notification permission and the "Model Updates" notification channel is not muted in Android Settings → Apps → OlliteRT → Notifications
- **No updates available** — the background worker checks for updates approximately every 24 hours. If no newer model files exist in the source, no notification is shown
- **Battery optimization** — if OlliteRT is battery-optimized, Android may prevent the background worker from running. See [Auto-start on boot doesn't work](#auto-start-on-boot-doesnt-work) for how to disable battery optimization

### Models from a deleted source still appear

When you delete a model source and navigate back, the Models screen refreshes automatically — models from that source should disappear immediately. If they persist, pull-to-refresh on the Models screen. Downloaded model files are not deleted when a source is removed — only the listing is removed.

## Tool Calling (Experimental)

OlliteRT supports two tool calling modes:

- **Tool Schema Injection (default)** — Tool schemas are injected directly into the model's context via the LiteRT SDK. The model returns structured tool call objects, producing more reliable results. Configurable in Settings → Model Behaviour → Tool Schema Injection.
- **Prompt-based (fallback)** — Tool definitions are embedded in the text prompt and the model's output is parsed for tool call patterns. Used when schema injection is disabled or as a fallback when the model doesn't return structured calls.

### Tool calling doesn't work / returns wrong results

- **Try disabling Tool Schema Injection** — if your model doesn't support SDK-level schema injection, disable it in Settings → Model Behaviour. The app will fall back to prompt-based tool calling
- **Use the right model** — only Gemma 4 E2B and E4B support tool calling reliably. Smaller models (Gemma 3 1B, Qwen 2.5, DeepSeek-R1) may ignore tool instructions entirely or produce malformed output
- **Keep tool definitions simple** — use short, clear names and descriptions. The more tools you define, the more context they consume and the harder it is for the model to follow them correctly
- **Too many tools** — large tool sets (e.g. Home Assistant with 20+ device control functions) can exceed the model's context window. When using prompt-based mode, you can enable **Compact Tool Schemas** in Settings → Context Management to automatically reduce tool definitions when context is tight. See the [FAQ](FAQ.md#what-is-prompt-compaction) for details on all compaction strategies
- **Lower the temperature** — higher temperatures increase randomness, which can cause the model to include extra text in tool arguments or call the wrong function. If your client limits temperature to 0–1 (Gemma supports 0–2), enable **Ignore Client Sampler Parameters** in Settings → Model Behaviour to use your own inference settings instead
- **Check the Logs screen** — expand the response body to see what the model actually output

### Streaming and tool calling

When tools are present in a request, responses are **buffered** instead of streamed in real time. This is because tool call detection can only happen after the full output is available. Once generation completes, OlliteRT either emits the response as a tool call or flushes it as regular text. Total latency is unchanged, but you won't see tokens appear progressively for tool-calling requests.

## Voice Transcription

### Transcription returns a conversational reply instead of transcribed text

Multimodal models treat audio as conversational input by default — they respond to what they hear rather than transcribing it. To get plain transcription output, enable **Force Transcription** in OlliteRT Settings → Home Assistant. This instructs the model to output only the transcribed text instead of replying conversationally.

> [!NOTE]
> Force Transcription applies to all clients using the `/v1/audio/transcriptions` endpoint, which may affect non-voice-assistant workflows (e.g. Open WebUI audio input).

### Transcription endpoint returns an error

1. **Check the URL** — your client's base URL must point to `http://PHONE_IP:8000/v1`. The client appends `/audio/transcriptions` automatically
2. **Check the model** — the model loaded on OlliteRT must support Audio capability. See the [Model Guide](MODELS.md#supported-models) for which models support audio
3. **Bearer auth** — if authentication is enabled, make sure your client sends the `Authorization: Bearer your-token` header
4. **Client-specific setup** — see [Client Setup](CLIENT_SETUP.md#home-assistant) for per-client configuration guides (Home Assistant, Open WebUI, etc.)

### Transcription is empty or garbled

- **Check audio format** — WAV, MP3, OGG (Vorbis), and FLAC are supported. See [Multimodal → Audio not working](#audio-not-working) for format details
- **Check model capabilities** — only models with Audio capability support audio input (Gemma 4, Gemma 3n). See the [Model Guide](MODELS.md#supported-models)
- **Try a different model** — transcription quality varies between models. Larger models generally produce better results


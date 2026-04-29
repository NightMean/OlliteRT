# OlliteRT (Android)

Android application for OlliteRT — an on-device LLM inference server that turns any Android phone into a selfhosted, OpenAI-compatible API endpoint.

See the [root README](../README.md) for project overview and user documentation.

---

## Quick Start

```bash
cd Android/src
./gradlew :app:assembleStableDebug
```

The APK is output to `app/build/outputs/apk/stable/debug/`.

For detailed build instructions (signing, flavors, versioning, CI), see **[docs/BUILDING.md](../docs/BUILDING.md)**.

---

## Architecture

The app is structured in four layers:

- **UI** — Jetpack Compose screens (Models, Status, Logs, Settings, Benchmark)
- **ViewModel** — Hilt-injected state management
- **Service** — Foreground service running an embedded Ktor HTTP server + LiteRT LM inference engine
- **Data** — SharedPreferences, Proto DataStore, Room database

For the full architecture diagram, package structure, threading model, and request flow, see **[docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md)**.

---

## Testing

```bash
./gradlew :app:testStableDebugUnitTest    # JVM unit tests
./gradlew :app:lintStableDebug            # Lint checks
```

Instrumented tests require a connected ARM64 device. Emulators need `-PDISABLE_ABI_SPLITS=true`.

---

## Documentation

| Document | Contents |
|----------|----------|
| [BUILDING.md](../docs/BUILDING.md) | Prerequisites, flavors, signing, versioning, lint |
| [ARCHITECTURE.md](../docs/ARCHITECTURE.md) | Layers, packages, threading, persistence, request flow |
| [MODELS.md](../docs/MODELS.md) | Supported models and allowlist format |
| [FAQ.md](../docs/FAQ.md) | Common questions and answers |
| [TROUBLESHOOTING.md](../docs/TROUBLESHOOTING.md) | Debugging common issues |
| [SECURITY.md](../docs/SECURITY.md) | Security considerations |
| [CLIENT_SETUP.md](../docs/CLIENT_SETUP.md) | Connecting clients (Home Assistant, Open WebUI, etc.) |
| [CHANGELOG.md](../docs/CHANGELOG.md) | Release history |

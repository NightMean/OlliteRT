# Privacy Policy

## Table of Contents

- [Summary](#summary)
- [Data Processing](#data-processing)
- [Network Connections](#network-connections)
- [Data Storage](#data-storage)
- [Permissions](#permissions)
- [Third-Party Services](#third-party-services)


---

## Summary

> [!NOTE]
> **OlliteRT is fully private by design.** No cloud processing, no telemetry, no analytics, no tracking, no accounts required. The app works fully offline after model download.

## Data Processing

OlliteRT runs large language models (LLMs) directly on your Android device using Google's LiteRT runtime. All processing — text generation, image analysis, audio processing — happens locally on your phone's GPU or CPU.

**Your prompts, conversations, and API requests never leave your device.** They are processed in-memory and are not stored permanently unless you have log persistence enabled (which stores logs only on-device in a local database).

## Network Connections

OlliteRT makes outbound network connections only for the following purposes:

| Connection | When | What's Sent | Can Be Disabled |
|:-----------|:-----|:------------|:----------------|
| **HuggingFace model download** | When you download a model | HTTP request to `huggingface.co` | Don't download models (import from local storage instead) |
| **HuggingFace OAuth** | When signing in to download gated models | Standard OAuth flow with HuggingFace | Optional — only needed for gated models |
| **GitHub update check** | Periodically (configurable) | HTTP request to GitHub Releases API | Disable in Settings → Auto-Launch & Behavior |
| **Model source refresh** | Periodically (~24 hours) and on pull-to-refresh | HTTPS request to each enabled model source URL to fetch the model list JSON | Remove the source in Settings → Model Sources |

Custom model sources are fetched from URLs you configure. The built-in Official source points to a JSON file hosted on GitHub. If you add a custom model source, OlliteRT will periodically fetch its URL — only the HTTP request itself is sent; no device data, usage metrics, or personal information is included.

The app **does not** make any other network connections. There are no analytics endpoints, crash reporters, or telemetry services.

---

### Local Network Server

When the server is running, OlliteRT listens for incoming HTTP connections on your local network (default port 8000). This is the core functionality — it's how clients like Home Assistant, Open WebUI, and other OpenAI API-compatible tools communicate with the on-device model.

- The server only accepts connections from your local network
- It is not accessible from the internet unless you explicitly configure port forwarding (not recommended)
- Optional bearer token authentication can be enabled to restrict access

## Data Storage

| Data | Storage Location | Encryption | User Control |
|:-----|:-----------------|:-----------|:-------------|
| Model files | App-private storage (`getExternalFilesDir`) | Android app sandbox | Delete from Models screen |
| Server settings | SharedPreferences | Android app sandbox | Reset in Settings |
| HuggingFace token | Proto DataStore | Android app sandbox | Add or remove in Settings |
| Request logs | Room database (on-device) | Android app sandbox | Auto-delete configurable, manual clear available |
| Bearer token | SharedPreferences | Android app sandbox | Change or disable in Settings |

All data is stored in Android's app-private directories, which are not accessible to other apps. Uninstalling OlliteRT removes all stored data.

## Permissions

| Permission | Why It's Needed |
|:-----------|:----------------|
| `FOREGROUND_SERVICE` | Keeps the HTTP server running when the app is in the background |
| `FOREGROUND_SERVICE_DATA_SYNC` | Foreground service type for the HTTP server (required by Android 14+) |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Foreground service type for serving network clients (required by Android 14+) |
| `INTERNET` | Model downloads, update checks, and model source refresh |
| `ACCESS_NETWORK_STATE` | Detect network availability before model downloads and source refresh |
| `ACCESS_WIFI_STATE` | Read the device's local IP address for the Status screen endpoint display |
| `CHANGE_NETWORK_STATE` | Request network connectivity for model downloads |
| `WAKE_LOCK` | Prevent the CPU from sleeping during model downloads (used by WorkManager) |
| `POST_NOTIFICATIONS` | Server status notification, model update alerts, and download progress |
| `RECEIVE_BOOT_COMPLETED` | Auto-start server on device boot (optional, user-enabled) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prompt the user to exempt OlliteRT from battery optimization so Android doesn't kill the server |

The app does **not** request: camera, microphone, location, contacts, phone, SMS, or storage permissions.

## Third-Party Services

| Service | Purpose | Privacy Policy |
|:--------|:--------|:---------------|
| HuggingFace | Model hosting and downloads | [huggingface.co/privacy](https://huggingface.co/privacy) |
| GitHub | Update checks, release downloads | [docs.github.com/privacy](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement) |

If you add custom model sources, OlliteRT will make network requests to whatever URLs you configure. Review the privacy practices of any third-party model source you add.

No other third-party services, SDKs, or libraries that collect user data are included in the app.


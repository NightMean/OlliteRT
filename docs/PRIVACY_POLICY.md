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

OlliteRT uses the following outbound connections.

| Connection | When | What's Sent | Can Be Disabled |
|:-----------|:-----|:------------|:----------------|
| **Hugging Face and ModelScope browsing** | When you search or open a repository in Models | Search text, sort and page information, or the repository ID and revision, as needed. If configured, the provider token is sent to the matching service. | Import models from local storage |
| **Model downloads** | When you check access or download a file | Repository, revision, file path and, for resumed downloads, a byte range. The provider can redirect the file request to its storage service. A configured token is sent when the request uses the matching provider. | Import models from local storage |
| **Provider token checks** | When you tap **Test** in Settings → Provider Tokens. OlliteRT also checks a saved Hugging Face token when an authenticated download access check returns HTTP 401. | The provider token, sent to that service's account endpoint | Skip the manual test. Remove the saved Hugging Face token to prevent automatic token checks. |
| **GitHub update check** | Periodically (configurable) | HTTP request to GitHub Releases API | Disable in Settings → App Updates |
| **Model source refresh** | Periodically (~24 hours) and on pull-to-refresh | HTTPS request to each enabled model source URL to fetch the model list JSON | Remove the source in Settings → Model Sources |

Custom model sources are fetched from URLs you configure. The built-in Official source points to a JSON file hosted on GitHub. If you add a custom model source, OlliteRT will periodically fetch its URL — only the HTTP request itself is sent; no device data, usage metrics, or personal information is included.

ModelScope International and China use separate tokens. ModelScope's catalog and download requests can send the matching token in an authorization header and a session cookie. The token check uses an authorization header. The app sends each ModelScope token only to its matching site.

OlliteRT has no analytics endpoints, crash reporters, or telemetry services.

---

### Local Network Server

When the server is running, OlliteRT listens for incoming HTTP connections on your local network (default port 8000). This is the core functionality — it's how clients like Home Assistant, Open WebUI, and other OpenAI API-compatible tools communicate with the on-device model.

- The server only accepts connections from your local network
- It is not accessible from the internet unless you explicitly configure port forwarding (not recommended)
- Optional bearer token authentication can be enabled to restrict access

## Data Storage

| Data | Storage Location | Protection | User Control |
|:-----|:-----------------|:-----------|:-------------|
| Model files | App-private storage (`getExternalFilesDir`) | Android app sandbox | Delete from Models screen |
| Server settings | SharedPreferences | Android app sandbox | Reset in Settings |
| Hugging Face token | SharedPreferences. A download request can also store a copy in the local WorkManager database. | Android app sandbox | Add or remove in Settings → Provider Tokens |
| ModelScope International and China tokens | Separate entries in SharedPreferences | Android app sandbox | Add or remove in Settings → Provider Tokens |
| Imported repository metadata | Local files in app-private storage | Android app sandbox | Remove the import from Models or from the provider's repository page |
| Request logs | Room database (on-device) | Android app sandbox | Auto-delete configurable, manual clear available |
| Bearer token | SharedPreferences | Android app sandbox | Change or disable in Settings |

All data is stored in Android's app-private directories, which are not accessible to other apps. Uninstalling OlliteRT removes all stored data.

## Permissions

| Permission | Why It's Needed |
|:-----------|:----------------|
| `FOREGROUND_SERVICE` | Keeps the HTTP server running when the app is in the background |
| `FOREGROUND_SERVICE_DATA_SYNC` | Foreground service type for the HTTP server (required by Android 14+) |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Foreground service type for serving network clients (required by Android 14+) |
| `INTERNET` | Provider browsing and token checks, model downloads, update checks, and model source refresh |
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
| Hugging Face | Repository browsing, token checks, and model downloads | [Hugging Face privacy policy](https://huggingface.co/privacy) |
| ModelScope | Repository browsing, token checks, and model downloads | [International privacy policy](https://modelscope.ai/protocol/Privacy-Policy), [China privacy policy](https://modelscope.cn/protocol/%E9%9A%90%E7%A7%81%E6%94%BF%E7%AD%96) |
| GitHub | Update checks, release downloads | [docs.github.com/privacy](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement) |

If you add custom model sources, OlliteRT will make network requests to whatever URLs you configure. Review the privacy practices of any third-party model source you add.

No other third-party services, SDKs, or libraries that collect user data are included in the app.


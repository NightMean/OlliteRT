# Security

## Table of Contents

- [Bearer Token Authentication](#bearer-token-authentication)
- [Network Exposure](#network-exposure)
- [Server Control Endpoints](#server-control-endpoints)
- [Model Sources](#model-sources)
- [Model Security](#model-security)

---

## Bearer Token Authentication

OlliteRT supports optional bearer token authentication to prevent unauthorized access to your server. This is a basic access control mechanism — the token is sent in plain text over HTTP. It prevents unauthorized access from other devices on your network but does not protect against traffic interception.

### Enabling Authentication

1. Go to **Settings → Server Configuration**
2. Toggle **Require Bearer Token** on — a random token is generated automatically
3. Save

---

### How It Works

When enabled, all API requests must include the token in the `Authorization` header:

```
Authorization: Bearer your-token-here
```

Requests without a valid token receive a `401 Unauthorized` response. See [Troubleshooting → 401 Unauthorized](TROUBLESHOOTING.md#401-unauthorized-error) if you're having auth issues.

**Endpoints exempt from auth:**
- `GET /` and `GET /v1` — server info (version, status, endpoints)
- `GET /health` — health check (returns only status, no sensitive data)
- `GET /metrics` — Prometheus metrics
- `GET /ping` — simple liveness check

**Endpoints requiring auth (when enabled):**
- All `/v1/*` inference endpoints (chat completions, completions, responses, audio transcriptions, models)
- All `/v1/server/*` control endpoints (stop, reload, thinking, config)

---

### Credential Storage

The bearer token is stored in SharedPreferences (read synchronously on every HTTP request for auth checking) and the HuggingFace token in Proto DataStore (accessed asynchronously during model downloads). Both are private to the app, not accessible by other apps. Neither is encrypted at rest — Android's app sandbox provides the isolation.

> [!IMPORTANT]
> Both the bearer token and HuggingFace token are automatically redacted when exporting logs.

## Network Exposure

### What's Exposed

OlliteRT binds to `0.0.0.0` on the configured port (default: `8000`), which means:

- **Accessible from any device on the same Wi-Fi network** — any computer, phone, or smart home hub on your LAN can reach the server
- **Not accessible from the internet** — unless you've configured port forwarding on your router

> [!CAUTION]
> **Never** expose OlliteRT directly to the internet. There is no HTTPS and bearer token auth is not designed for public-facing use. Use a VPN ([WireGuard](https://wireguard.com/), [Tailscale](https://tailscale.com/)) for remote access.

### Recommendations

| Risk | Mitigation |
|:-----|:-----------|
| Unauthorized LAN access | Enable bearer token authentication |
| Eavesdropping on LAN | Use a trusted home network; OlliteRT uses HTTP (not HTTPS) — traffic is unencrypted on the local network |
| Internet exposure | **Never** expose OlliteRT directly to the internet. If you need remote access, use a VPN ([WireGuard](https://wireguard.com/), [Tailscale](https://tailscale.com/)) to your home network |
| Port scanning | Change the default port from `8000` to a non-standard port in Settings |
| Shared/public Wi-Fi | Do not run OlliteRT on public or untrusted networks |

---

### No HTTPS

OlliteRT serves HTTP only — there is no TLS/SSL support. This is a deliberate choice:

- **Certificate management on Android is impractical** — no Let's Encrypt, no certificate renewal, no domain pointing to a phone's LAN IP
- **LAN traffic is typically trusted** — Home Assistant, Prometheus, and other local tools commonly communicate over HTTP on the LAN
- **Performance** — TLS adds overhead that is unnecessary for local-only communication

If you need encrypted communication, place OlliteRT behind a reverse proxy (e.g. Caddy, nginx) that terminates TLS, or use a VPN tunnel.

## Server Control Endpoints

The `/v1/server/*` endpoints allow remote control of the server:

| Endpoint | Action | Risk |
|:---------|:-------|:-----|
| `/v1/server/stop` | Stops the server | Denial of service |
| `/v1/server/reload` | Reloads the current model | Brief downtime during reload |
| `/v1/server/thinking` | Toggles thinking mode | Changes model behavior |
| `/v1/server/config` | Updates inference settings | Changes temperature, max tokens, etc. |

> [!IMPORTANT]
> Without bearer token authentication enabled, anyone on your network can access these endpoints — including stopping your server or changing its settings. Enable auth in Settings to restrict access.

## Model Sources

OlliteRT supports custom model sources — JSON-based model lists fetched from a URL you configure. The built-in Official source points to a JSON file hosted on GitHub.

### Security Considerations

- **Use HTTPS URLs** — model source data is fetched over whatever protocol the URL specifies. HTTP URLs transmit data in plain text and are vulnerable to man-in-the-middle attacks that could serve a tampered model list
- **Only add sources you trust** — a model source controls which models appear in your model list and where they are downloaded from
- **Fetching is periodic** — each enabled model source is fetched approximately every 24 hours to check for model updates. Only the HTTP request is sent; no device data, usage metrics, or personal information is included
- **Per-source isolation** — a failure or timeout fetching one model source does not affect others

## Model Security

- Models are downloaded from [HuggingFace](https://huggingface.co/litert-community) (for the built-in Official source) or from URLs specified by custom model sources
- Downloads use HTTPS and are pinned to a specific commit hash, but there is no post-download checksum verification
- Models are stored in the app's private storage directory — not accessible to other apps
- Imported models from local storage are copied to app-private storage
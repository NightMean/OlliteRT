# Prometheus Metrics

OlliteRT exposes a `GET /metrics` endpoint in [Prometheus exposition format](https://prometheus.io/docs/instrumenting/exposition_formats/) (`text/plain; version=0.0.4`). Always enabled, no authentication required.

## Table of Contents

- [Quick Setup](#quick-setup)
- [Metrics Reference](#metrics-reference)
- [Example Queries](#example-queries)
- [Notes](#notes)

---

## Quick Setup

### Prometheus

```yaml
# prometheus.yml
scrape_configs:
  - job_name: "ollitert"
    scrape_interval: 15s
    static_configs:
      - targets: ["PHONE_IP:8000"]
    metrics_path: "/metrics"
```

### Grafana

1. Add your Prometheus instance as a data source
2. Import a dashboard or create panels using the metrics below
3. Suggested panels: decode speed over time, TTFB histogram, request count, error rate, context utilization

## Metrics Reference

### Counters (10)

Cumulative values that increase monotonically. Reset when the server stops.

| Metric | Description |
|:-------|:------------|
| `ollitert_requests_total` | Total requests processed |
| `ollitert_prompt_tokens_total` | Total prompt tokens (estimated) |
| `ollitert_generation_tokens_total` | Total generated tokens (estimated) |
| `ollitert_prompt_seconds_total` | Cumulative prefill time (seconds) |
| `ollitert_generation_seconds_total` | Cumulative decode time (seconds) |
| `ollitert_errors_total` | Total request errors |
| `ollitert_errors_by_category_total{category="..."}` | Errors by category (`model_load`, `inference`, `network`, `system`) |
| `ollitert_request_text_total` | Text-only requests |
| `ollitert_request_image_total` | Image multimodal requests |
| `ollitert_request_audio_total` | Audio multimodal requests |

### Gauges (19)

Point-in-time values that can go up or down.

| Metric | Description |
|:-------|:------------|
| `ollitert_uptime_seconds` | Time since server entered RUNNING state |
| `ollitert_model_load_time_seconds` | Model load/warmup time |
| `ollitert_prompt_tokens_per_second` | Last request prefill throughput |
| `ollitert_generation_tokens_per_second` | Last request decode throughput |
| `ollitert_generation_tokens_per_second_peak` | Peak decode throughput since start |
| `ollitert_time_to_first_token_ms` | Last TTFB |
| `ollitert_time_to_first_token_avg_ms` | Average TTFB |
| `ollitert_inter_token_latency_ms` | Last inter-token latency |
| `ollitert_request_latency_ms` | Last request total latency |
| `ollitert_request_latency_avg_ms` | Average request latency |
| `ollitert_request_latency_peak_ms` | Peak request latency |
| `ollitert_context_utilization_percent` | Last request context window usage (%) |
| `ollitert_requests_processing` | Currently inferring (0 or 1) |
| `ollitert_model_idle_unloaded` | Model unloaded due to keep-alive idle timeout (0 or 1) |
| `ollitert_memory_native_heap_bytes` | Native heap allocated bytes (LiteRT model weights) |
| `ollitert_memory_app_heap_used_bytes` | JVM heap used bytes |
| `ollitert_memory_app_total_pss_bytes` | Total process PSS (JVM + native + mmap'd pages) |
| `ollitert_memory_device_available_bytes` | Device available RAM |
| `ollitert_memory_device_total_bytes` | Device total RAM |

## Example Queries

**Average decode speed over the last 5 minutes:**
```promql
avg_over_time(ollitert_generation_tokens_per_second[5m])
```

**Request error rate:**
```promql
rate(ollitert_errors_total[5m]) / rate(ollitert_requests_total[5m])
```

**Modality breakdown:**
```promql
ollitert_request_text_total
ollitert_request_image_total
ollitert_request_audio_total
```

**Native heap memory (model weights):**
```promql
ollitert_memory_native_heap_bytes
```

**Error breakdown by category:**
```promql
ollitert_errors_by_category_total
```

## Notes

> [!NOTE]
> - **No authentication** — matches the convention used by llama.cpp, vLLM, and TGI. Prometheus expects unauthenticated scrape targets.
> - **No histograms** — OlliteRT processes one request at a time, making histograms less useful than in multi-request servers.
> - **Token counts are estimated** — the LiteRT runtime doesn't expose a tokenizer API, so token counts use a character-based approximation (~4 characters per token).

For an explanation of what these metrics mean in practice, see [FAQ → What do the benchmark numbers mean?](../FAQ.md#what-do-the-benchmark-numbers-mean).

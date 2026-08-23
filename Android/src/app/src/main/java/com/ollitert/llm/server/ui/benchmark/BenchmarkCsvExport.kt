/*
 * Copyright 2026 Google LLC
 * Modifications Copyright 2025-2026 @NightMean (https://github.com/NightMean)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ollitert.llm.server.ui.benchmark

import com.ollitert.llm.server.proto.LlmBenchmarkResult
import com.ollitert.llm.server.proto.ValueSeries

internal fun getBenchmarkResultCsv(llmResult: LlmBenchmarkResult, aggregation: Aggregation): String {
  val basicInfo = llmResult.basicInfo
  val stats = llmResult.stats

  val header =
    listOf(
        "start time (ms)",
        "end time (ms)",
        "model name",
        "accelerator",
        "speculative decoding",
        "prefill tokens count",
        "decode tokens count",
        "runs count",
        "app version",
        "prefill speed (tokens/sec)",
        "decode speed (tokens/sec)",
        "time to first token (sec)",
        "first init time (ms)",
        "steady init time (ms)",
      )
      .joinToString(",")

  val data =
    listOf(
        basicInfo.startMs,
        basicInfo.endMs,
        basicInfo.modelName,
        basicInfo.accelerator,
        basicInfo.speculativeDecoding,
        basicInfo.prefillTokens,
        basicInfo.decodeTokens,
        basicInfo.numberOfRuns,
        basicInfo.appVersion,
        getAggregationValue(stats.prefillSpeed, aggregation),
        getAggregationValue(stats.decodeSpeed, aggregation),
        getAggregationValue(stats.timeToFirstToken, aggregation),
        stats.firstInitTimeMs,
        getAggregationValue(stats.nonFirstInitTimeMs, aggregation),
      )
      .joinToString(",")

  return "$header\n$data"
}

internal fun getAggregationValue(valueSeries: ValueSeries, aggregation: Aggregation): Double {
  return when (aggregation) {
    Aggregation.AVG -> valueSeries.avg
    Aggregation.MEDIAN -> valueSeries.medium
    // Aggregation.P25 -> valueSeries.pct25
    // Aggregation.P75 -> valueSeries.pct75
    Aggregation.MIN -> valueSeries.min
    Aggregation.MAX -> valueSeries.max
  }
}

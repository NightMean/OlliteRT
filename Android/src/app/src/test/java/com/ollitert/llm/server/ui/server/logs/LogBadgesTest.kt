/*
 * Copyright 2025-2026 @NightMean (https://github.com/NightMean)
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

package com.ollitert.llm.server.ui.server.logs

import java.time.ZoneId
import java.util.Locale
import java.util.stream.IntStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogBadgesTest {

  @Test
  fun formatTimestampUsesRequestedLocaleAndZone() {
    assertEquals(
      "00:00:00",
      formatTimestamp(0L, locale = Locale.US, zoneId = ZoneId.of("UTC")),
    )
  }

  @Test
  fun formatTimestampIsSafeAcrossParallelCalls() {
    val utc = ZoneId.of("UTC")
    assertTrue(
      IntStream.range(0, 3_600)
        .parallel()
        .allMatch { second ->
          val expected = "%02d:%02d:%02d".format(
            Locale.US,
            second / 3_600,
            (second / 60) % 60,
            second % 60,
          )
          formatTimestamp(second * 1_000L, locale = Locale.US, zoneId = utc) == expected
        }
    )
  }
}

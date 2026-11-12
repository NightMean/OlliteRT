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
package com.ollitert.llm.server.ui.modelmanager

import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HuggingFaceDateTest {
  @Test fun handlesInvalidAndOutOfRangeDates() {
    assertNull(formatHuggingFaceUpdatedDate(null))
    assertNull(formatHuggingFaceUpdatedDate("invalid"))
    assertNull(formatHuggingFaceUpdatedDate("+999999999-12-31T23:59:59Z"))
    assertEquals("Nov 10, 2026", formatHuggingFaceUpdatedDate("2026-11-10T03:56:06Z", Locale.US, TimeZone.getTimeZone("UTC")))
  }
}

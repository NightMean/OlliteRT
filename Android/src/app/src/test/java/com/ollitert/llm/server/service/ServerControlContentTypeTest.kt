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

package com.ollitert.llm.server.service

import com.ollitert.llm.server.service.http.isJsonContentType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerControlContentTypeTest {
  @Test
  fun acceptsJsonWithCaseAndParameters() {
    assertTrue(isJsonContentType(listOf("application/json")))
    assertTrue(isJsonContentType(listOf("Application/JSON; charset=utf-8")))
    assertTrue(isJsonContentType(listOf("application/json; note=\"one,two\"")))
  }

  @Test
  fun rejectsMissingAmbiguousAndNonJsonContentTypes() {
    for (headers in listOf(
      emptyList(), listOf(""), listOf("text/plain"), listOf("application/x-www-form-urlencoded"),
      listOf("multipart/form-data; boundary=x"), listOf("application/json, text/plain"),
      listOf("text/plain, application/json"),
      listOf("application/json", "text/plain"), listOf("application"),
    )) {
      assertFalse("Expected rejection for $headers", isJsonContentType(headers))
    }
  }
}

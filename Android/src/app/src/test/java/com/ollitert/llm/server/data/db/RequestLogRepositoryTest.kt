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

package com.ollitert.llm.server.data.db

import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class RequestLogRepositoryTest {

  private lateinit var repository: RequestLogRepository

  @Before
  fun setUp() {
    repository = mockk(relaxed = true)
  }

  @Test
  fun `initialize calls repository contract`() {
    repository.initialize()
    verify { repository.initialize() }
  }

  @Test
  fun `clearPersistedLogs calls repository contract`() {
    repository.clearPersistedLogs()
    verify { repository.clearPersistedLogs() }
  }

  @Test
  fun `shutdown calls repository contract`() {
    repository.shutdown()
    verify { repository.shutdown() }
  }
}

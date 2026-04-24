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

package com.ollitert.llm.server

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ollitert.llm.server.data.ModelAllowlistJson
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test that verifies allowlist deserialization works after R8 optimization.
 *
 * kotlinx-serialization uses a compiler plugin (no reflection), so R8 is less likely
 * to break it than Gson — but this test catches any keep-rule gaps in release builds:
 *
 *   ./gradlew connectedStableReleaseAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class R8AllowlistSmokeTest {

  @Test
  fun bundledAllowlistDeserializesSuccessfully() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val json = context.assets.open("model_allowlist.json").bufferedReader().use { it.readText() }

    val allowlist = ModelAllowlistJson.decode(json)

    assertNotNull("Allowlist should not be null", allowlist)
    assertTrue("Allowlist should contain at least one model", allowlist.models.isNotEmpty())

    val first = allowlist.models.first()
    assertNotNull("First model name should not be null", first.name)
    assertNotNull("First model defaultConfig should not be null", first.defaultConfig)
  }
}

package com.ollitert.llm.server

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.ollitert.llm.server.data.ModelAllowlist
import com.ollitert.llm.server.data.ModelAllowlistJson
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test that verifies Gson reflection works after R8 optimization.
 *
 * **Why this exists:** JVM unit tests run against unminified code and always pass.
 * R8 (release builds) strips constructors and merges classes it considers unused,
 * which breaks Gson's reflective instantiation. This test catches that — but ONLY
 * when run against a release build variant:
 *
 *   ./gradlew connectedProdReleaseAndroidTest
 *
 * If this test fails on release but passes on debug, the fix is in proguard-rules.pro:
 * add `-keep` rules for the affected data classes.
 */
@RunWith(AndroidJUnit4::class)
class R8GsonSmokeTest {

  @Test
  fun bundledAllowlistDeserializesSuccessfully() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val json = context.assets.open("model_allowlist.json").bufferedReader().use { it.readText() }

    // This is the exact same code path as ModelManagerViewModel.readModelAllowlistFromAssets().
    // If R8 strips constructors from AllowedModel, DefaultConfig, or any nested class,
    // Gson.fromJson() will throw: "Abstract classes can't be instantiated!"
    val allowlist: ModelAllowlist = Gson().fromJson(json, ModelAllowlist::class.java)

    assertNotNull("Allowlist should not be null", allowlist)
    assertTrue("Allowlist should contain at least one model", allowlist.models.isNotEmpty())

    // Verify a model can be fully accessed (catches partial deserialization)
    val first = allowlist.models.first()
    assertNotNull("First model name should not be null", first.name)
    assertNotNull("First model defaultConfig should not be null", first.defaultConfig)
  }

  @Test
  fun modelAllowlistJsonDecodeWorks() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val json = context.assets.open("model_allowlist.json").bufferedReader().use { it.readText() }

    // Test the convenience wrapper used by LlmHttpAllowlistLoader
    val allowlist = ModelAllowlistJson.decode(json)

    assertNotNull(allowlist)
    assertTrue(allowlist.models.isNotEmpty())
  }
}

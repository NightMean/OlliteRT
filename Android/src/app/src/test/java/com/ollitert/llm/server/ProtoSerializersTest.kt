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

import androidx.datastore.core.CorruptionException
import com.ollitert.llm.server.data.BenchmarkResultsSerializer
import com.ollitert.llm.server.data.SettingsSerializer
import com.ollitert.llm.server.data.UserDataSerializer
import com.ollitert.llm.server.proto.BenchmarkResults
import com.ollitert.llm.server.proto.Settings
import com.ollitert.llm.server.proto.UserData
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProtoSerializersTest {

  @Test
  fun settingsSerializerExposesDefaultInstance() {
    assertSame(Settings.getDefaultInstance(), SettingsSerializer.defaultValue)
  }

  @Test
  fun settingsSerializerRoundTripsProto() = runBlocking {
    val original =
      Settings.newBuilder()
        .setHasSeenBenchmarkComparisonHelp(true)
        .addTextInputHistory("hello")
        .build()

    val output = ByteArrayOutputStream()
    SettingsSerializer.writeTo(original, output)

    val decoded = SettingsSerializer.readFrom(ByteArrayInputStream(output.toByteArray()))
    assertEquals(original, decoded)
  }

  @Test
  fun settingsSerializerRejectsInvalidBytes() = runBlocking {
    try {
      SettingsSerializer.readFrom(ByteArrayInputStream("not-a-proto".toByteArray()))
      fail("Expected invalid proto bytes to fail")
    } catch (e: Exception) {
      assertTrue(e is CorruptionException)
    }
  }

  @Test
  fun benchmarkResultsSerializerRoundTripsProto() = runBlocking {
    val original = BenchmarkResults.newBuilder().build()

    val output = ByteArrayOutputStream()
    BenchmarkResultsSerializer.writeTo(original, output)

    val decoded = BenchmarkResultsSerializer.readFrom(ByteArrayInputStream(output.toByteArray()))
    assertEquals(original, decoded)
  }

  @Test
  fun benchmarkResultsSerializerRejectsInvalidBytes() = runBlocking {
    try {
      BenchmarkResultsSerializer.readFrom(ByteArrayInputStream(byteArrayOf(1, 2, 3)))
      fail("Expected invalid proto bytes to fail")
    } catch (e: Exception) {
      assertTrue(e is CorruptionException)
    }
  }

  @Test
  fun userDataSerializerExposesDefaultInstance() {
    assertSame(UserData.getDefaultInstance(), UserDataSerializer.defaultValue)
  }

  @Test
  fun userDataSerializerRoundTripsProto() = runBlocking {
    val original = UserData.newBuilder()
      .putSecrets("hf_token", "hf_test_value_123")
      .build()

    val output = ByteArrayOutputStream()
    UserDataSerializer.writeTo(original, output)

    val decoded = UserDataSerializer.readFrom(ByteArrayInputStream(output.toByteArray()))
    assertEquals(original, decoded)
    assertEquals("hf_test_value_123", decoded.secretsMap["hf_token"])
  }

  @Test
  fun userDataSerializerRejectsInvalidBytes() = runBlocking {
    try {
      UserDataSerializer.readFrom(ByteArrayInputStream("not-a-proto".toByteArray()))
      fail("Expected invalid proto bytes to fail")
    } catch (e: Exception) {
      assertTrue(e is CorruptionException)
    }
  }

}

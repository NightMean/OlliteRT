package com.ollitert.llm.server

import androidx.datastore.core.CorruptionException
import com.ollitert.llm.server.data.BenchmarkResultsSerializer
import com.ollitert.llm.server.data.SettingsSerializer
import com.ollitert.llm.server.proto.BenchmarkResults
import com.ollitert.llm.server.proto.Settings
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

}

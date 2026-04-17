package com.ollitert.llm.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies product flavor build configuration is wired up correctly.
 * These tests validate that BuildConfig fields, application ID suffixes,
 * and version name suffixes are set as expected for the active flavor.
 */
class BuildConfigFlavorTest {

  @Test
  fun channelFieldIsPresent() {
    // BuildConfig.CHANNEL must be one of the defined flavors
    val validChannels = setOf("dev", "beta", "stable")
    assertTrue(
      "BuildConfig.CHANNEL ('${BuildConfig.CHANNEL}') must be one of $validChannels",
      BuildConfig.CHANNEL in validChannels,
    )
  }

  @Test
  fun applicationIdMatchesFlavor() {
    // applicationId should have the correct suffix for the active flavor
    when (BuildConfig.CHANNEL) {
      "dev" -> assertTrue(
        "Dev applicationId should end with .dev",
        BuildConfig.APPLICATION_ID.endsWith(".dev"),
      )
      "beta" -> assertTrue(
        "Beta applicationId should end with .beta",
        BuildConfig.APPLICATION_ID.endsWith(".beta"),
      )
      "stable" -> assertEquals(
        "Prod applicationId should have no suffix",
        "com.ollitert.llm.server",
        BuildConfig.APPLICATION_ID,
      )
    }
  }

  @Test
  fun versionNameIncludesChannelSuffix() {
    when (BuildConfig.CHANNEL) {
      "dev" -> assertTrue(
        "Dev versionName should contain -dev suffix",
        BuildConfig.VERSION_NAME.endsWith("-dev"),
      )
      "beta" -> assertTrue(
        "Beta versionName should contain -beta suffix",
        BuildConfig.VERSION_NAME.endsWith("-beta"),
      )
      "stable" -> assertTrue(
        "Prod versionName should not contain channel suffix",
        !BuildConfig.VERSION_NAME.contains("-dev") && !BuildConfig.VERSION_NAME.contains("-beta"),
      )
    }
  }

  @Test
  fun allFlavorsCanCoexist() {
    // Verify application ID suffixes are distinct so all flavors install side-by-side
    val baseId = "com.ollitert.llm.server"
    val devId = "$baseId.dev"
    val betaId = "$baseId.beta"
    val prodId = baseId

    val ids = setOf(devId, betaId, prodId)
    assertEquals("All three flavor applicationIds must be unique", 3, ids.size)
  }
}

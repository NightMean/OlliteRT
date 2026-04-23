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

package com.ollitert.llm.server.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryTest {

  @Test
  fun toProtoSetsAllFields() {
    val repo = Repository(
      id = "test-id",
      url = "https://example.com/models.json",
      enabled = true,
      isBuiltIn = false,
      contentVersion = 5,
      lastRefreshMs = 1000L,
      lastError = "timeout",
      name = "Test Repo",
      description = "A test",
      iconUrl = "https://example.com/icon.png",
      modelCount = 3,
    )
    val proto = repo.toProto()
    assertEquals("test-id", proto.id)
    assertEquals("https://example.com/models.json", proto.url)
    assertTrue(proto.enabled)
    assertFalse(proto.isBuiltIn)
    assertEquals(5, proto.contentVersion)
    assertEquals(1000L, proto.lastRefreshMs)
    assertEquals("timeout", proto.lastError)
  }

  @Test
  fun fromProtoSetsAllFieldsWithDefaults() {
    val proto = com.ollitert.llm.server.proto.RepositoryEntry.newBuilder()
      .setId("abc")
      .setUrl("https://example.com/list.json")
      .setEnabled(true)
      .setIsBuiltIn(true)
      .setContentVersion(2)
      .setLastRefreshMs(500L)
      .setLastError("")
      .build()
    val repo = Repository.fromProto(proto)
    assertEquals("abc", repo.id)
    assertEquals("https://example.com/list.json", repo.url)
    assertTrue(repo.enabled)
    assertTrue(repo.isBuiltIn)
    assertEquals(2, repo.contentVersion)
    assertEquals(500L, repo.lastRefreshMs)
    assertEquals("", repo.lastError)
    // In-memory fields get defaults
    assertEquals("", repo.name)
    assertEquals("", repo.description)
    assertEquals("", repo.iconUrl)
    assertEquals(null, repo.modelCount)
  }

  @Test
  fun roundTripPreservesAllPersistedFields() {
    val original = Repository(
      id = "round-trip",
      url = "https://test.com/models.json",
      enabled = false,
      isBuiltIn = true,
      contentVersion = 10,
      lastRefreshMs = 9999L,
      lastError = "404",
      name = "ignored in proto",
      description = "also ignored",
      iconUrl = "also ignored",
      modelCount = 42,
    )
    val restored = Repository.fromProto(original.toProto())
    assertEquals(original.id, restored.id)
    assertEquals(original.url, restored.url)
    assertEquals(original.enabled, restored.enabled)
    assertEquals(original.isBuiltIn, restored.isBuiltIn)
    assertEquals(original.contentVersion, restored.contentVersion)
    assertEquals(original.lastRefreshMs, restored.lastRefreshMs)
    assertEquals(original.lastError, restored.lastError)
  }

  @Test
  fun cacheFilenameUsesIdForUserAdded() {
    val repo = Repository(
      id = "abc-123",
      url = "",
      enabled = true,
      isBuiltIn = false,
      contentVersion = 0,
      lastRefreshMs = 0,
      lastError = "",
      name = "",
      description = "",
      iconUrl = "",
      modelCount = null,
    )
    assertEquals("model_allowlist_abc-123.json", repo.cacheFilename)
  }

  @Test
  fun cacheFilenameUsesOfficialForBuiltIn() {
    val repo = Repository(
      id = "official",
      url = "",
      enabled = true,
      isBuiltIn = true,
      contentVersion = 0,
      lastRefreshMs = 0,
      lastError = "",
      name = "",
      description = "",
      iconUrl = "",
      modelCount = null,
    )
    assertEquals("model_allowlist_official.json", repo.cacheFilename)
  }
}

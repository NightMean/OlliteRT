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

import com.ollitert.llm.server.data.allowlist.DiscoveredModel
import com.ollitert.llm.server.data.allowlist.DiscoveredProvider
import com.ollitert.llm.server.data.allowlist.ModelScopeSite
import com.ollitert.llm.server.data.allowlist.modelScopeFileUrl
import org.junit.Assert.*
import org.junit.Test

class RepositoryImportsTest {
  @Test fun groupsVariantsByRepositoryAndSearchesWithoutCase() {
    fun item(repo: String, path: String) = RepositoryImport(DiscoveredModel(repo, path, "a".repeat(40), 1), null, null)
    val entries = listOf(item("one/Alpha", "a.litertlm"), item("one/Alpha", "b.litertlm"), item("two/Beta", "a.litertlm"))
    val result = matchingImportedRepositories(entries, " ALPHA ")
    assertEquals(setOf("one/Alpha"), result.keys)
    assertEquals(entries.take(2), result.getValue("one/Alpha"))
  }

  @Test fun paginationAccountsForImportedCardsAndHeaders() {
    assertTrue(shouldLoadRepositoryPage(-1, importedCount = 3, availableCount = 0))
    assertFalse(shouldLoadRepositoryPage(4, importedCount = 3, availableCount = 5))
    assertFalse(shouldLoadRepositoryPage(5, importedCount = 3, availableCount = 5))
    assertTrue(shouldLoadRepositoryPage(7, importedCount = 3, availableCount = 5))
    assertFalse(shouldLoadRepositoryPage(10, importedCount = 3, availableCount = 5))
    assertTrue(shouldLoadRepositoryPage(1, importedCount = 0, availableCount = 1))
  }

  @Test fun explicitSelectionDoesNotChangeWhenImportsLoadOrDisappear() {
    val imported = RepositoryImport(DiscoveredModel("a/b", "first.litertlm", "a".repeat(40), 1), null, null)
    val paths = listOf("first.litertlm", "second.litertlm")
    assertEquals("first.litertlm", selectRepositoryPath("first.litertlm", paths, listOf(imported)))
    assertEquals("first.litertlm", selectRepositoryPath("first.litertlm", paths.drop(1), emptyList()))
    assertEquals("second.litertlm", selectRepositoryPath(null, paths, listOf(imported)))
    assertNull(selectRepositoryPath(null, paths.take(1), listOf(imported)))
  }

  @Test fun importedLinksUseTheirOwnSiteAndPinnedRevision() {
    val base = DiscoveredModel("a/b", "model.litertlm", "a".repeat(40), 1, provider = DiscoveredProvider.MODELSCOPE)
    val china = base.copy(modelScopeSite = ModelScopeSite.CHINA)
    val international = base.copy(modelScopeSite = ModelScopeSite.INTERNATIONAL, revision = "b".repeat(40))
    val entries = listOf(RepositoryImport(china, null, null), RepositoryImport(international, null, null))
    assertEquals(modelScopeFileUrl(international.repository, international.revision, international.path, international.modelScopeSite),
      importedFileUrl(entries, international.repository, international.path, ModelScopeSite.INTERNATIONAL))
  }
}

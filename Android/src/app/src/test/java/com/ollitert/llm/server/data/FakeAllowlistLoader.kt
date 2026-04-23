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

import com.ollitert.llm.server.ui.modelmanager.AllowlistLoader

class FakeAllowlistLoader : AllowlistLoader {

  private val diskCache = mutableMapOf<String, ModelAllowlist>()
  var bundledAllowlist: ModelAllowlist? = null

  override fun readTestAllowlist(): ModelAllowlist? = null

  override fun saveToDisk(content: String, filename: String) {
    diskCache[filename] = ModelAllowlistJson.decode(content)
  }

  fun putDiskCache(filename: String, allowlist: ModelAllowlist) {
    diskCache[filename] = allowlist
  }

  override fun readFromDiskCache(filename: String): ModelAllowlist? = diskCache[filename]

  override fun readFromAssets(): ModelAllowlist? = bundledAllowlist
}

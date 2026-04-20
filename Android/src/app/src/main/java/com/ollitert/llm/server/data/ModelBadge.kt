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

sealed class ModelBadge(val key: String) {
  data object BestOverall : ModelBadge("best_overall")
  data object New : ModelBadge("new")
  data object Fastest : ModelBadge("fastest")

  class Other(key: String) : ModelBadge(key) {
    val displayLabel: String = key
      .split("_")
      .filter { it.isNotEmpty() }
      .joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercaseChar() }
      }

    override fun equals(other: Any?): Boolean = other is Other && other.key == key
    override fun hashCode(): Int = key.hashCode()
  }

  companion object {
    fun fromKey(key: String): ModelBadge? = when {
      key.isBlank() -> null
      key == "best_overall" -> BestOverall
      key == "new" -> New
      key == "fastest" -> Fastest
      else -> Other(key)
    }
  }
}

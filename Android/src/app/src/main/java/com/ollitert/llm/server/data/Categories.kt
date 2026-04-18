/*
 * Copyright 2025 Google LLC
 * Modifications Copyright 2025-2026 @NightMean (https://github.com/NightMean)
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

import androidx.annotation.StringRes
import com.ollitert.llm.server.R

/**
 * Stores basic info about a Category
 *
 * A category is a tab on the home page which contains a list of tasks. Category is set through
 * Task.
 */
data class CategoryInfo(
  // The id of the category.
  val id: String,

  // The string resource id of the label of the resource, for display purpose.
  @param:StringRes val labelStringRes: Int? = null,

  // The string label. It takes precedence over labelStringRes above.
  val label: String? = null,
)

/** Pre-defined categories. */
object Category {
  val LLM = CategoryInfo(id = "llm", labelStringRes = R.string.category_llm)
}

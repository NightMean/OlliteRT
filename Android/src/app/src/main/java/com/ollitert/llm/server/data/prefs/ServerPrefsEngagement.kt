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
package com.ollitert.llm.server.data.prefs

import android.content.Context
import androidx.core.content.edit

// -- Keys: engagement prompt ------------------------------------------------
private const val KEY_MANUAL_START_COUNT = "manual_start_count"
private const val KEY_ENGAGEMENT_PROMPT_PERMANENTLY_DISMISSED = "engagement_prompt_permanently_dismissed"
private const val KEY_ENGAGEMENT_PROMPT_SHOW_COUNT = "engagement_prompt_show_count"
/** Maximum number of times the engagement prompt is shown before being auto-suppressed. */
private const val ENGAGEMENT_PROMPT_MAX_SHOWS = 2
/** Manual start count threshold for showing the engagement prompt the first time. */
private const val ENGAGEMENT_PROMPT_FIRST_THRESHOLD = 3
/** Manual start count threshold for showing the engagement prompt the second time. */
private const val ENGAGEMENT_PROMPT_SECOND_THRESHOLD = 13

// -- Pref declarations -------------------------------------------------------

internal val MANUAL_START_COUNT = IntPref(KEY_MANUAL_START_COUNT, 0)
internal val ENGAGEMENT_PROMPT_SHOW_COUNT = IntPref(KEY_ENGAGEMENT_PROMPT_SHOW_COUNT, 0)

  // ══════════════════════════════════════════════════════════════════════════
  // § Engagement Prompt
  // ══════════════════════════════════════════════════════════════════════════

  /** Number of times the user has manually pressed "Start Server" (excludes auto-start on boot). */
fun ServerPrefs.getManualStartCount(context: Context): Int = get(context, MANUAL_START_COUNT)

fun ServerPrefs.incrementManualStartCount(context: Context): Int {
    val newCount = get(context, MANUAL_START_COUNT) + 1
    set(context, MANUAL_START_COUNT, newCount)
    return newCount
  }

  /** True if the user checked "Don't show this again" or tapped a positive action (Support/Star). */
fun ServerPrefs.isEngagementPromptPermanentlyDismissed(context: Context): Boolean =
    prefs(context).getBoolean(KEY_ENGAGEMENT_PROMPT_PERMANENTLY_DISMISSED, false)

fun ServerPrefs.setEngagementPromptPermanentlyDismissed(context: Context) {
    prefs(context).edit { putBoolean(KEY_ENGAGEMENT_PROMPT_PERMANENTLY_DISMISSED, true) }
  }

  /** How many times the engagement prompt has been shown (max 2 lifetime). */
fun ServerPrefs.getEngagementPromptShowCount(context: Context): Int = get(context, ENGAGEMENT_PROMPT_SHOW_COUNT)

fun ServerPrefs.incrementEngagementPromptShowCount(context: Context): Int {
    val newCount = get(context, ENGAGEMENT_PROMPT_SHOW_COUNT) + 1
    set(context, ENGAGEMENT_PROMPT_SHOW_COUNT, newCount)
    return newCount
  }

  /**
   * Whether the engagement prompt should be shown right now.
   * Criteria: not permanently dismissed, shown fewer than 2 times, and manual start count
   * hits a threshold (3 for first show, 13 for second show — i.e. 10 additional starts).
   */
fun ServerPrefs.shouldShowEngagementPrompt(context: Context): Boolean {
    if (isEngagementPromptPermanentlyDismissed(context)) return false
    val showCount = getEngagementPromptShowCount(context)
    if (showCount >= ENGAGEMENT_PROMPT_MAX_SHOWS) return false
    val startCount = getManualStartCount(context)
    return when (showCount) {
      0 -> startCount >= ENGAGEMENT_PROMPT_FIRST_THRESHOLD
      1 -> startCount >= ENGAGEMENT_PROMPT_SECOND_THRESHOLD
      else -> false
    }
  }

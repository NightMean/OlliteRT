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
import com.ollitert.llm.server.BuildConfig
import androidx.core.content.edit

// -- Keys: update check & cached release state ------------------------------
private const val KEY_UPDATE_CHECK_ENABLED = "update_check_enabled"
private const val KEY_UPDATE_CHECK_INTERVAL_HOURS = "update_check_interval_hours"
private const val KEY_LAST_DISMISSED_UPDATE_VERSION = "last_dismissed_update_version"
private const val KEY_CACHED_LATEST_VERSION = "cached_latest_version"
private const val KEY_CACHED_RELEASE_HTML_URL = "cached_release_html_url"
private const val KEY_CACHED_RELEASE_ETAG = "cached_release_etag"
private const val KEY_UPDATE_CHECK_CONSECUTIVE_FAILURES = "update_check_consecutive_failures"
private const val KEY_CROSS_CHANNEL_NOTIFY_ENABLED = "cross_channel_notify_enabled"
private const val KEY_LAST_DISMISSED_CROSS_CHANNEL_VERSION = "last_dismissed_cross_channel_version"
private const val KEY_CACHED_CROSS_CHANNEL_VERSION = "cached_cross_channel_version"
private const val DEFAULT_UPDATE_CHECK_ENABLED = true
private const val DEFAULT_UPDATE_CHECK_INTERVAL_HOURS = 24

// -- Pref declarations -------------------------------------------------------

internal val UPDATE_CHECK_ENABLED = BoolPref(KEY_UPDATE_CHECK_ENABLED, DEFAULT_UPDATE_CHECK_ENABLED)
internal val UPDATE_CHECK_INTERVAL_HOURS = IntPref(KEY_UPDATE_CHECK_INTERVAL_HOURS, DEFAULT_UPDATE_CHECK_INTERVAL_HOURS)
internal val UPDATE_CHECK_CONSECUTIVE_FAILURES = IntPref(KEY_UPDATE_CHECK_CONSECUTIVE_FAILURES, 0)
internal val CROSS_CHANNEL_NOTIFY_ENABLED = BoolPref(KEY_CROSS_CHANNEL_NOTIFY_ENABLED, BuildConfig.UPDATE_CHANNEL != "stable")

  // ══════════════════════════════════════════════════════════════════════════
  // § Update Check
  // ══════════════════════════════════════════════════════════════════════════

fun ServerPrefs.isUpdateCheckEnabled(context: Context): Boolean = get(context, UPDATE_CHECK_ENABLED)
fun ServerPrefs.setUpdateCheckEnabled(context: Context, enabled: Boolean) = set(context, UPDATE_CHECK_ENABLED, enabled)

fun ServerPrefs.getUpdateCheckIntervalHours(context: Context): Int = get(context, UPDATE_CHECK_INTERVAL_HOURS)
fun ServerPrefs.setUpdateCheckIntervalHours(context: Context, hours: Int) = set(context, UPDATE_CHECK_INTERVAL_HOURS, hours.coerceIn(1, 720))

fun ServerPrefs.getLastDismissedUpdateVersion(context: Context): String? =
    prefs(context).getString(KEY_LAST_DISMISSED_UPDATE_VERSION, null)

fun ServerPrefs.setLastDismissedUpdateVersion(context: Context, version: String?) {
    prefs(context).edit {
      if (version != null) putString(KEY_LAST_DISMISSED_UPDATE_VERSION, version)
      else remove(KEY_LAST_DISMISSED_UPDATE_VERSION)
    }
  }

fun ServerPrefs.getCachedLatestVersion(context: Context): String? =
    prefs(context).getString(KEY_CACHED_LATEST_VERSION, null)

fun ServerPrefs.getCachedReleaseHtmlUrl(context: Context): String? =
    prefs(context).getString(KEY_CACHED_RELEASE_HTML_URL, null)

fun ServerPrefs.getCachedReleaseETag(context: Context): String? =
    prefs(context).getString(KEY_CACHED_RELEASE_ETAG, null)

fun ServerPrefs.setCachedUpdateInfo(context: Context, version: String?, htmlUrl: String?, etag: String?) {
    prefs(context).edit {
      if (version != null) putString(KEY_CACHED_LATEST_VERSION, version) else remove(KEY_CACHED_LATEST_VERSION)
      if (htmlUrl != null) putString(KEY_CACHED_RELEASE_HTML_URL, htmlUrl) else remove(KEY_CACHED_RELEASE_HTML_URL)
      if (etag != null) putString(KEY_CACHED_RELEASE_ETAG, etag) else remove(KEY_CACHED_RELEASE_ETAG)
    }
  }

fun ServerPrefs.getUpdateCheckConsecutiveFailures(context: Context): Int = get(context, UPDATE_CHECK_CONSECUTIVE_FAILURES)
fun ServerPrefs.setUpdateCheckConsecutiveFailures(context: Context, count: Int) = set(context, UPDATE_CHECK_CONSECUTIVE_FAILURES, count)

  /** Clear all cached update state (version, URL, ETag, dismiss). Called after a successful app update. */
fun ServerPrefs.clearUpdateState(context: Context) {
    prefs(context).edit {
      remove(KEY_CACHED_LATEST_VERSION)
      remove(KEY_CACHED_RELEASE_HTML_URL)
      remove(KEY_CACHED_RELEASE_ETAG)
      remove(KEY_LAST_DISMISSED_UPDATE_VERSION)
      remove(KEY_UPDATE_CHECK_CONSECUTIVE_FAILURES)
      remove(KEY_LAST_DISMISSED_CROSS_CHANNEL_VERSION)
      remove(KEY_CACHED_CROSS_CHANNEL_VERSION)
    }
  }

fun ServerPrefs.isCrossChannelNotifyEnabled(context: Context): Boolean = get(context, CROSS_CHANNEL_NOTIFY_ENABLED)
fun ServerPrefs.setCrossChannelNotifyEnabled(context: Context, enabled: Boolean) = set(context, CROSS_CHANNEL_NOTIFY_ENABLED, enabled)

fun ServerPrefs.getLastDismissedCrossChannelVersion(context: Context): String? =
    prefs(context).getString(KEY_LAST_DISMISSED_CROSS_CHANNEL_VERSION, null)

fun ServerPrefs.setLastDismissedCrossChannelVersion(context: Context, version: String?) {
    prefs(context).edit {
      if (version != null) putString(KEY_LAST_DISMISSED_CROSS_CHANNEL_VERSION, version)
      else remove(KEY_LAST_DISMISSED_CROSS_CHANNEL_VERSION)
    }
  }

fun ServerPrefs.getCachedCrossChannelVersion(context: Context): String? =
    prefs(context).getString(KEY_CACHED_CROSS_CHANNEL_VERSION, null)

fun ServerPrefs.setCachedCrossChannelVersion(context: Context, version: String?) {
    prefs(context).edit {
      if (version != null) putString(KEY_CACHED_CROSS_CHANNEL_VERSION, version)
      else remove(KEY_CACHED_CROSS_CHANNEL_VERSION)
    }
  }

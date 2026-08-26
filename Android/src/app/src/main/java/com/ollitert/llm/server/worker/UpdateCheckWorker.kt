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

package com.ollitert.llm.server.worker

import com.ollitert.llm.server.common.ServerMetrics
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.hilt.work.HiltWorker
import com.ollitert.llm.server.BuildConfig
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.GitHubConfig
import com.ollitert.llm.server.common.SemVer
import com.ollitert.llm.server.data.model.EventCategory
import com.ollitert.llm.server.data.model.LogLevel
import com.ollitert.llm.server.data.prefs.ServerPrefs
import com.ollitert.llm.server.data.repository.RequestLogStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import com.ollitert.llm.server.data.prefs.getCachedCrossChannelVersion

import com.ollitert.llm.server.data.prefs.getCachedLatestVersion

import com.ollitert.llm.server.data.prefs.getCachedReleaseETag

import com.ollitert.llm.server.data.prefs.getCachedReleaseHtmlUrl

import com.ollitert.llm.server.data.prefs.getLastDismissedCrossChannelVersion

import com.ollitert.llm.server.data.prefs.getLastDismissedUpdateVersion

import com.ollitert.llm.server.data.prefs.getUpdateCheckConsecutiveFailures

import com.ollitert.llm.server.data.prefs.getUpdateCheckIntervalHours

import com.ollitert.llm.server.data.prefs.isCrossChannelNotifyEnabled

import com.ollitert.llm.server.data.prefs.isUpdateCheckEnabled

import com.ollitert.llm.server.data.prefs.isVerboseDebugEnabled

import com.ollitert.llm.server.data.prefs.setCachedCrossChannelVersion

import com.ollitert.llm.server.data.prefs.setCachedUpdateInfo

import com.ollitert.llm.server.data.prefs.setUpdateCheckConsecutiveFailures

import com.ollitert.llm.server.data.prefs.setUpdateCheckEnabled
/**
 * Background WorkManager worker that checks GitHub Releases for newer versions.
 * Runs periodically (default: every 24 hours) with network + battery constraints.
 */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
  @Assisted appContext: Context,
  @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

  private var cachedReleasesJson: String? = null

  override suspend fun doWork(): Result {
    val context = applicationContext

    val isManualCheck = inputData.getBoolean(KEY_MANUAL_CHECK, false)
    if (!isManualCheck && !ServerPrefs.isUpdateCheckEnabled(context)) {
      return Result.success(workDataOf(KEY_RESULT to RESULT_DISABLED))
    }

    val verbose = ServerPrefs.isVerboseDebugEnabled(context)
    if (verbose) {
      val endpoint = if (BuildConfig.UPDATE_CHANNEL == "stable") "/releases/latest" else "/releases?per_page=10"
      RequestLogStore.addEvent(
        "Update check started",
        level = LogLevel.DEBUG,
        category = EventCategory.UPDATE,
        body = "Channel: ${BuildConfig.UPDATE_CHANNEL}, Endpoint: $endpoint",
      )
    }

    val crossChannelEnabled = ServerPrefs.isCrossChannelNotifyEnabled(context)
    val checkedChannels = if (crossChannelEnabled) "stable/beta/dev" else BuildConfig.UPDATE_CHANNEL

    try {
      // fetchGitHub uses blocking HttpURLConnection (up to 15s on timeouts);
      // move the whole fetch-and-decide section off the Default dispatcher so
      // it can't starve CPU-bound coroutines sharing the pool.
      val release = withContext(Dispatchers.IO) { fetchLatestRelease(context) } ?: run {
        if (verbose) {
          RequestLogStore.addEvent(
            "No releases found",
            level = LogLevel.DEBUG,
            category = EventCategory.UPDATE,
            body = "Channel: $checkedChannels, Version: ${BuildConfig.VERSION_NAME}",
          )
        }
        return Result.success(workDataOf(
          KEY_RESULT to RESULT_UP_TO_DATE,
          KEY_MESSAGE to context.getString(R.string.update_check_no_updates, checkedChannels),
        ))
      }

      ServerPrefs.setUpdateCheckConsecutiveFailures(context, 0)

      val currentVersion = BuildConfig.VERSION_NAME
      if (!SemVer.isNewer(currentVersion, release.tagName)) {
        if (verbose) {
          RequestLogStore.addEvent(
            "Already on latest version",
            level = LogLevel.DEBUG,
            category = EventCategory.UPDATE,
            body = "Checked: ${release.tagName} (${BuildConfig.UPDATE_CHANNEL})",
          )
        }
        ServerMetrics.setAvailableUpdate(null, null)
        if (ServerPrefs.isCrossChannelNotifyEnabled(context) && cachedReleasesJson != null) {
          checkCrossChannel(context, verbose)
        }
        return Result.success(workDataOf(
          KEY_RESULT to RESULT_UP_TO_DATE,
          KEY_MESSAGE to context.getString(R.string.update_check_up_to_date, currentVersion),
        ))
      }

      ServerPrefs.setCachedUpdateInfo(context, release.tagName, release.htmlUrl, release.etag)
      ServerMetrics.setAvailableUpdate(release.tagName, release.htmlUrl)

      RequestLogStore.addEvent(
        "Update available: ${release.tagName}",
        level = LogLevel.INFO,
        category = EventCategory.UPDATE,
        body = "Current: $currentVersion\nRelease: ${release.htmlUrl}",
      )

      val versionDisplay = release.tagName.removePrefix("v")

      val dismissed = ServerPrefs.getLastDismissedUpdateVersion(context)
      if (dismissed == release.tagName) {
        Log.d(TAG, "User dismissed notification for ${release.tagName} — skipping notification")
        return Result.success(workDataOf(
          KEY_RESULT to RESULT_UPDATE_AVAILABLE,
          KEY_MESSAGE to context.getString(R.string.notif_update_available_body, versionDisplay),
        ))
      }

      UpdateNotificationHelper.postUpdateNotification(context, release)

      return Result.success(workDataOf(
        KEY_RESULT to RESULT_UPDATE_AVAILABLE,
        KEY_MESSAGE to context.getString(R.string.notif_update_available_body, versionDisplay),
      ))

    } catch (e: CancellationException) {
      throw e
    } catch (e: UpdateCheckException) {
      val errorMessage = handleError(context, e, verbose)
      return Result.success(workDataOf(
        KEY_RESULT to RESULT_ERROR,
        KEY_MESSAGE to errorMessage,
      ))
    } catch (e: Exception) {
      Log.w(TAG, "Update check failed unexpectedly", e)
      if (verbose) {
        RequestLogStore.addEvent(
          "Update check failed — network error",
          level = LogLevel.WARNING,
          category = EventCategory.UPDATE,
          body = e.message ?: applicationContext.getString(R.string.error_unknown),
        )
      }
      return Result.success(workDataOf(
        KEY_RESULT to RESULT_ERROR,
        KEY_MESSAGE to context.getString(R.string.update_check_failed_network),
      ))
    }
  }

  private fun fetchLatestRelease(context: Context): ReleaseInfo? {
    val crossChannelEnabled = ServerPrefs.isCrossChannelNotifyEnabled(context)
    return if (crossChannelEnabled) {
      fetchFromReleasesList(context)
    } else {
      when (BuildConfig.UPDATE_CHANNEL) {
        "stable" -> fetchLatestStable(context)
        "beta" -> fetchLatestBetaOrStable(context)
        "dev" -> fetchLatestAny(context)
        else -> fetchLatestStable(context)
      }
    }
  }

  private fun fetchFromReleasesList(context: Context): ReleaseInfo? =
    fetchChannelRelease(
      context = context,
      endpoint = "releases?per_page=10",
      tagPattern = GitHubReleaseClient.DEV_TAG_PATTERN,
      // Cross-channel analysis runs on every check — conditional requests would
      // starve it of the body it needs, so no ETag here.
      useEtag = false,
      cacheReleasesJson = true,
      persistCache = false,
    )

  private fun fetchLatestStable(context: Context): ReleaseInfo? =
    fetchChannelRelease(
      context = context,
      endpoint = "releases/latest",
      tagPattern = null,
      useEtag = true,
      cacheReleasesJson = false,
      persistCache = true,
    )

  private fun fetchLatestBetaOrStable(context: Context): ReleaseInfo? =
    fetchChannelRelease(
      context = context,
      endpoint = "releases?per_page=10",
      tagPattern = GitHubReleaseClient.BETA_TAG_PATTERN,
      useEtag = true,
      cacheReleasesJson = true,
      persistCache = true,
    )

  private fun fetchLatestAny(context: Context): ReleaseInfo? =
    fetchChannelRelease(
      context = context,
      endpoint = "releases?per_page=10",
      tagPattern = GitHubReleaseClient.DEV_TAG_PATTERN,
      useEtag = true,
      cacheReleasesJson = true,
      persistCache = true,
    )

  /**
   * Shared channel-fetch pipeline. The four per-channel variants differ only in
   * endpoint, tag filter, and whether ETag caching / release-body caching /
   * persisted update-info apply — this helper encodes those axes once.
   *
   * `tagPattern == null` means the single-release `/releases/latest` shape;
   * otherwise the releases list is filtered by pattern.
   */
  private fun fetchChannelRelease(
    context: Context,
    endpoint: String,
    tagPattern: Regex?,
    useEtag: Boolean,
    cacheReleasesJson: Boolean,
    persistCache: Boolean,
  ): ReleaseInfo? {
    val url = "${GitHubConfig.API_BASE}/$endpoint"
    val cachedETag = if (useEtag) ServerPrefs.getCachedReleaseETag(context) else null
    val response = GitHubReleaseClient.fetchGitHub(url, cachedETag)

    return when (response) {
      is GitHubResponse.NotModified -> {
        // Only reachable when an ETag was sent; fall back to persisted state.
        if (!useEtag) return null
        val cachedVersion = ServerPrefs.getCachedLatestVersion(context) ?: return null
        val cachedUrl = ServerPrefs.getCachedReleaseHtmlUrl(context) ?: return null
        ReleaseInfo(cachedVersion, cachedUrl, cachedETag)
      }
      is GitHubResponse.Success -> {
        if (cacheReleasesJson) cachedReleasesJson = response.body
        val release = if (tagPattern != null) {
          GitHubReleaseClient.findBestRelease(response.body, tagPattern)
        } else {
          GitHubReleaseClient.parseRelease(response.body, response.etag)
        }
        if (persistCache) {
          release?.also {
            ServerPrefs.setCachedUpdateInfo(context, it.tagName, it.htmlUrl, response.etag)
          }
        }
        release
      }
      is GitHubResponse.Error -> throw UpdateCheckException(response.code, url)
    }
  }

  private fun checkCrossChannel(context: Context, verbose: Boolean) {
    val json = cachedReleasesJson ?: return
    val ownPattern = when (BuildConfig.UPDATE_CHANNEL) {
      "stable" -> GitHubReleaseClient.STABLE_TAG_PATTERN
      "beta" -> GitHubReleaseClient.BETA_TAG_PATTERN
      "dev" -> GitHubReleaseClient.DEV_TAG_PATTERN
      else -> return
    }

    val crossRelease = GitHubReleaseClient.findCrossChannelRelease(json, ownPattern) ?: return

    val currentVersion = BuildConfig.VERSION_NAME
    if (!SemVer.isNewer(currentVersion, crossRelease.tagName)) return

    val dismissed = ServerPrefs.getLastDismissedCrossChannelVersion(context)
    if (dismissed == crossRelease.tagName) {
      if (verbose) {
        Log.d(TAG, "Cross-channel release ${crossRelease.tagName} already dismissed")
      }
      return
    }

    val cached = ServerPrefs.getCachedCrossChannelVersion(context)
    if (cached == crossRelease.tagName) return

    ServerPrefs.setCachedCrossChannelVersion(context, crossRelease.tagName)

    if (verbose) {
      RequestLogStore.addEvent(
        "Cross-channel release: ${crossRelease.tagName}",
        level = LogLevel.DEBUG,
        category = EventCategory.UPDATE,
        body = "Channel: ${BuildConfig.UPDATE_CHANNEL}, Cross-channel: ${crossRelease.tagName}",
      )
    }

    UpdateNotificationHelper.postCrossChannelNotification(context, crossRelease)
  }

  private fun handleError(context: Context, e: UpdateCheckException, verbose: Boolean): String {
    when (e.httpCode) {
      403 -> {
        Log.w(TAG, "Update check rate limited (403)")
        if (verbose) {
          RequestLogStore.addEvent(
            "Update check failed — rate limited",
            level = LogLevel.WARNING,
            category = EventCategory.UPDATE,
            body = "HTTP 403: Rate limit exceeded.",
          )
        }
        return context.getString(R.string.update_check_failed_rate_limited)
      }
      404 -> {
        if (e.url.endsWith("/releases/latest")) {
          if (verbose) {
            RequestLogStore.addEvent(
              "No releases found for ${BuildConfig.UPDATE_CHANNEL} channel",
              level = LogLevel.DEBUG,
              category = EventCategory.UPDATE,
              body = "GitHub /releases/latest returned 404 — no releases for ${BuildConfig.UPDATE_CHANNEL} channel yet",
            )
          }
          return context.getString(R.string.update_check_no_updates, BuildConfig.UPDATE_CHANNEL)
        }

        val failures = ServerPrefs.getUpdateCheckConsecutiveFailures(context) + 1
        ServerPrefs.setUpdateCheckConsecutiveFailures(context, failures)
        Log.w(TAG, "Update check 404 — consecutive failures: $failures/$MAX_CONSECUTIVE_FAILURES")

        if (verbose) {
          RequestLogStore.addEvent(
            "Update check failed — repository not found",
            level = LogLevel.WARNING,
            category = EventCategory.UPDATE,
            body = "HTTP 404: ${e.url}\nConsecutive failures: $failures/$MAX_CONSECUTIVE_FAILURES",
          )
        }

        if (failures >= MAX_CONSECUTIVE_FAILURES) {
          ServerPrefs.setUpdateCheckEnabled(context, false)
          cancelUpdateCheck(context)
          RequestLogStore.addEvent(
            "Update check auto-disabled — repository not found",
            level = LogLevel.ERROR,
            category = EventCategory.UPDATE,
            body = "$MAX_CONSECUTIVE_FAILURES consecutive 404 errors. Re-enable in Settings or update the repository URL.",
          )
        }
        return context.getString(R.string.update_check_failed_not_found)
      }
      in 500..599 -> {
        Log.w(TAG, "Update check server error (${e.httpCode})")
        if (verbose) {
          RequestLogStore.addEvent(
            "Update check failed — server error",
            level = LogLevel.WARNING,
            category = EventCategory.UPDATE,
            body = "HTTP ${e.httpCode}: ${e.url}",
          )
        }
        return context.getString(R.string.update_check_failed_server_error)
      }
      else -> {
        Log.w(TAG, "Update check HTTP error: ${e.httpCode}")
        return context.getString(R.string.update_check_failed_http, e.httpCode)
      }
    }
  }

  companion object {
    private const val TAG = "OlliteRT.UpdateChk"
    private const val WORK_NAME = "ollitert_update_check"
    const val UPDATE_CHANNEL_ID = UpdateNotificationHelper.UPDATE_CHANNEL_ID
    const val UPDATE_NOTIFICATION_ID = UpdateNotificationHelper.UPDATE_NOTIFICATION_ID
    const val BETA_RELEASE_CHANNEL_ID = UpdateNotificationHelper.BETA_RELEASE_CHANNEL_ID
    const val DEV_RELEASE_CHANNEL_ID = UpdateNotificationHelper.DEV_RELEASE_CHANNEL_ID
    const val CROSS_CHANNEL_NOTIFICATION_ID = UpdateNotificationHelper.CROSS_CHANNEL_NOTIFICATION_ID
    private const val MAX_CONSECUTIVE_FAILURES = 5

    const val KEY_RESULT = "result"
    const val KEY_MESSAGE = "message"
    const val RESULT_UP_TO_DATE = "up_to_date"
    const val RESULT_UPDATE_AVAILABLE = "update_available"
    const val RESULT_ERROR = "error"
    const val RESULT_DISABLED = "disabled"
    private const val KEY_MANUAL_CHECK = "manual_check"

    const val GITHUB_RELEASES_URL = GitHubConfig.RELEASES_URL

    fun isOwnChannelTag(tag: String): Boolean = GitHubReleaseClient.isOwnChannelTag(tag)

    val STABLE_TAG_PATTERN get() = GitHubReleaseClient.STABLE_TAG_PATTERN
    val BETA_TAG_PATTERN get() = GitHubReleaseClient.BETA_TAG_PATTERN
    val DEV_TAG_PATTERN get() = GitHubReleaseClient.DEV_TAG_PATTERN

    internal fun parseRelease(json: String, etag: String?): ReleaseInfo? =
      GitHubReleaseClient.parseRelease(json, etag)

    internal fun findBestRelease(releasesJson: String, tagPattern: Regex): ReleaseInfo? =
      GitHubReleaseClient.findBestRelease(releasesJson, tagPattern)

    internal fun findCrossChannelRelease(releasesJson: String, ownPattern: Regex): ReleaseInfo? =
      GitHubReleaseClient.findCrossChannelRelease(releasesJson, ownPattern)

    fun buildUpdateIntent(context: Context, releaseHtmlUrl: String): Intent =
      UpdateNotificationHelper.buildUpdateIntent(context, releaseHtmlUrl)

    fun isPlayStoreBuild(context: Context): Boolean =
      UpdateNotificationHelper.isPlayStoreBuild(context)

    fun canPostUpdateNotification(context: Context): Boolean =
      UpdateNotificationHelper.canPostUpdateNotification(context)

    fun isUpdateChannelMuted(context: Context): Boolean =
      UpdateNotificationHelper.isUpdateChannelMuted(context)

    fun createNotificationChannel(context: Context) =
      UpdateNotificationHelper.createNotificationChannel(context)

    fun createCrossChannelNotificationChannels(context: Context) =
      UpdateNotificationHelper.createCrossChannelNotificationChannels(context)

    fun canPostCrossChannelNotification(context: Context): Boolean =
      UpdateNotificationHelper.canPostCrossChannelNotification(context)

    fun areCrossChannelChannelsMuted(context: Context): Boolean =
      UpdateNotificationHelper.areCrossChannelChannelsMuted(context)

    fun scheduleUpdateCheck(context: Context) {
      val intervalHours = ServerPrefs.getUpdateCheckIntervalHours(context).toLong()
      val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

      val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(intervalHours, TimeUnit.HOURS)
        .setConstraints(constraints)
        .setInitialDelay(1, TimeUnit.HOURS)
        .build()

      // UPDATE (not REPLACE): this runs on every app open, and REPLACE resets the
      // periodic timer each time — on a frequently used device background checks
      // would be postponed indefinitely. UPDATE applies constraint/interval
      // changes (e.g. after the user edits the interval setting) while keeping
      // the original next-run schedule.
      WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        WORK_NAME,
        ExistingPeriodicWorkPolicy.UPDATE,
        request,
      )
    }

    fun cancelUpdateCheck(context: Context) {
      WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
      val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      mgr?.cancel(UPDATE_NOTIFICATION_ID)
      ServerMetrics.setAvailableUpdate(null, null)
    }

    fun checkNow(context: Context): java.util.UUID {
      val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
      val request = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
        .setConstraints(constraints)
        .setInputData(workDataOf(KEY_MANUAL_CHECK to true))
        .build()
      WorkManager.getInstance(context).enqueue(request)
      return request.id
    }

    fun clearStaleNotification(context: Context) =
      UpdateNotificationHelper.clearStaleNotification(context)
  }
}

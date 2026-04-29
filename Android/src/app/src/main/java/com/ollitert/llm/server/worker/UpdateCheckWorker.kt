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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
import com.ollitert.llm.server.data.HTTP_CONNECT_TIMEOUT_MS
import com.ollitert.llm.server.data.HTTP_READ_TIMEOUT_MS
import com.ollitert.llm.server.data.ServerPrefs
import com.ollitert.llm.server.service.EventCategory
import com.ollitert.llm.server.service.LogLevel
import com.ollitert.llm.server.service.RequestLogStore
import com.ollitert.llm.server.service.ServerMetrics
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager worker that checks GitHub Releases for newer versions.
 * Runs periodically (default: every 24 hours) with network + battery constraints.
 *
 * Channel-aware: stable checks /releases/latest (stable only), beta/dev fetch the
 * releases list and filter by tag pattern. See [fetchLatestRelease] for details.
 */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
  @Assisted appContext: Context,
  @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

  private var cachedReleasesJson: String? = null

  override suspend fun doWork(): Result {
    val context = applicationContext

    // Manual checks (via "Check Now" button) bypass the enabled check —
    // only periodic scheduled checks should respect the toggle.
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

    try {
      val release = fetchLatestRelease(context) ?: run {
        // null means either 304 Not Modified with no cache, or no matching releases found.
        // Both mean there's nothing newer to report — but distinguish for user feedback.
        if (verbose) {
          RequestLogStore.addEvent(
            "No releases found",
            level = LogLevel.DEBUG,
            category = EventCategory.UPDATE,
            body = "Channel: ${BuildConfig.UPDATE_CHANNEL}, Version: ${BuildConfig.VERSION_NAME}",
          )
        }
        return Result.success(workDataOf(
          KEY_RESULT to RESULT_UP_TO_DATE,
          KEY_MESSAGE to context.getString(R.string.update_check_no_updates),
        ))
      }

      // Reset consecutive failure counter on any successful response
      ServerPrefs.setUpdateCheckConsecutiveFailures(context, 0)

      val currentVersion = BuildConfig.VERSION_NAME
      if (!SemVer.isNewer(currentVersion, release.tagName)) {
        // Current version is up-to-date
        if (verbose) {
          RequestLogStore.addEvent(
            "Already on latest version",
            level = LogLevel.DEBUG,
            category = EventCategory.UPDATE,
            body = "Checked: ${release.tagName} (${BuildConfig.UPDATE_CHANNEL})",
          )
        }
        // Clear any stale update state
        ServerMetrics.setAvailableUpdate(null, null)
        if (ServerPrefs.isCrossChannelNotifyEnabled(context) && cachedReleasesJson != null) {
          checkCrossChannel(context, verbose)
        }
        return Result.success(workDataOf(
          KEY_RESULT to RESULT_UP_TO_DATE,
          KEY_MESSAGE to context.getString(R.string.update_check_up_to_date, currentVersion),
        ))
      }

      // Newer version found — cache it and surface it
      ServerPrefs.setCachedUpdateInfo(context, release.tagName, release.htmlUrl, release.etag)
      ServerMetrics.setAvailableUpdate(release.tagName, release.htmlUrl)

      RequestLogStore.addEvent(
        "Update available: ${release.tagName}",
        level = LogLevel.INFO,
        category = EventCategory.UPDATE,
        body = "Current: $currentVersion\nRelease: ${release.htmlUrl}",
      )

      val versionDisplay = release.tagName.removePrefix("v")

      // Check if user already dismissed this exact version
      val dismissed = ServerPrefs.getLastDismissedUpdateVersion(context)
      if (dismissed == release.tagName) {
        Log.d(TAG, "User dismissed notification for ${release.tagName} — skipping notification")
        return Result.success(workDataOf(
          KEY_RESULT to RESULT_UPDATE_AVAILABLE,
          KEY_MESSAGE to context.getString(R.string.notif_update_available_body, versionDisplay),
        ))
      }

      postUpdateNotification(context, release)

      if (ServerPrefs.isCrossChannelNotifyEnabled(context) && cachedReleasesJson != null) {
        checkCrossChannel(context, verbose)
      }

      return Result.success(workDataOf(
        KEY_RESULT to RESULT_UPDATE_AVAILABLE,
        KEY_MESSAGE to context.getString(R.string.notif_update_available_body, versionDisplay),
      ))

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

  // ── GitHub API fetching ──────────────────────────────────────────────────

  private data class ReleaseInfo(
    val tagName: String,
    val htmlUrl: String,
    val etag: String?,
  )

  /**
   * Fetch the latest applicable release from GitHub based on the build channel.
   * Returns null if the API returned 304 (Not Modified) or no applicable release was found.
   */
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

  private fun fetchFromReleasesList(context: Context): ReleaseInfo? {
    val url = "${GitHubConfig.API_BASE}/releases?per_page=10"
    val response = fetchGitHub(url, etag = null)
    return when (response) {
      is GitHubResponse.NotModified -> null
      is GitHubResponse.Success -> {
        cachedReleasesJson = response.body
        val ownPattern = when (BuildConfig.UPDATE_CHANNEL) {
          "stable" -> STABLE_TAG_PATTERN
          "beta" -> BETA_TAG_PATTERN
          "dev" -> DEV_TAG_PATTERN
          else -> STABLE_TAG_PATTERN
        }
        findBestRelease(response.body, ownPattern)
      }
      is GitHubResponse.Error -> throw UpdateCheckException(response.code, url)
    }
  }

  /**
   * Prod channel: GET /releases/latest — GitHub automatically skips pre-releases.
   */
  private fun fetchLatestStable(context: Context): ReleaseInfo? {
    val url = "${GitHubConfig.API_BASE}/releases/latest"
    val cachedETag = ServerPrefs.getCachedReleaseETag(context)
    val response = fetchGitHub(url, cachedETag)

    return when (response) {
      is GitHubResponse.NotModified -> {
        // Use cached data — still the same release
        val cachedVersion = ServerPrefs.getCachedLatestVersion(context) ?: return null
        val cachedUrl = ServerPrefs.getCachedReleaseHtmlUrl(context) ?: return null
        ReleaseInfo(cachedVersion, cachedUrl, cachedETag)
      }
      is GitHubResponse.Success -> parseRelease(response.body, response.etag)
      is GitHubResponse.Error -> throw UpdateCheckException(response.code, url)
    }
  }

  /**
   * Beta channel: GET /releases?per_page=10, filter for beta or stable tags.
   */
  private fun fetchLatestBetaOrStable(context: Context): ReleaseInfo? {
    val url = "${GitHubConfig.API_BASE}/releases?per_page=10"
    val response = fetchGitHub(url, etag = null) // Don't cache list endpoint
    return when (response) {
      is GitHubResponse.NotModified -> null
      is GitHubResponse.Success -> findBestRelease(response.body, BETA_TAG_PATTERN)
      is GitHubResponse.Error -> throw UpdateCheckException(response.code, url)
    }
  }

  /**
   * Dev channel: GET /releases?per_page=10, take the most recent non-draft release.
   */
  private fun fetchLatestAny(context: Context): ReleaseInfo? {
    val url = "${GitHubConfig.API_BASE}/releases?per_page=10"
    val response = fetchGitHub(url, etag = null)
    return when (response) {
      is GitHubResponse.NotModified -> null
      is GitHubResponse.Success -> findBestRelease(response.body, DEV_TAG_PATTERN)
      is GitHubResponse.Error -> throw UpdateCheckException(response.code, url)
    }
  }

  /**
   * Find the newest release matching the given tag pattern, skipping drafts.
   * Releases are returned by GitHub in reverse chronological order.
   */
  private fun findBestRelease(releasesJson: String, tagPattern: Regex): ReleaseInfo? {
    return Json.parseToJsonElement(releasesJson).jsonArray
      .map { it.jsonObject }
      .firstOrNull { release ->
        release["draft"]?.jsonPrimitive?.booleanOrNull != true &&
          (release["tag_name"]?.jsonPrimitive?.content ?: "").let { it.isNotBlank() && tagPattern.matches(it) } &&
          (release["html_url"]?.jsonPrimitive?.content ?: "").isNotBlank()
      }?.let { release ->
        val tag = release["tag_name"]?.jsonPrimitive?.content ?: return@let null
        val url = release["html_url"]?.jsonPrimitive?.content ?: return@let null
        ReleaseInfo(tagName = tag, htmlUrl = url, etag = null)
      }
  }

  private fun findCrossChannelRelease(releasesJson: String): ReleaseInfo? {
    val ownPattern = when (BuildConfig.UPDATE_CHANNEL) {
      "stable" -> STABLE_TAG_PATTERN
      "beta" -> BETA_TAG_PATTERN
      "dev" -> DEV_TAG_PATTERN
      else -> STABLE_TAG_PATTERN
    }
    return Json.parseToJsonElement(releasesJson).jsonArray
      .map { it.jsonObject }
      .firstOrNull { release ->
        release["draft"]?.jsonPrimitive?.booleanOrNull != true &&
          (release["tag_name"]?.jsonPrimitive?.content ?: "").let { tag ->
            tag.isNotBlank() && !ownPattern.matches(tag) && SemVer.parse(tag) != null
          } &&
          (release["html_url"]?.jsonPrimitive?.content ?: "").isNotBlank()
      }?.let { release ->
        val tag = release["tag_name"]?.jsonPrimitive?.content ?: return@let null
        val url = release["html_url"]?.jsonPrimitive?.content ?: return@let null
        ReleaseInfo(tagName = tag, htmlUrl = url, etag = null)
      }
  }

  private fun checkCrossChannel(context: Context, verbose: Boolean) {
    val json = cachedReleasesJson ?: return
    val crossRelease = findCrossChannelRelease(json) ?: return

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

    postCrossChannelNotification(context, crossRelease)
  }

  private fun postCrossChannelNotification(context: Context, release: ReleaseInfo) {
    val channelId = crossChannelNotificationChannelId(release.tagName)

    val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
    val channel = mgr.getNotificationChannel(channelId)
    if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) return
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

    val tapIntent = buildUpdateIntent(context, release.htmlUrl)
    val contentIntent = PendingIntent.getActivity(
      context, CROSS_CHANNEL_REQUEST_CODE, tapIntent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    val dismissIntent = PendingIntent.getBroadcast(
      context, CROSS_CHANNEL_DISMISS_REQUEST_CODE,
      Intent(context, UpdateDismissReceiver::class.java)
        .putExtra(UpdateDismissReceiver.EXTRA_DISMISSED_VERSION, release.tagName)
        .putExtra(UpdateDismissReceiver.EXTRA_IS_CROSS_CHANNEL, true),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    val versionDisplay = release.tagName.removePrefix("v")
    val titleRes = when {
      release.tagName.contains("-dev.") -> R.string.notif_cross_channel_title_dev
      release.tagName.contains("-beta.") -> R.string.notif_cross_channel_title_beta
      else -> R.string.notif_cross_channel_title_stable
    }

    val notification = NotificationCompat.Builder(context, channelId)
      .setContentTitle(context.getString(titleRes))
      .setContentText(context.getString(R.string.notif_cross_channel_body, versionDisplay))
      .setSmallIcon(R.mipmap.ic_launcher_monochrome)
      .setContentIntent(contentIntent)
      .setDeleteIntent(dismissIntent)
      .setAutoCancel(true)
      .build()

    mgr.notify(CROSS_CHANNEL_NOTIFICATION_ID, notification)
  }

  private fun parseRelease(json: String, etag: String?): ReleaseInfo? {
    val obj = Json.parseToJsonElement(json).jsonObject
    val tag = obj["tag_name"]?.jsonPrimitive?.content ?: ""
    val url = obj["html_url"]?.jsonPrimitive?.content ?: ""
    if (tag.isBlank() || url.isBlank()) return null
    return ReleaseInfo(tag, url, etag)
  }

  // ── HTTP layer ───────────────────────────────────────────────────────────

  private sealed class GitHubResponse {
    data class Success(val body: String, val etag: String?) : GitHubResponse()
    data object NotModified : GitHubResponse()
    data class Error(val code: Int) : GitHubResponse()
  }

  private fun fetchGitHub(url: String, etag: String?): GitHubResponse {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
      connection.connectTimeout = HTTP_CONNECT_TIMEOUT_MS
      connection.readTimeout = HTTP_READ_TIMEOUT_MS
      connection.setRequestProperty("Accept", "application/vnd.github+json")
      connection.setRequestProperty("User-Agent", "OlliteRT/${BuildConfig.VERSION_NAME}")
      if (etag != null) {
        connection.setRequestProperty("If-None-Match", etag)
      }

      val code = connection.responseCode
      return when {
        code == 304 -> GitHubResponse.NotModified
        code in 200..299 -> {
          val body = connection.inputStream.bufferedReader().use { it.readText() }
          val responseEtag = connection.getHeaderField("ETag")
          GitHubResponse.Success(body, responseEtag)
        }
        else -> GitHubResponse.Error(code)
      }
    } finally {
      connection.disconnect()
    }
  }

  // ── Error handling ───────────────────────────────────────────────────────

  private class UpdateCheckException(val httpCode: Int, val url: String) : Exception("HTTP $httpCode: $url")

  /** Handle HTTP errors and return a user-facing message for toast feedback. */
  private fun handleError(context: Context, e: UpdateCheckException, verbose: Boolean): String {
    when (e.httpCode) {
      403 -> {
        // Rate limited — silently skip, resets hourly
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
        // Repository not found — increment consecutive failure counter
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

        // Auto-disable after too many consecutive 404s (repo migrated, URL wrong)
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
        // GitHub server error — transient, try again next cycle
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

  // ── Notification ─────────────────────────────────────────────────────────

  private fun postUpdateNotification(context: Context, release: ReleaseInfo) {
    // Check notification permission (required on Android 13+)
    if (!canPostUpdateNotification(context)) {
      if (ServerPrefs.isVerboseDebugEnabled(context)) {
        RequestLogStore.addEvent(
          "Update check skipped — notification permission not granted",
          level = LogLevel.DEBUG,
          category = EventCategory.UPDATE,
        )
      }
      // Still update ServerMetrics so API endpoints and foreground notification show the update
      return
    }

    val tapIntent = buildUpdateIntent(context, release.htmlUrl)
    val contentIntent = PendingIntent.getActivity(
      context, UPDATE_REQUEST_CODE, tapIntent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    // deleteIntent fires when user swipes the notification away
    val dismissIntent = PendingIntent.getBroadcast(
      context, UPDATE_DISMISS_REQUEST_CODE,
      Intent(context, UpdateDismissReceiver::class.java)
        .putExtra(UpdateDismissReceiver.EXTRA_DISMISSED_VERSION, release.tagName),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    val versionDisplay = release.tagName.removePrefix("v")
    val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
      .setContentTitle(context.getString(R.string.notif_update_available_title))
      .setContentText(context.getString(R.string.notif_update_available_body, versionDisplay))
      .setSmallIcon(R.mipmap.ic_launcher_monochrome)
      .setContentIntent(contentIntent)
      .setDeleteIntent(dismissIntent)
      .setAutoCancel(true)
      .build()

    val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    if (mgr == null) {
      Log.e(TAG, "NotificationManager unavailable — cannot post update notification")
      return
    }
    mgr.notify(UPDATE_NOTIFICATION_ID, notification)
  }

  companion object {
    private const val TAG = "OlliteRT.UpdateChk"
    private const val WORK_NAME = "ollitert_update_check"
    const val UPDATE_CHANNEL_ID = "ollitert-app-update"
    const val UPDATE_NOTIFICATION_ID = 43
    private const val UPDATE_REQUEST_CODE = 100
    private const val UPDATE_DISMISS_REQUEST_CODE = 101
    const val BETA_RELEASE_CHANNEL_ID = "ollitert-beta-release"
    const val DEV_RELEASE_CHANNEL_ID = "ollitert-dev-release"
    private const val CROSS_CHANNEL_NOTIFICATION_ID = 44
    private const val CROSS_CHANNEL_REQUEST_CODE = 102
    private const val CROSS_CHANNEL_DISMISS_REQUEST_CODE = 103
    private const val MAX_CONSECUTIVE_FAILURES = 5

    // Output data keys for communicating check results to the UI (via WorkInfo.outputData)
    const val KEY_RESULT = "result"
    const val KEY_MESSAGE = "message"
    const val RESULT_UP_TO_DATE = "up_to_date"
    const val RESULT_UPDATE_AVAILABLE = "update_available"
    const val RESULT_ERROR = "error"
    const val RESULT_DISABLED = "disabled"
    private const val KEY_MANUAL_CHECK = "manual_check"

    /** GitHub Releases page URL for the "What's New" link in Settings. */
    const val GITHUB_RELEASES_URL = GitHubConfig.RELEASES_URL

    // Tag patterns for channel-aware filtering (internal for testability)
    internal val STABLE_TAG_PATTERN = Regex("^v\\d+\\.\\d+\\.\\d+$")
    internal val BETA_TAG_PATTERN = Regex("^v\\d+\\.\\d+\\.\\d+(-beta\\.\\d+)?$")
    internal val DEV_TAG_PATTERN = Regex("^v\\d+\\.\\d+\\.\\d+(-(?:dev|beta)\\.\\d+)?$")

    /**
     * Determine tap target based on install source:
     * - Play Store users → Play Store listing (they update from there)
     * - Sideloaded users → GitHub Release page (changelog + APK download)
     */
    fun buildUpdateIntent(context: Context, releaseHtmlUrl: String): Intent {
      val uri = if (isPlayStoreBuild(context)) {
        Uri.parse("market://details?id=${context.packageName}")
      } else {
        Uri.parse(releaseHtmlUrl)
      }
      val intent = Intent(Intent.ACTION_VIEW, uri)
      // Play Store intent fallback — if Play Store app isn't installed (rare: degoogled ROMs)
      if (isPlayStoreBuild(context)) {
        try {
          // Verify the market:// intent can be resolved
          if (intent.resolveActivity(context.packageManager) == null) {
            return Intent(Intent.ACTION_VIEW, Uri.parse(releaseHtmlUrl))
          }
        } catch (_: Exception) {
          return Intent(Intent.ACTION_VIEW, Uri.parse(releaseHtmlUrl))
        }
      }
      return intent
    }

    /** Check if the app was installed from the Google Play Store. */
    fun isPlayStoreBuild(context: Context): Boolean {
      return try {
        val info = context.packageManager.getInstallSourceInfo(context.packageName)
        info.installingPackageName == "com.android.vending"
      } catch (_: Exception) {
        false
      }
    }

    /**
     * Check if we can actually post update notifications.
     * Checks both the app-level permission and channel-level mute status.
     */
    fun canPostUpdateNotification(context: Context): Boolean {
      if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
      val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        ?: return false
      val channel = mgr.getNotificationChannel(UPDATE_CHANNEL_ID)
      // channel == null means not created yet — will be created with IMPORTANCE_DEFAULT
      return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /**
     * Check if notifications are enabled at the app level but the update channel is muted.
     * Used by Settings to show a distinct message ("channel is muted") vs "permission not granted".
     */
    fun isUpdateChannelMuted(context: Context): Boolean {
      if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
      val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        ?: return false
      val channel = mgr.getNotificationChannel(UPDATE_CHANNEL_ID) ?: return false
      return channel.importance == NotificationManager.IMPORTANCE_NONE
    }

    /** Create the update notification channel. Call from Application.onCreate(). */
    fun createNotificationChannel(context: Context) {
      val channel = NotificationChannel(
        UPDATE_CHANNEL_ID,
        context.getString(R.string.notif_channel_app_update_name),
        NotificationManager.IMPORTANCE_DEFAULT,
      ).apply {
        description = context.getString(R.string.notif_channel_app_update_desc)
      }
      val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      if (mgr == null) {
        Log.e(TAG, "NotificationManager unavailable — cannot create update channel")
        return
      }
      mgr.createNotificationChannel(channel)
    }

    fun createCrossChannelNotificationChannels(context: Context) {
      val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      if (mgr == null) {
        Log.e(TAG, "NotificationManager unavailable — cannot create cross-channel notification channels")
        return
      }
      val betaChannel = NotificationChannel(
        BETA_RELEASE_CHANNEL_ID,
        context.getString(R.string.notif_channel_beta_release_name),
        NotificationManager.IMPORTANCE_DEFAULT,
      ).apply {
        description = context.getString(R.string.notif_channel_beta_release_desc)
      }
      val devChannel = NotificationChannel(
        DEV_RELEASE_CHANNEL_ID,
        context.getString(R.string.notif_channel_dev_release_name),
        NotificationManager.IMPORTANCE_DEFAULT,
      ).apply {
        description = context.getString(R.string.notif_channel_dev_release_desc)
      }
      mgr.createNotificationChannel(betaChannel)
      mgr.createNotificationChannel(devChannel)
    }

    fun canPostCrossChannelNotification(context: Context): Boolean {
      if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
      val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        ?: return false
      val beta = mgr.getNotificationChannel(BETA_RELEASE_CHANNEL_ID)
      val dev = mgr.getNotificationChannel(DEV_RELEASE_CHANNEL_ID)
      val betaActive = beta == null || beta.importance != NotificationManager.IMPORTANCE_NONE
      val devActive = dev == null || dev.importance != NotificationManager.IMPORTANCE_NONE
      return betaActive || devActive
    }

    fun areCrossChannelChannelsMuted(context: Context): Boolean {
      if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
      val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        ?: return false
      val beta = mgr.getNotificationChannel(BETA_RELEASE_CHANNEL_ID)
      val dev = mgr.getNotificationChannel(DEV_RELEASE_CHANNEL_ID)
      val betaMuted = beta != null && beta.importance == NotificationManager.IMPORTANCE_NONE
      val devMuted = dev != null && dev.importance == NotificationManager.IMPORTANCE_NONE
      return betaMuted && devMuted
    }

    private fun crossChannelNotificationChannelId(tag: String): String {
      return when {
        tag.contains("-dev.") -> DEV_RELEASE_CHANNEL_ID
        tag.contains("-beta.") -> BETA_RELEASE_CHANNEL_ID
        else -> UPDATE_CHANNEL_ID
      }
    }

    /**
     * Schedule the periodic update check. Called from Application.onCreate()
     * and when the user toggles the setting on or changes the frequency.
     */
    fun scheduleUpdateCheck(context: Context) {
      val intervalHours = ServerPrefs.getUpdateCheckIntervalHours(context).toLong()

      val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

      val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(intervalHours, TimeUnit.HOURS)
        .setConstraints(constraints)
        .setInitialDelay(1, TimeUnit.HOURS) // Don't check immediately on every app start
        .build()

      WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        WORK_NAME,
        ExistingPeriodicWorkPolicy.REPLACE, // REPLACE to pick up interval changes
        request,
      )
    }

    /** Cancel the periodic update check. Called when the user disables the setting. */
    fun cancelUpdateCheck(context: Context) {
      WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
      // Clear any pending notification and cached state
      val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      mgr?.cancel(UPDATE_NOTIFICATION_ID)
      ServerMetrics.setAvailableUpdate(null, null)
    }

    /**
     * Enqueue a one-time immediate check (for the "Check Now" button in Settings).
     * Returns the work request UUID so the caller can observe the result via
     * [WorkManager.getWorkInfoByIdFlow].
     */
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

    /**
     * Clear stale update notification after an auto-update.
     * Call from Application.onCreate() — if the cached "latest" version is no longer
     * newer than the installed version, the app was updated and the notification is stale.
     */
    fun clearStaleNotification(context: Context) {
      val cached = ServerPrefs.getCachedLatestVersion(context) ?: return
      if (!SemVer.isNewer(BuildConfig.VERSION_NAME, cached)) {
        // Cached "latest" is no longer newer than installed — update happened
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        mgr?.cancel(UPDATE_NOTIFICATION_ID)
        ServerPrefs.clearUpdateState(context)
        ServerMetrics.setAvailableUpdate(null, null)
        if (ServerPrefs.isVerboseDebugEnabled(context)) {
          RequestLogStore.addEvent(
            "Stale update notification cleared",
            level = LogLevel.DEBUG,
            category = EventCategory.UPDATE,
            body = "App updated to ${BuildConfig.VERSION_NAME}, cached latest was $cached",
          )
        }
      } else {
        // Still have a pending update — restore it to ServerMetrics so API/notification surface it
        val url = ServerPrefs.getCachedReleaseHtmlUrl(context)
        ServerMetrics.setAvailableUpdate(cached, url)
      }
    }
  }
}

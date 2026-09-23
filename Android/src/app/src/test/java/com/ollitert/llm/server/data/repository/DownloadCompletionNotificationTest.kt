/*
 * Copyright 2026 @NightMean (https://github.com/NightMean)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.ollitert.llm.server.data.repository

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ollitert.llm.server.OlliteRTLifecycleProvider
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.model.ModelDownloadStatus
import com.ollitert.llm.server.data.model.ModelDownloadStatusType
import com.ollitert.llm.server.data.storage.KEY_MODEL_DOWNLOAD_ERROR_MESSAGE
import com.ollitert.llm.server.data.storage.KEY_MODEL_DOWNLOAD_RATE
import com.ollitert.llm.server.data.storage.KEY_MODEL_DOWNLOAD_RECEIVED_BYTES
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DownloadCompletionNotificationTest(
  private val sdkInt: Int,
  private val runtimePermissionRequired: Boolean,
) {
  private val context = mockk<Context>()
  private val packageManager = mockk<PackageManager>()
  private val workManager = mockk<WorkManager>()
  private val notificationManager = mockk<NotificationManagerCompat>(relaxUnitFun = true)
  private val platformNotificationManager = mockk<NotificationManager>(relaxUnitFun = true)
  private val lifecycleProvider = OlliteRTLifecycleProvider()
  private val workerId = UUID.randomUUID()
  private val workInfo = MutableLiveData<WorkInfo?>()
  private val model = Model(name = "test", sizeInBytes = 100L).apply { preProcess() }
  private val statuses = mutableListOf<ModelDownloadStatus>()
  private val notification = Notification()

  @Before
  fun setUp() {
    ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
      override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
      override fun postToMainThread(runnable: Runnable) = runnable.run()
      override fun isMainThread() = true
    })
    mockkObject(WorkManager.Companion)
    mockkStatic(
      NotificationManagerCompat::class,
      ContextCompat::class,
      PendingIntent::class,
      Uri::class,
    )
    mockkConstructor(NotificationCompat.Builder::class)
    every { WorkManager.getInstance(context) } returns workManager
    every { workManager.getWorkInfoByIdLiveData(workerId) } returns workInfo
    every { NotificationManagerCompat.from(context) } returns notificationManager
    every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns platformNotificationManager
    every { context.packageManager } returns packageManager
    every { context.packageName } returns "com.ollitert.llm.server"
    every { packageManager.getLaunchIntentForPackage("com.ollitert.llm.server") } returns Intent()
    every { context.getString(R.string.notif_channel_download_name) } returns "Downloads"
    every { context.getString(R.string.notification_title_success) } returns "Downloaded"
    every { context.getString(R.string.notification_content_success) } returns "%s downloaded"
    every { context.getString(R.string.notification_title_fail) } returns "Download failed"
    every { context.getString(R.string.notification_content_fail) } returns "%s failed"
    every { PendingIntent.getActivity(context, any(), any<Intent>(), any()) } returns mockk()
    every { Uri.parse(any()) } returns mockk()
    every { anyConstructed<NotificationCompat.Builder>().build() } returns notification
    every {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    } returns PackageManager.PERMISSION_DENIED
  }

  @After
  fun tearDown() {
    unmockkConstructor(NotificationCompat.Builder::class)
    unmockkObject(WorkManager.Companion)
    unmockkStatic(
      NotificationManagerCompat::class,
      ContextCompat::class,
      PendingIntent::class,
      Uri::class,
    )
    ArchTaskExecutor.getInstance().setDelegate(null)
  }

  @Test
  fun deniedPermissionOnlySuppressesSuccessNotificationsOnModernAndroid() {
    observe()

    emit(WorkInfo.State.SUCCEEDED)
    emit(WorkInfo.State.SUCCEEDED)

    assertEquals(listOf(ModelDownloadStatus(ModelDownloadStatusType.SUCCEEDED)), statuses)
    assertFalse(workInfo.hasObservers())
    verify(exactly = if (runtimePermissionRequired) 0 else 1) {
      notificationManager.notify(1, notification)
    }
    verify(exactly = if (runtimePermissionRequired) 1 else 0) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  @Test
  fun grantedPermissionPostsSuccessNotification() {
    grantPermission()
    observe()

    emit(WorkInfo.State.SUCCEEDED)

    assertEquals(listOf(ModelDownloadStatus(ModelDownloadStatusType.SUCCEEDED)), statuses)
    assertFalse(workInfo.hasObservers())
    verify(exactly = 1) { notificationManager.notify(1, notification) }
    verify(exactly = if (runtimePermissionRequired) 1 else 0) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  @Test
  fun deniedPermissionKeepsFailureAndLastProgress() {
    observe()
    emit(
      WorkInfo.State.RUNNING,
      progress = Data.Builder()
        .putLong(KEY_MODEL_DOWNLOAD_RECEIVED_BYTES, 47L)
        .putLong(KEY_MODEL_DOWNLOAD_RATE, 8L)
        .build(),
    )

    emit(
      WorkInfo.State.FAILED,
      outputData = Data.Builder()
        .putString(KEY_MODEL_DOWNLOAD_ERROR_MESSAGE, "Network unavailable")
        .build(),
    )

    assertEquals(
      listOf(
        ModelDownloadStatus(
          ModelDownloadStatusType.IN_PROGRESS,
          totalBytes = 100L,
          receivedBytes = 47L,
          bytesPerSecond = 8L,
        ),
        ModelDownloadStatus(
          ModelDownloadStatusType.FAILED,
          totalBytes = 100L,
          receivedBytes = 47L,
          errorMessage = "Network unavailable",
        ),
      ),
      statuses,
    )
    assertFalse(workInfo.hasObservers())
    verify(exactly = if (runtimePermissionRequired) 0 else 1) {
      notificationManager.notify(1, notification)
    }
    verify(exactly = if (runtimePermissionRequired) 1 else 0) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  @Test
  fun grantedPermissionPostsFailureNotification() {
    grantPermission()
    observe()

    emit(WorkInfo.State.FAILED)

    assertEquals(
      listOf(ModelDownloadStatus(ModelDownloadStatusType.FAILED, totalBytes = 100L)),
      statuses,
    )
    assertFalse(workInfo.hasObservers())
    verify(exactly = 1) { notificationManager.notify(1, notification) }
    verify(exactly = if (runtimePermissionRequired) 1 else 0) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  @Test
  fun returningToForegroundSuppressesSuccessNotifications() {
    observe()
    lifecycleProvider.isAppInForeground = true

    emit(WorkInfo.State.SUCCEEDED)

    assertEquals(listOf(ModelDownloadStatus(ModelDownloadStatusType.SUCCEEDED)), statuses)
    assertFalse(workInfo.hasObservers())
    verify(exactly = 0) { notificationManager.notify(any(), any()) }
    verify(exactly = 0) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  @Test
  fun foregroundFailuresDoNotPostNotifications() {
    lifecycleProvider.isAppInForeground = true
    observe()

    emit(WorkInfo.State.FAILED)

    assertEquals(
      listOf(ModelDownloadStatus(ModelDownloadStatusType.FAILED, totalBytes = 100L)),
      statuses,
    )
    assertFalse(workInfo.hasObservers())
    verify(exactly = 0) { notificationManager.notify(any(), any()) }
    verify(exactly = 0) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  @Test
  fun backgroundingBeforeCompletionAllowsNotification() {
    grantPermission()
    lifecycleProvider.isAppInForeground = true
    observe()
    lifecycleProvider.isAppInForeground = false

    emit(WorkInfo.State.SUCCEEDED)

    assertEquals(listOf(ModelDownloadStatus(ModelDownloadStatusType.SUCCEEDED)), statuses)
    assertFalse(workInfo.hasObservers())
    verify(exactly = 1) { notificationManager.notify(1, notification) }
  }

  @Test
  fun cancelledDownloadsResetProgressWithoutNotifying() {
    observe()
    emit(
      WorkInfo.State.RUNNING,
      progress = Data.Builder().putLong(KEY_MODEL_DOWNLOAD_RECEIVED_BYTES, 47L).build(),
    )

    emit(WorkInfo.State.CANCELLED)

    assertEquals(
      listOf(
        ModelDownloadStatus(ModelDownloadStatusType.IN_PROGRESS, totalBytes = 100L, receivedBytes = 47L),
        ModelDownloadStatus(ModelDownloadStatusType.NOT_DOWNLOADED),
      ),
      statuses,
    )
    assertFalse(workInfo.hasObservers())
    verify(exactly = 0) { notificationManager.notify(any(), any()) }
    verify(exactly = 0) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  @Test
  fun callbackFailureStillDetachesObserver() {
    val failure = IllegalStateException("Status callback failed")
    observe { _, _ -> throw failure }

    assertSame(failure, assertThrows(IllegalStateException::class.java) {
      emit(WorkInfo.State.SUCCEEDED)
    })

    assertFalse(workInfo.hasObservers())
    verify(exactly = 0) { notificationManager.notify(any(), any()) }
  }

  private fun grantPermission() {
    every {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    } returns PackageManager.PERMISSION_GRANTED
  }

  private fun observe(
    onStatusUpdated: (Model, ModelDownloadStatus) -> Unit = { observedModel, status ->
      assertSame(model, observedModel)
      statuses += status
    },
  ) {
    DownloadRepository(context, lifecycleProvider).observerWorkerProgress(
      workerId = workerId,
      model = model,
      sdkInt = sdkInt,
      onStatusUpdated = onStatusUpdated,
    )
  }

  private fun emit(
    state: WorkInfo.State,
    outputData: Data = Data.EMPTY,
    progress: Data = Data.EMPTY,
  ) {
    workInfo.value = WorkInfo(
      id = workerId,
      state = state,
      tags = emptySet(),
      outputData = outputData,
      progress = progress,
    )
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "API {0}")
    fun versions(): Collection<Array<Any>> = listOf(
      arrayOf(30, false),
      arrayOf(31, false),
      arrayOf(32, false),
      arrayOf(33, true),
      arrayOf(34, true),
    )
  }
}

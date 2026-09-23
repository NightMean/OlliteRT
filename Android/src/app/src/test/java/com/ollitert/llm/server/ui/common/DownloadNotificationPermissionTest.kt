package com.ollitert.llm.server.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.core.content.ContextCompat
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.ui.modelmanager.ModelManagerViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class DownloadNotificationPermissionTest {
  private val context = mockk<Context>()
  private val launcher = mockk<ManagedActivityResultLauncher<String, Boolean>>(relaxed = true)
  private val viewModel = mockk<ModelManagerViewModel>(relaxed = true)
  private val model = Model("test")

  @Before
  fun setUp() {
    mockkStatic(ContextCompat::class)
    every {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    } returns PackageManager.PERMISSION_DENIED
  }

  @After
  fun tearDown() {
    unmockkStatic(ContextCompat::class)
  }

  @Test
  fun pre33DownloadsStartEvenWhenAppNotificationsAreDisabled() {
    for (sdk in listOf(30, 31, 32)) {
      checkNotificationPermissionAndStartDownload(context, launcher, viewModel, model, sdkInt = sdk)
    }

    verify(exactly = 3) { viewModel.downloadModel(model) }
    verify(exactly = 0) { launcher.launch(any()) }
    verify(exactly = 0) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  @Test
  fun modernAndroidRequestsPermissionInsteadOfStartingTwice() {
    for (sdk in listOf(33, 34)) {
      checkNotificationPermissionAndStartDownload(context, launcher, viewModel, model, sdkInt = sdk)
    }
    verify(exactly = 0) { viewModel.downloadModel(any()) }
    verify(exactly = 2) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
  }

  @Test
  fun modernAndroidStartsDirectlyWhenPermissionAlreadyGranted() {
    every {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    } returns PackageManager.PERMISSION_GRANTED
    checkNotificationPermissionAndStartDownload(context, launcher, viewModel, model, sdkInt = 33)
    verify(exactly = 1) { viewModel.downloadModel(model) }
    verify(exactly = 0) { launcher.launch(any()) }
  }
}

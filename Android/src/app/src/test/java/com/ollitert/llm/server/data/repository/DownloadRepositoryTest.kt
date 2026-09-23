package com.ollitert.llm.server.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ollitert.llm.server.OlliteRTLifecycleProvider
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.model.ModelDownloadStatus
import com.ollitert.llm.server.data.model.ModelDownloadStatusType
import com.ollitert.llm.server.data.storage.KEY_MODEL_DOWNLOAD_ERROR_MESSAGE
import com.ollitert.llm.server.data.storage.KEY_MODEL_DOWNLOAD_RECEIVED_BYTES
import com.ollitert.llm.server.data.storage.KEY_MODEL_FROM_MODELSCOPE
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DownloadRepositoryTest {
  private val context = mockk<Context>(relaxed = true)
  private val lifecycle = mockk<OlliteRTLifecycleProvider>()
  private val workManager = mockk<WorkManager>()
  private val liveData = mockk<LiveData<WorkInfo?>>(relaxed = true)
  private val observer = slot<Observer<WorkInfo?>>()
  private lateinit var repository: DownloadRepository
  private val model = Model(name = "model", sizeInBytes = 100).apply { preProcess() }

  @Before
  fun setUp() {
    mockkObject(WorkManager.Companion)
    every { WorkManager.getInstance(context) } returns workManager
    every { lifecycle.isAppInForeground } returns true
    every { workManager.getWorkInfoByIdLiveData(any()) } returns liveData
    every { liveData.observeForever(capture(observer)) } just Runs
    repository = DownloadRepository(context, lifecycle)
  }

  @After
  fun tearDown() {
    unmockkObject(WorkManager.Companion)
  }

  private fun workInfo(
    state: WorkInfo.State,
    progress: Data = Data.EMPTY,
    output: Data = Data.EMPTY,
  ): WorkInfo {
    val info = mockk<WorkInfo>()
    every { info.state } returns state
    every { info.progress } returns progress
    every { info.outputData } returns output
    return info
  }

  @Test
  fun explicitPrimaryZeroProgressReplacesSavedMirrorProgress() {
    var status = ModelDownloadStatus(
      ModelDownloadStatusType.PARTIALLY_DOWNLOADED,
      totalBytes = 100,
      receivedBytes = 16,
      fromModelScope = true,
    )
    repository.observerWorkerProgress(UUID.randomUUID(), model) { _, updated -> status = updated }
    observer.captured.onChanged(workInfo(WorkInfo.State.RUNNING))
    assertEquals(ModelDownloadStatusType.PARTIALLY_DOWNLOADED, status.status)

    observer.captured.onChanged(workInfo(
      WorkInfo.State.RUNNING,
      progress = workDataOf(
        KEY_MODEL_DOWNLOAD_RECEIVED_BYTES to 0L,
        KEY_MODEL_FROM_MODELSCOPE to false,
      ),
    ))

    assertEquals(ModelDownloadStatusType.IN_PROGRESS, status.status)
    assertEquals(0L, status.receivedBytes)
    assertEquals(100L, status.totalBytes)
    assertFalse(status.fromModelScope)
  }

  @Test
  fun mirrorTransitionResetsProgressAndFailureRetainsItsSourceAndBytes() {
    val updates = mutableListOf<ModelDownloadStatus>()
    repository.observerWorkerProgress(UUID.randomUUID(), model) { _, updated -> updates.add(updated) }
    for ((received, mirror) in listOf(40L to false, 0L to true, 16L to true)) {
      observer.captured.onChanged(workInfo(
        WorkInfo.State.RUNNING,
        progress = workDataOf(
          KEY_MODEL_DOWNLOAD_RECEIVED_BYTES to received,
          KEY_MODEL_FROM_MODELSCOPE to mirror,
        ),
      ))
    }
    observer.captured.onChanged(workInfo(
      WorkInfo.State.FAILED,
      output = workDataOf(
        KEY_MODEL_DOWNLOAD_ERROR_MESSAGE to "both providers failed",
        KEY_MODEL_FROM_MODELSCOPE to true,
      ),
    ))

    assertEquals(listOf(40L, 0L, 16L, 16L), updates.map { it.receivedBytes })
    assertEquals(listOf(false, true, true, true), updates.map { it.fromModelScope })
    assertEquals(ModelDownloadStatusType.FAILED, updates.last().status)
    assertEquals("both providers failed", updates.last().errorMessage)
    assertEquals(100L, updates.last().totalBytes)
    verify(exactly = 1) { liveData.removeObserver(observer.captured) }
  }

  @Test
  fun cancellationClearsProviderProgressAndStopsObserving() {
    val updates = mutableListOf<ModelDownloadStatus>()
    repository.observerWorkerProgress(UUID.randomUUID(), model) { _, updated -> updates.add(updated) }
    observer.captured.onChanged(workInfo(
      WorkInfo.State.RUNNING,
      progress = workDataOf(
        KEY_MODEL_DOWNLOAD_RECEIVED_BYTES to 16L,
        KEY_MODEL_FROM_MODELSCOPE to true,
      ),
    ))
    assertTrue(updates.last().fromModelScope)

    observer.captured.onChanged(workInfo(WorkInfo.State.CANCELLED))

    assertEquals(ModelDownloadStatusType.NOT_DOWNLOADED, updates.last().status)
    assertEquals(0L, updates.last().receivedBytes)
    assertEquals(0L, updates.last().totalBytes)
    assertFalse(updates.last().fromModelScope)
    verify(exactly = 1) { liveData.removeObserver(observer.captured) }
  }
}

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

package com.ollitert.llm.server.ui.server

import android.content.Context
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.ServerStatus
import com.ollitert.llm.server.data.DEFAULT_PORT
import com.ollitert.llm.server.data.ClientIpAccessPolicy
import com.ollitert.llm.server.data.ClientIpPolicyConfig
import com.ollitert.llm.server.data.ClientIpPolicyMode
import com.ollitert.llm.server.data.ServerBindConfig
import com.ollitert.llm.server.data.ServerBindMode
import com.ollitert.llm.server.data.ServerPrefs
import com.ollitert.llm.server.ui.server.settings.STT_TRANSCRIPTION_PROMPT
import com.ollitert.llm.server.data.db.RequestLogPersistence
import com.ollitert.llm.server.service.RequestLogStore
import com.ollitert.llm.server.service.ServerMetrics
import com.ollitert.llm.server.service.ServerService
import com.ollitert.llm.server.data.FakeDataStoreRepository
import com.ollitert.llm.server.ui.floatingmonitor.FloatingMonitorPermissionCoordinator
import com.ollitert.llm.server.worker.UpdateCheckWorker
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Manual construction (no Hilt test rules) — ServerPrefs/RequestLogStore are mocked objects,
// and the ViewModel's only Android dep is Context. Hilt DI adds no value for these unit tests.
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private val mockContext: Context = mockk(relaxed = true)
  private val mockPersistence: RequestLogPersistence = mockk(relaxed = true)
  private lateinit var floatingMonitorPermissionCoordinator: FloatingMonitorPermissionCoordinator
  private lateinit var vm: SettingsViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(ServerPrefs)
    mockkObject(RequestLogStore)
    mockkObject(ServerService)
    mockkObject(ServerMetrics)
    mockkObject(UpdateCheckWorker)

    every { ServerPrefs.getPort(any()) } returns DEFAULT_PORT
    every { ServerPrefs.getServerBindConfig(any()) } returns ServerBindConfig()
    every { ServerPrefs.getClientIpPolicyConfig(any()) } returns ClientIpPolicyConfig()
    every { ServerPrefs.getBearerToken(any()) } returns ""
    every { ServerPrefs.getHfToken(any()) } returns ""
    every { ServerPrefs.isKeepScreenOn(any()) } returns true
    every { ServerPrefs.isAutoStartOnBoot(any()) } returns false
    every { ServerPrefs.isFloatingMonitorEnabled(any()) } returns false
    every { ServerPrefs.isKeepAliveEnabled(any()) } returns false
    every { ServerPrefs.getKeepAliveMinutes(any()) } returns 30
    every { ServerPrefs.isWarmupEnabled(any()) } returns true
    every { ServerPrefs.isUpdateCheckEnabled(any()) } returns true
    every { ServerPrefs.getUpdateCheckIntervalHours(any()) } returns 24
    every { ServerPrefs.isLogPersistenceEnabled(any()) } returns false
    every { ServerPrefs.getLogMaxEntries(any()) } returns 500
    every { ServerPrefs.getLogAutoDeleteMinutes(any()) } returns 0L
    every { ServerPrefs.isVerboseDebugEnabled(any()) } returns false
    every { ServerPrefs.isAutoExpandLogs(any()) } returns false
    every { ServerPrefs.isStreamLogsPreview(any()) } returns true
    every { ServerPrefs.isCompactImageData(any()) } returns true
    every { ServerPrefs.isHideHealthLogs(any()) } returns false
    every { ServerPrefs.isClearLogsOnStop(any()) } returns false
    every { ServerPrefs.isConfirmClearLogs(any()) } returns true
    every { ServerPrefs.isKeepPartialResponse(any()) } returns true
    every { ServerPrefs.isEagerVisionInit(any()) } returns false
    every { ServerPrefs.isCustomPromptsEnabled(any()) } returns false
    every { ServerPrefs.isAutoTruncateHistory(any()) } returns true
    every { ServerPrefs.isAutoTrimPrompts(any()) } returns false
    every { ServerPrefs.isIgnoreClientSamplerParams(any()) } returns false
    every { ServerPrefs.getDefaultModelName(any()) } returns null
    every { ServerPrefs.getCorsAllowedOrigins(any()) } returns "*"
    every { ServerPrefs.isShowRequestTypes(any()) } returns false
    every { ServerPrefs.isShowAdvancedMetrics(any()) } returns false
    every { ServerPrefs.isSttTranscriptionPromptEnabled(any()) } returns false
    every { ServerPrefs.getSttTranscriptionPromptText(any()) } returns ""
    every { ServerPrefs.isNotifShowRequestCount(any()) } returns false
    every { ServerPrefs.isResolveClientHostnames(any()) } returns false
    every { ServerPrefs.getTimeoutChatCompletions(any()) } returns 120L
    every { ServerPrefs.getTimeoutResponses(any()) } returns 90L
    every { ServerPrefs.getTimeoutStreaming(any()) } returns 90L
    every { ServerPrefs.getTimeoutBlocking(any()) } returns 30L
    every { ServerPrefs.getTimeoutWarmup(any()) } returns 10L
    every { ServerPrefs.getTimeoutKeepAliveRecheckSeconds(any()) } returns 30L
    every { ServerPrefs.getTimeoutCleanupAwait(any()) } returns 15L

    every { ServerService.resetKeepAliveTimer(any()) } returns Unit
    every { ServerService.updateClientIpAccessPolicy(any()) } returns Unit

    every { ServerPrefs.setBearerToken(any(), any()) } returns Unit
    every { ServerPrefs.save(any(), any()) } returns Unit
    every { ServerPrefs.setServerBindConfig(any(), any()) } returns Unit
    every { ServerPrefs.setClientIpPolicyConfig(any(), any()) } returns Unit
    every { ServerPrefs.setKeepScreenOn(any(), any()) } returns Unit
    every { ServerPrefs.setTimeoutChatCompletions(any(), any()) } returns Unit
    every { ServerPrefs.setTimeoutResponses(any(), any()) } returns Unit
    every { ServerPrefs.setTimeoutStreaming(any(), any()) } returns Unit
    every { ServerPrefs.setTimeoutBlocking(any(), any()) } returns Unit
    every { ServerPrefs.setTimeoutWarmup(any(), any()) } returns Unit
    every { ServerPrefs.setTimeoutKeepAliveRecheckSeconds(any(), any()) } returns Unit
    every { ServerPrefs.setTimeoutCleanupAwait(any(), any()) } returns Unit
    every { ServerPrefs.setFloatingMonitorEnabled(any(), any()) } returns Unit
    every { ServerPrefs.resetFloatingMonitorPosition(any()) } returns Unit
    every { ServerPrefs.resetToDefaults(any()) } returns Unit
    every { ServerPrefs.dumpToLogcat(any()) } returns Unit

    every { RequestLogStore.entries } returns mockk { every { value } returns emptyList() }
    every { RequestLogStore.addEvent(any(), any(), any(), any(), any()) } returns Unit
    every { RequestLogStore.clear() } returns Unit

    every { ServerMetrics.status } returns mockk { every { value } returns ServerStatus.STOPPED }
    every { mockContext.getString(R.string.settings_floating_monitor) } returns "Floating monitor"
    every { mockContext.getString(R.string.settings_floating_monitor_desc) } returns
      "Show server status as an overlay. Manage overlay permission and reset position."

    every { UpdateCheckWorker.scheduleUpdateCheck(any()) } returns Unit
    every { UpdateCheckWorker.cancelUpdateCheck(any()) } returns Unit

    floatingMonitorPermissionCoordinator = FloatingMonitorPermissionCoordinator()
    vm = SettingsViewModel(
      mockContext,
      mockPersistence,
      FakeDataStoreRepository(),
      floatingMonitorPermissionCoordinator,
    )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkAll()
  }

  // --- Change Detection ---

  @Test
  fun noUnsavedChangesInitially() {
    assertFalse(vm.hasUnsavedChanges)
  }

  @Test
  fun portChangeDetected() {
    vm.portText = "9090"
    assertTrue(vm.hasUnsavedChanges)
  }

  @Test
  fun portRevertClearsChange() {
    vm.portText = "9090"
    vm.portText = DEFAULT_PORT.toString()
    assertFalse(vm.hasUnsavedChanges)
  }

  @Test
  fun toggleChangeDetected() {
    vm.keepScreenOnEntry.update(false)
    assertTrue(vm.hasUnsavedChanges)
  }

  @Test
  fun toggleRevertClearsChange() {
    vm.keepScreenOnEntry.update(false)
    vm.keepScreenOnEntry.update(true)
    assertFalse(vm.hasUnsavedChanges)
  }

  @Test
  fun bearerTokenChangeDetected() {
    vm.bearerEnabledEntry.update(true)
    vm.bearerTokenEntry.update("secret")
    assertTrue(vm.hasUnsavedChanges)
  }

  @Test
  fun bearerTokenDisabledMeansEffectiveEmpty() {
    vm.bearerEnabledEntry.update(false)
    vm.bearerTokenEntry.update("secret")
    assertFalse(vm.hasUnsavedChanges)
  }

  @Test
  fun bearerEnabledWithBlankTokenPersistsBlankToken() {
    vm.bearerEnabledEntry.update(true)
    vm.bearerTokenEntry.update("   ")
    every { ServerPrefs.isLogPersistenceEnabled(any()) } returns false
    vm.save(ServerStatus.STOPPED)
    // When enabled with blank token, effectiveBearerToken is the blank string itself,
    // which the server treats as "auth disabled" (isBlank() check in requireAuth).
    verify(exactly = 1) { ServerPrefs.setBearerToken(mockContext, "   ") }
  }

  @Test
  fun bearerEnabledWithEmptyTokenShowsNoUnsavedChanges() {
    // Starting state: bearer disabled (token was empty), so bearerEnabledEntry.saved = false
    // Enabling the toggle with an empty token shouldn't count as a meaningful change
    // because effectiveBearerToken = "" (same as saved)
    vm.bearerEnabledEntry.update(true)
    vm.bearerTokenEntry.update("")
    assertFalse(vm.hasUnsavedChanges)
  }

  // --- Dependency-Based Enabling ---

  @Test
  fun startOnBootDisabledWhenNoDefaultModel() {
    assertFalse(vm.isSettingEnabled("start_on_boot"))
  }

  @Test
  fun startOnBootEnabledWhenDefaultModelSet() {
    vm.defaultModelEntry.update("gemma-3-4b")
    assertTrue(vm.isSettingEnabled("start_on_boot"))
  }

  @Test
  fun keepAliveTimeoutDisabledWhenKeepAliveOff() {
    assertFalse(vm.isSettingEnabled("keep_alive_timeout"))
  }

  @Test
  fun keepAliveTimeoutEnabledWhenKeepAliveOn() {
    vm.keepAliveEnabledEntry.update(true)
    assertTrue(vm.isSettingEnabled("keep_alive_timeout"))
  }

  @Test
  fun logSubSettingsDisabledWhenPersistenceOff() {
    assertFalse(vm.isSettingEnabled("log_max_entries"))
    assertFalse(vm.isSettingEnabled("log_auto_delete"))
    assertFalse(vm.isSettingEnabled("clear_all_logs"))
  }

  @Test
  fun logSubSettingsEnabledWhenPersistenceOn() {
    vm.logPersistenceEnabledEntry.update(true)
    assertTrue(vm.isSettingEnabled("log_max_entries"))
    assertTrue(vm.isSettingEnabled("log_auto_delete"))
    assertTrue(vm.isSettingEnabled("clear_all_logs"))
  }

  @Test
  fun customBindAddressEnabledOnlyForCustomMode() {
    assertFalse(vm.isSettingEnabled("custom_bind_address"))
    vm.serverBindModeEntry.update(ServerBindMode.CUSTOM.preferenceValue)
    assertTrue(vm.isSettingEnabled("custom_bind_address"))
  }

  @Test
  fun clientIpRulesEnabledOnlyForActivePolicy() {
    assertFalse(vm.isSettingEnabled("client_ip_rules"))
    vm.clientIpPolicyModeEntry.update(ClientIpPolicyMode.ALLOW_ONLY.preferenceValue)
    assertTrue(vm.isSettingEnabled("client_ip_rules"))
  }

  // --- Alpha ---

  @Test
  fun settingAlphaFullWhenEnabled() {
    vm.keepAliveEnabledEntry.update(true)
    assertEquals(1f, vm.settingAlpha("keep_alive_timeout"))
  }

  @Test
  fun settingAlphaDimmedWhenDisabled() {
    assertEquals(0.4f, vm.settingAlpha("keep_alive_timeout"))
  }

  // --- Search Filtering ---

  @Test
  fun blankQueryShowsAllSettings() {
    vm.searchQuery = ""
    assertTrue(vm.settingVisible("host_port"))
    assertTrue(vm.settingVisible("keep_screen_awake"))
  }

  @Test
  fun blankQueryShowsAllCards() {
    vm.searchQuery = ""
    assertTrue(vm.cardVisible("GENERAL"))
    assertTrue(vm.cardVisible("SERVER_CONFIG"))
  }

  // --- Save ---

  @Test
  fun saveSuccessWhenNoChanges() {
    val result = vm.save(ServerStatus.STOPPED)
    assertTrue(result is SettingsViewModel.SaveResult.Success)
  }

  @Test
  fun saveSuccessWithChangesServerStopped() {
    vm.keepScreenOnEntry.update(false)
    every { ServerPrefs.isLogPersistenceEnabled(any()) } returns false
    val result = vm.save(ServerStatus.STOPPED)
    assertTrue(result is SettingsViewModel.SaveResult.Success)
  }

  @Test
  fun saveNeedsRestartWhenPortChangedServerRunning() {
    vm.portText = "9090"
    every { ServerMetrics.status } returns mockk { every { value } returns ServerStatus.RUNNING }
    val result = vm.save(ServerStatus.RUNNING)
    assertTrue(result is SettingsViewModel.SaveResult.NeedsRestart)
    verify(exactly = 1) { ServerPrefs.save(mockContext, 9090) }
  }

  @Test
  fun saveNeedsRestartWhenBindModeChangesServerRunning() {
    vm.serverBindModeEntry.update(ServerBindMode.LOOPBACK.preferenceValue)
    every { ServerMetrics.status } returns mockk { every { value } returns ServerStatus.RUNNING }

    val result = vm.save(ServerStatus.RUNNING)

    assertTrue(result is SettingsViewModel.SaveResult.NeedsRestart)
    verify(exactly = 1) {
      ServerPrefs.setServerBindConfig(mockContext, ServerBindConfig(ServerBindMode.LOOPBACK, ""))
    }
  }

  @Test
  fun customBindAddressRejectsHostnames() {
    vm.serverBindModeEntry.update(ServerBindMode.CUSTOM.preferenceValue)
    vm.customBindAddressEntry.update("phone.local")

    val result = vm.save(ServerStatus.STOPPED)

    assertTrue(result is SettingsViewModel.SaveResult.ValidationError)
    assertTrue(vm.hasError("custom_bind_address"))
  }

  @Test
  fun customBindAddressAcceptsNumericIp() {
    vm.serverBindModeEntry.update(ServerBindMode.CUSTOM.preferenceValue)
    vm.customBindAddressEntry.update("192.168.1.50")

    val result = vm.save(ServerStatus.STOPPED)

    assertTrue(result is SettingsViewModel.SaveResult.Success)
    verify(exactly = 1) {
      ServerPrefs.setServerBindConfig(
        mockContext,
        ServerBindConfig(ServerBindMode.CUSTOM, "192.168.1.50"),
      )
    }
  }

  @Test
  fun clientIpPolicyAppliesLiveWithoutRestart() {
    val policySlot = slot<ClientIpAccessPolicy>()
    vm.clientIpPolicyModeEntry.update(ClientIpPolicyMode.ALLOW_ONLY.preferenceValue)
    vm.clientIpRulesEntry.update("192.168.1.0/24")
    every { ServerMetrics.status } returns mockk { every { value } returns ServerStatus.RUNNING }

    val result = vm.save(ServerStatus.RUNNING)

    assertTrue(result is SettingsViewModel.SaveResult.Success)
    verify(exactly = 1) {
      ServerPrefs.setClientIpPolicyConfig(
        mockContext,
        ClientIpPolicyConfig(ClientIpPolicyMode.ALLOW_ONLY, "192.168.1.0/24"),
      )
    }
    verify(exactly = 1) { ServerService.updateClientIpAccessPolicy(capture(policySlot)) }
    assertTrue(policySlot.captured.allows("192.168.1.42"))
    assertFalse(policySlot.captured.allows("10.0.0.1"))
  }

  @Test
  fun activeClientIpPolicyRequiresRules() {
    vm.clientIpPolicyModeEntry.update(ClientIpPolicyMode.BLOCK_LISTED.preferenceValue)
    vm.clientIpRulesEntry.update("")

    val result = vm.save(ServerStatus.STOPPED)

    assertTrue(result is SettingsViewModel.SaveResult.ValidationError)
    assertTrue(vm.hasError("client_ip_rules"))
  }

  @Test
  fun settingsLogDoesNotExposeClientIpRules() {
    vm.clientIpPolicyModeEntry.update(ClientIpPolicyMode.BLOCK_LISTED.preferenceValue)
    vm.clientIpRulesEntry.update("203.0.113.25")

    vm.save(ServerStatus.STOPPED)

    verify(exactly = 1) {
      RequestLogStore.addEvent(
        any(),
        any(),
        any(),
        any(),
        match { body ->
          !body.orEmpty().contains("203.0.113.25") &&
            body.orEmpty().contains("Client IP Rules")
        },
      )
    }
  }

  @Test
  fun saveAdvancesBaselines() {
    vm.keepScreenOnEntry.update(false)
    assertTrue(vm.hasUnsavedChanges)
    every { ServerPrefs.isLogPersistenceEnabled(any()) } returns false
    vm.save(ServerStatus.STOPPED)
    assertFalse(vm.hasUnsavedChanges)
  }

  @Test
  fun saveCallsPersistenceUpdateMaxEntries() {
    every { ServerPrefs.isLogPersistenceEnabled(any()) } returns false
    vm.save(ServerStatus.STOPPED)
    verify(exactly = 1) { mockPersistence.updateMaxEntries() }
  }

  @Test
  fun savePersistsToggleValueToSharedPreferences() {
    vm.keepScreenOnEntry.update(false)
    every { ServerPrefs.isLogPersistenceEnabled(any()) } returns false
    vm.save(ServerStatus.STOPPED)
    verify(exactly = 1) { ServerPrefs.setKeepScreenOn(mockContext, false) }
  }

  @Test
  fun savePersistsFloatingMonitorIntentLiveWithoutRestart() {
    vm.floatingMonitorEntry.update(true)
    every { ServerPrefs.isLogPersistenceEnabled(any()) } returns false

    val result = vm.save(ServerStatus.RUNNING)

    assertTrue(result is SettingsViewModel.SaveResult.Success)
    verify(exactly = 1) { ServerPrefs.setFloatingMonitorEnabled(mockContext, true) }
  }

  @Test
  fun unsavedFloatingMonitorDraftDoesNotExposePermissionAction() {
    vm.floatingMonitorEntry.update(true)

    assertFalse(vm.shouldShowFloatingMonitorPermissionAction(overlayPermissionGranted = false))

    every { ServerPrefs.isLogPersistenceEnabled(any()) } returns false
    vm.save(ServerStatus.STOPPED)

    assertTrue(vm.shouldShowFloatingMonitorPermissionAction(overlayPermissionGranted = false))
  }

  @Test
  fun permissionResultUpdatesObservationAndEndsSuppression() {
    vm.beginFloatingMonitorPermissionFlow()
    assertTrue(floatingMonitorPermissionCoordinator.permissionFlowInProgress.value)

    vm.endFloatingMonitorPermissionFlow(permissionGranted = true)

    assertTrue(floatingMonitorPermissionCoordinator.overlayPermissionGranted.value)
    assertFalse(floatingMonitorPermissionCoordinator.permissionFlowInProgress.value)
  }

  @Test
  fun permissionObservationExposedByViewModelReflectsGrantAndRevocation() {
    assertFalse(vm.floatingMonitorPermissionGranted.value)

    floatingMonitorPermissionCoordinator.updateObservedPermission(true)
    assertTrue(vm.floatingMonitorPermissionGranted.value)

    floatingMonitorPermissionCoordinator.updateObservedPermission(false)
    assertFalse(vm.floatingMonitorPermissionGranted.value)
  }

  @Test
  fun floatingMonitorSearchFindsPermissionAndPositionTerms() {
    vm.searchQuery = "permission"
    assertTrue(vm.settingVisible("floating_monitor"))

    vm.searchQuery = "position"
    assertTrue(vm.settingVisible("floating_monitor"))
  }

  @Test
  fun savePersistsBearerTokenToSharedPreferences() {
    vm.bearerEnabledEntry.update(true)
    vm.bearerTokenEntry.update("my-secret")
    every { ServerPrefs.isLogPersistenceEnabled(any()) } returns false
    vm.save(ServerStatus.STOPPED)
    verify(exactly = 1) { ServerPrefs.setBearerToken(mockContext, "my-secret") }
  }

  // --- trySave Trim Warning ---

  @Test
  fun trySaveWarnsWhenMaxEntriesReducedBelowCurrent() {
    every { RequestLogStore.entries } returns mockk { every { value } returns List(100) { mockk() } }
    vm.logMaxEntriesEntry.update(50)
    val result = vm.trySave(ServerStatus.STOPPED)
    assertTrue(result is SettingsViewModel.SaveResult.NeedsTrimConfirmation)
    val trim = result as SettingsViewModel.SaveResult.NeedsTrimConfirmation
    assertEquals(100, trim.currentCount)
    assertEquals(50, trim.newMax)
  }

  @Test
  fun trySaveSkipsWarningWhenMaxNotChanged() {
    every { RequestLogStore.entries } returns mockk { every { value } returns List(100) { mockk() } }
    every { ServerPrefs.isLogPersistenceEnabled(any()) } returns false
    val result = vm.trySave(ServerStatus.STOPPED)
    assertTrue(result is SettingsViewModel.SaveResult.Success)
  }

  // --- Reset ---

  @Test
  fun resetFloatingMonitorPositionOnlyClearsPosition() {
    vm.resetFloatingMonitorPosition()

    verify(exactly = 1) { ServerPrefs.resetFloatingMonitorPosition(mockContext) }
    verify(exactly = 0) { ServerPrefs.resetToDefaults(any()) }
  }

  @Test
  fun resetToDefaultsCallsPrefsReset() {
    vm.resetToDefaults()
    verify(exactly = 1) { ServerPrefs.resetToDefaults(mockContext) }
  }

  @Test
  fun resetToDefaultsTurnsFloatingMonitorOff() {
    vm.floatingMonitorEntry.update(true)

    vm.resetToDefaults()

    assertFalse(vm.floatingMonitorEntry.current)
    assertFalse(vm.floatingMonitorEntry.saved)
  }

  @Test
  fun resetToDefaultsClearsValidationErrors() {
    vm.validationErrors["host_port"] = "bad"
    vm.resetToDefaults()
    assertTrue(vm.validationErrors.isEmpty())
  }

  @Test
  fun resetToDefaultsSyncsPersistence() {
    vm.resetToDefaults()
    verify(exactly = 1) { mockPersistence.updateMaxEntries() }
    verify(exactly = 1) { mockPersistence.schedulePruning() }
    verify(exactly = 1) { mockPersistence.clearPersistedLogs() }
  }

  @Test
  fun resetToDefaultsResetsPortText() {
    vm.portText = "9090"
    vm.resetToDefaults()
    assertEquals(vm.portEntry.saved.toString(), vm.portText)
  }

  // --- Default Consistency ---

  @Test
  fun haSTTTranscriptionPromptDefaultIsTrue() {
    assertTrue(STT_TRANSCRIPTION_PROMPT.default)
  }

  // --- Clear Logs ---

  @Test
  fun clearPersistedLogsClearsStoreAndDatabase() {
    vm.clearPersistedLogs()
    verify(exactly = 1) { RequestLogStore.clear() }
    verify(exactly = 1) { mockPersistence.clearPersistedLogs() }
  }
}

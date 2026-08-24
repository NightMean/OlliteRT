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

package com.ollitert.llm.server.service.inference

import com.ollitert.llm.server.common.ServerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ServerLifecycleStateMachineTest {

  private lateinit var stateMachine: ServerLifecycleStateMachine

  @Before
  fun setUp() {
    stateMachine = ServerLifecycleStateMachine()
  }

  @Test
  fun initialStateIsStopped() {
    val state = stateMachine.state.value
    assertEquals(ServerLifecycleState.Stopped, state)
    assertEquals(ServerStatus.STOPPED, state.status)
  }

  @Test
  fun startRequestedTransitionsToStarting() {
    val state = stateMachine.process(
      ServerLifecycleEvent.StartRequested(
        port = 8000,
        modelName = "gemma-2b-it",
        timestampMs = 1000L,
      )
    )

    assertTrue(state is ServerLifecycleState.Starting)
    val starting = state as ServerLifecycleState.Starting
    assertEquals(8000, starting.port)
    assertEquals("gemma-2b-it", starting.modelName)
    assertEquals(1000L, starting.loadingStartedAtMs)
    assertEquals(ServerStatus.LOADING, state.status)
  }

  @Test
  fun runningEnteredTransitionsToRunning() {
    stateMachine.process(
      ServerLifecycleEvent.StartRequested(
        port = 8000,
        modelName = "gemma-2b-it",
        timestampMs = 1000L,
      )
    )

    val state = stateMachine.process(
      ServerLifecycleEvent.RunningEntered(
        bindAddress = "192.168.1.5:8000",
        isLoopbackOnly = false,
        timestampMs = 2000L,
      )
    )

    assertTrue(state is ServerLifecycleState.Running)
    val running = state as ServerLifecycleState.Running
    assertEquals(8000, running.port)
    assertEquals("gemma-2b-it", running.modelName)
    assertEquals("192.168.1.5:8000", running.bindAddress)
    assertFalse(running.isLoopbackOnly)
    assertEquals(2000L, running.startedAtMs)
    assertFalse(running.isInferring)
    assertFalse(running.isIdleUnloaded)
    assertEquals(ServerStatus.RUNNING, state.status)
  }

  @Test
  fun inferenceLifecycleWithinRunning() {
    stateMachine.process(ServerLifecycleEvent.StartRequested(8000, "gemma", 1000L))
    stateMachine.process(ServerLifecycleEvent.RunningEntered("localhost:8000", true, 2000L))

    val inferringState = stateMachine.process(ServerLifecycleEvent.InferenceStarted)
    assertTrue((inferringState as ServerLifecycleState.Running).isInferring)

    val completedState = stateMachine.process(ServerLifecycleEvent.InferenceCompleted)
    assertFalse((completedState as ServerLifecycleState.Running).isInferring)
  }

  @Test
  fun idleUnloadAndReloadLifecycleWithinRunning() {
    stateMachine.process(ServerLifecycleEvent.StartRequested(8000, "gemma", 1000L))
    stateMachine.process(ServerLifecycleEvent.RunningEntered("localhost:8000", true, 2000L))

    val unloadedState = stateMachine.process(ServerLifecycleEvent.ModelIdleUnloaded)
    assertTrue((unloadedState as ServerLifecycleState.Running).isIdleUnloaded)

    val reloadedState = stateMachine.process(ServerLifecycleEvent.ModelReloadedFromIdle)
    assertFalse((reloadedState as ServerLifecycleState.Running).isIdleUnloaded)
  }

  @Test
  fun errorPreservesModelName() {
    stateMachine.process(ServerLifecycleEvent.StartRequested(8000, "gemma-2b-it", 1000L))

    val errorState = stateMachine.process(ServerLifecycleEvent.ErrorOccurred("Out of memory"))
    assertTrue(errorState is ServerLifecycleState.Error)
    val error = errorState as ServerLifecycleState.Error
    assertEquals("Out of memory", error.message)
    assertEquals("gemma-2b-it", error.modelName)
    assertEquals(ServerStatus.ERROR, errorState.status)
  }

  @Test
  fun clearErrorIfModelMatches() {
    stateMachine.process(ServerLifecycleEvent.StartRequested(8000, "gemma-2b-it", 1000L))
    stateMachine.process(ServerLifecycleEvent.ErrorOccurred("Model file corrupt"))

    val stateAfterClear = stateMachine.process(ServerLifecycleEvent.ClearErrorIfModel("gemma-2b-it"))
    assertEquals(ServerLifecycleState.Stopped, stateAfterClear)
  }

  @Test
  fun clearErrorDoesNotClearDifferentModel() {
    stateMachine.process(ServerLifecycleEvent.StartRequested(8000, "gemma-2b-it", 1000L))
    stateMachine.process(ServerLifecycleEvent.ErrorOccurred("Model file corrupt"))

    val stateAfterClear = stateMachine.process(ServerLifecycleEvent.ClearErrorIfModel("other-model"))
    assertTrue(stateAfterClear is ServerLifecycleState.Error)
  }

  @Test
  fun stopRequestedResetsToStopped() {
    stateMachine.process(ServerLifecycleEvent.StartRequested(8000, "gemma", 1000L))
    stateMachine.process(ServerLifecycleEvent.RunningEntered("localhost:8000", true, 2000L))

    val stoppedState = stateMachine.process(ServerLifecycleEvent.StopRequested)
    assertEquals(ServerLifecycleState.Stopped, stoppedState)
  }

  @Test
  fun serverMetricsLifecycleIntegration() {
    ServerMetrics.resetForTesting()
    assertEquals(ServerLifecycleState.Stopped, ServerMetrics.lifecycleState.value)

    ServerMetrics.onServerStarting(8080, "phi-2")
    assertEquals(ServerStatus.LOADING, ServerMetrics.status.value)
    val startingState = ServerMetrics.lifecycleState.value as ServerLifecycleState.Starting
    assertEquals("phi-2", startingState.modelName)
    assertEquals(8080, startingState.port)

    ServerMetrics.onServerRunning("0.0.0.0:8080", false)
    assertEquals(ServerStatus.RUNNING, ServerMetrics.status.value)
    val runningState = ServerMetrics.lifecycleState.value as ServerLifecycleState.Running
    assertEquals("0.0.0.0:8080", runningState.bindAddress)
    assertFalse(runningState.isLoopbackOnly)

    ServerMetrics.onServerStopped()
    assertEquals(ServerStatus.STOPPED, ServerMetrics.status.value)
    assertEquals(ServerLifecycleState.Stopped, ServerMetrics.lifecycleState.value)
  }
}

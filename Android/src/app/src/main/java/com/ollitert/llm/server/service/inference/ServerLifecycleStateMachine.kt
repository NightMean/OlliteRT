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

import androidx.compose.runtime.Immutable
import com.ollitert.llm.server.common.ServerStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Deterministic finite states modeling the server runtime lifecycle.
 */
@Immutable
sealed interface ServerLifecycleState {

  data object Stopped : ServerLifecycleState

  data class Starting(
    val port: Int,
    val modelName: String?,
    val loadingStartedAtMs: Long,
  ) : ServerLifecycleState

  data class Running(
    val port: Int,
    val modelName: String?,
    val bindAddress: String,
    val isLoopbackOnly: Boolean,
    val startedAtMs: Long,
    val isInferring: Boolean = false,
    val isIdleUnloaded: Boolean = false,
  ) : ServerLifecycleState

  data class Error(
    val message: String?,
    val modelName: String? = null,
  ) : ServerLifecycleState

  val status: ServerStatus
    get() = when (this) {
      is Stopped -> ServerStatus.STOPPED
      is Starting -> ServerStatus.LOADING
      is Running -> ServerStatus.RUNNING
      is Error -> ServerStatus.ERROR
    }
}

/**
 * Pure domain events driving server lifecycle state transitions.
 */
sealed interface ServerLifecycleEvent {
  data class StartRequested(
    val port: Int,
    val modelName: String?,
    val timestampMs: Long = System.currentTimeMillis(),
  ) : ServerLifecycleEvent

  data class RunningEntered(
    val bindAddress: String?,
    val isLoopbackOnly: Boolean,
    val timestampMs: Long = System.currentTimeMillis(),
  ) : ServerLifecycleEvent

  data object InferenceStarted : ServerLifecycleEvent

  data object InferenceCompleted : ServerLifecycleEvent

  data object ModelIdleUnloaded : ServerLifecycleEvent

  data object ModelReloadedFromIdle : ServerLifecycleEvent

  data class ErrorOccurred(
    val message: String?,
  ) : ServerLifecycleEvent

  data object StopRequested : ServerLifecycleEvent

  data class ClearErrorIfModel(
    val modelName: String,
  ) : ServerLifecycleEvent
}

/**
 * Pure reducer function governing server lifecycle transitions.
 */
object ServerLifecycleReducer {

  fun reduce(currentState: ServerLifecycleState, event: ServerLifecycleEvent): ServerLifecycleState {
    return when (event) {
      is ServerLifecycleEvent.StartRequested -> {
        ServerLifecycleState.Starting(
          port = event.port,
          modelName = event.modelName,
          loadingStartedAtMs = event.timestampMs,
        )
      }
      is ServerLifecycleEvent.RunningEntered -> {
        when (currentState) {
          is ServerLifecycleState.Starting -> {
            ServerLifecycleState.Running(
              port = currentState.port,
              modelName = currentState.modelName,
              bindAddress = event.bindAddress ?: "localhost",
              isLoopbackOnly = event.isLoopbackOnly,
              startedAtMs = event.timestampMs,
              isInferring = false,
              isIdleUnloaded = false,
            )
          }
          is ServerLifecycleState.Running -> {
            currentState.copy(
              bindAddress = event.bindAddress ?: currentState.bindAddress,
              isLoopbackOnly = event.isLoopbackOnly,
            )
          }
          is ServerLifecycleState.Stopped, is ServerLifecycleState.Error -> {
            ServerLifecycleState.Running(
              port = 8000,
              modelName = (currentState as? ServerLifecycleState.Error)?.modelName,
              bindAddress = event.bindAddress ?: "localhost",
              isLoopbackOnly = event.isLoopbackOnly,
              startedAtMs = event.timestampMs,
              isInferring = false,
              isIdleUnloaded = false,
            )
          }
        }
      }
      is ServerLifecycleEvent.InferenceStarted -> {
        if (currentState is ServerLifecycleState.Running) {
          currentState.copy(isInferring = true)
        } else {
          currentState
        }
      }
      is ServerLifecycleEvent.InferenceCompleted -> {
        if (currentState is ServerLifecycleState.Running) {
          currentState.copy(isInferring = false)
        } else {
          currentState
        }
      }
      is ServerLifecycleEvent.ModelIdleUnloaded -> {
        if (currentState is ServerLifecycleState.Running) {
          currentState.copy(isIdleUnloaded = true)
        } else {
          currentState
        }
      }
      is ServerLifecycleEvent.ModelReloadedFromIdle -> {
        if (currentState is ServerLifecycleState.Running) {
          currentState.copy(isIdleUnloaded = false)
        } else {
          currentState
        }
      }
      is ServerLifecycleEvent.ErrorOccurred -> {
        val model = when (currentState) {
          is ServerLifecycleState.Starting -> currentState.modelName
          is ServerLifecycleState.Running -> currentState.modelName
          is ServerLifecycleState.Error -> currentState.modelName
          is ServerLifecycleState.Stopped -> null
        }
        ServerLifecycleState.Error(message = event.message, modelName = model)
      }
      is ServerLifecycleEvent.StopRequested -> {
        ServerLifecycleState.Stopped
      }
      is ServerLifecycleEvent.ClearErrorIfModel -> {
        if (currentState is ServerLifecycleState.Error && currentState.modelName == event.modelName) {
          ServerLifecycleState.Stopped
        } else {
          currentState
        }
      }
    }
  }
}

/**
 * Thread-safe state machine managing the active [ServerLifecycleState].
 */
class ServerLifecycleStateMachine(
  initialState: ServerLifecycleState = ServerLifecycleState.Stopped,
) {
  private val _state = MutableStateFlow(initialState)
  val state: StateFlow<ServerLifecycleState> = _state.asStateFlow()

  @Synchronized
  fun process(event: ServerLifecycleEvent): ServerLifecycleState {
    val nextState = ServerLifecycleReducer.reduce(_state.value, event)
    _state.value = nextState
    return nextState
  }

  @Synchronized
  fun reset(state: ServerLifecycleState = ServerLifecycleState.Stopped) {
    _state.value = state
  }
}

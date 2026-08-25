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
package com.ollitert.llm.server.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.ollitert.llm.server.data.prefs.ClientIpAccessPolicy
import com.ollitert.llm.server.data.prefs.ServerPrefs
import com.ollitert.llm.server.common.ServerMetrics

/**
 * Intent-launching entry points for [ServerService], split out of the companion
 * object to keep the service class focused on lifecycle orchestration.
 * These are extension functions on ServerService.Companion so every existing
 * `ServerService.start(...)`-style call site keeps compiling unchanged.
 */
fun ServerService.Companion.start(context: Context, port: Int = DEFAULT_PORT, modelName: String? = null, source: String? = null): Boolean {
      val intent = Intent(context, ServerService::class.java).apply {
        putExtra(EXTRA_PORT, port)
        if (modelName != null) putExtra(EXTRA_MODEL_NAME, modelName)
        if (source != null) putExtra(EXTRA_START_SOURCE, source)
      }
      return try {
        context.startForegroundService(intent)
        true
      } catch (e: Exception) {
        Log.e(TAG, "Failed to start service", e)
        false
      }
    }

fun ServerService.Companion.stop(context: Context) {
      try {
        context.stopService(Intent(context, ServerService::class.java))
      } catch (e: Exception) {
        Log.w(TAG, "Failed to stop service", e)
      }
    }

fun ServerService.Companion.queueReloadAfterLoad(port: Int, modelName: String, configValues: Map<String, Any>?) {
      pendingReloadAfterLoad.set(ServerService.Companion.PendingReload(port, modelName, configValues))
    }

fun ServerService.Companion.reload(context: Context, port: Int = DEFAULT_PORT, modelName: String? = null, configValues: Map<String, Any>? = null): Boolean {
      pendingConfigOverrides.set(configValues)
      val intent = Intent(context, ServerService::class.java).apply {
        action = ACTION_RELOAD
        putExtra(EXTRA_PORT, port)
        if (modelName != null) putExtra(EXTRA_MODEL_NAME, modelName)
      }
      return try {
        context.startForegroundService(intent)
        true
      } catch (e: Exception) {
        Log.e(TAG, "Failed to reload service", e)
        false
      }
    }

fun ServerService.Companion.resetKeepAliveTimer(context: Context) {
      try {
        context.startService(
          Intent(context, ServerService::class.java).apply { action = ACTION_RESET_KEEP_ALIVE }
        )
      } catch (e: Exception) {
        Log.w(TAG, "Failed to reset keep-alive timer — service may not be running", e)
      }
    }

fun ServerService.Companion.updateClientIpAccessPolicy(policy: ClientIpAccessPolicy) {
      activeInstance?.server?.updateClientIpAccessPolicy(policy)
    }

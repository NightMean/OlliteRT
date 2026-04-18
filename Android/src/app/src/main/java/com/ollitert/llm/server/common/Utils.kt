/*
 * Copyright 2025 Google LLC
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

package com.ollitert.llm.server.common

import android.os.Build
import android.util.Log
import com.google.gson.Gson
import java.net.HttpURLConnection
import com.ollitert.llm.server.data.HTTP_CONNECT_TIMEOUT_MS
import com.ollitert.llm.server.data.HTTP_READ_TIMEOUT_MS
import java.net.URL

const val LOCAL_URL_BASE = "https://appassets.androidplatform.net"

fun cleanUpMediapipeTaskErrorMessage(message: String): String {
  val index = message.indexOf("=== Source Location Trace")
  if (index >= 0) {
    return message.substring(0, index)
  }
  return message
}

inline fun <reified T> getJsonResponse(url: String): JsonObjAndTextContent<T>? {
  var connection: HttpURLConnection? = null
  try {
    connection = URL(url).openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = HTTP_CONNECT_TIMEOUT_MS
    connection.readTimeout = HTTP_READ_TIMEOUT_MS
    connection.connect()

    val responseCode = connection.responseCode
    if (responseCode == HttpURLConnection.HTTP_OK) {
      val response = connection.inputStream.bufferedReader().use { it.readText() }

      val jsonObj = parseJson<T>(response)
      return if (jsonObj != null) {
        JsonObjAndTextContent(jsonObj = jsonObj, textContent = response)
      } else {
        null
      }
    } else {
      Log.e("AGUtils", "HTTP error: $responseCode")
    }
  } catch (e: Exception) {
    Log.e("AGUtils", "Error when getting or parsing json response", e)
  } finally {
    connection?.disconnect()
  }

  return null
}

/** Parses a JSON string into an object of type [T] using Gson. */
inline fun <reified T> parseJson(response: String): T? {
  return try {
    val gson = Gson()
    gson.fromJson(response, T::class.java)
  } catch (e: Exception) {
    Log.e("AGUtils", "Error parsing JSON string", e)
    null
  }
}

fun isPixel10(): Boolean {
  return Build.MODEL != null && Build.MODEL.lowercase().contains("pixel 10")
}

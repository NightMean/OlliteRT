package com.ollitert.llm.server.ui.modelmanager

import android.content.Context
import android.util.Log
import com.ollitert.llm.server.common.GitHubConfig
import com.ollitert.llm.server.common.getJsonResponse
import com.ollitert.llm.server.data.ModelAllowlist
import com.google.gson.Gson
import java.io.File

private const val TAG = "AllowlistLoader"
private const val MODEL_ALLOWLIST_FILENAME = "model_allowlist.json"
private const val MODEL_ALLOWLIST_TEST_FILENAME = "model_allowlist_test.json"

/**
 * Loads the model allowlist from network, disk cache, or bundled assets.
 * Pure I/O layer — does not touch ViewModel state or coroutine scopes.
 * The ViewModel calls these methods from its own coroutine scope.
 *
 * Loading chain: test file → network (GitHub) → disk cache → bundled asset.
 */
class ModelAllowlistLoader(
  private val context: Context,
  private val externalFilesDir: File?,
) {

  data class LoadResult(
    val allowlist: ModelAllowlist?,
    val source: AllowlistSource?,
    val rawJson: String? = null,
  )

  /** Try to load the test allowlist from /data/local/tmp. */
  fun readTestAllowlist(): ModelAllowlist? {
    return readFromDisk(fileName = MODEL_ALLOWLIST_TEST_FILENAME)
  }

  /**
   * Fetch allowlist from GitHub. Returns the parsed allowlist and raw JSON
   * (for caching to disk), or null on failure.
   */
  fun fetchFromNetwork(version: String): LoadResult {
    val url = "${GitHubConfig.ALLOWLIST_BASE_URL}/${version}.json"
    Log.d(TAG, "Loading model allowlist from internet. Url: $url")
    val data = getJsonResponse<ModelAllowlist>(url = url)
    return if (data?.jsonObj != null) {
      Log.d(TAG, "Done: loading model allowlist from internet")
      LoadResult(data.jsonObj, AllowlistSource.NETWORK, data.textContent)
    } else {
      Log.w(TAG, "Failed to load model allowlist from internet")
      LoadResult(null, null)
    }
  }

  /** Save allowlist JSON to disk for future offline use. */
  fun saveToDisk(content: String) {
    try {
      Log.d(TAG, "Saving model allowlist to disk...")
      val file = File(externalFilesDir, MODEL_ALLOWLIST_FILENAME)
      file.writeText(content)
      Log.d(TAG, "Done: saving model allowlist to disk.")
    } catch (e: Exception) {
      Log.e(TAG, "failed to write model allowlist to disk", e)
    }
  }

  /** Read allowlist from disk cache. */
  fun readFromDiskCache(): ModelAllowlist? {
    return readFromDisk(MODEL_ALLOWLIST_FILENAME)
  }

  /** Read allowlist from APK's bundled assets (fallback for fresh install). */
  fun readFromAssets(): ModelAllowlist? {
    return try {
      val content = context.assets.open(MODEL_ALLOWLIST_FILENAME).bufferedReader().use { it.readText() }
      Log.d(TAG, "Loaded bundled model allowlist from assets")
      Gson().fromJson(content, ModelAllowlist::class.java)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to read bundled model allowlist from assets", e)
      null
    }
  }

  private fun readFromDisk(fileName: String): ModelAllowlist? {
    try {
      Log.d(TAG, "Reading model allowlist from disk: $fileName")
      val baseDir =
        if (fileName == MODEL_ALLOWLIST_TEST_FILENAME) File("/data/local/tmp") else externalFilesDir
      val file = File(baseDir, fileName)
      if (file.exists()) {
        val content = file.readText()
        Log.d(TAG, "Model allowlist content from local file: $content")
        return Gson().fromJson(content, ModelAllowlist::class.java)
      }
    } catch (e: Exception) {
      Log.e(TAG, "failed to read model allowlist from disk", e)
      return null
    }
    return null
  }
}

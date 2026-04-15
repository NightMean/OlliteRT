package com.ollitert.llm.server.ui.modelmanager

import android.content.Context
import android.util.Log
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
) : AllowlistLoader {

  /** Try to load the test allowlist from /data/local/tmp. */
  override fun readTestAllowlist(): ModelAllowlist? {
    return readFromDisk(fileName = MODEL_ALLOWLIST_TEST_FILENAME)
  }

  /** Save allowlist JSON to disk for future offline use. */
  override fun saveToDisk(content: String) {
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
  override fun readFromDiskCache(): ModelAllowlist? {
    return readFromDisk(MODEL_ALLOWLIST_FILENAME)
  }

  /** Read allowlist from APK's bundled assets (fallback for fresh install). */
  override fun readFromAssets(): ModelAllowlist? {
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

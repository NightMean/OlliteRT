package com.ollitert.llm.server.service

import com.ollitert.llm.server.data.AllowedModel
import com.ollitert.llm.server.data.ModelAllowlist
import com.ollitert.llm.server.data.ModelAllowlistJson
import java.io.File

/**
 * Loads the model allowlist from the filesystem. Resolution order:
 * 1. [externalFilesDir]/model_allowlist.json
 * 2. /sdcard/Android/data/[packageName]/files/model_allowlist.json
 * 3. [assetReader] (bundled asset, optional)
 *
 * Caches the last successful load so callers always get a valid list even
 * when the external file is temporarily unavailable.
 */
class LlmHttpAllowlistLoader(
  private val externalFilesDir: File?,
  private val packageName: String,
  private val assetReader: () -> String? = { null },
) {
  private var cached: ModelAllowlist? = null
  var lastSource: String = "unknown"
    private set

  /** Returns the current list of allowed models, falling back to cache on error. */
  fun load(): List<AllowedModel> {
    val fresh = readFromFiles()
    if (fresh != null && fresh.models.isNotEmpty()) {
      cached = fresh
      return fresh.models
    }
    if (lastSource == "unknown") lastSource = "empty"
    return cached?.models ?: emptyList()
  }

  private fun readFromFiles(): ModelAllowlist? {
    return try {
      var file = externalFilesDir?.let { File(it, "model_allowlist.json") }
      if (file == null || !file.exists()) {
        file = File("/sdcard/Android/data/$packageName/files", "model_allowlist.json")
      }
      if (file.exists()) {
        val allowlist = ModelAllowlistJson.decode(file.readText())
        lastSource = "external:${file.absolutePath}"
        return allowlist
      }
      val assetText = assetReader() ?: return null
      val allowlist = ModelAllowlistJson.decode(assetText)
      lastSource = "asset"
      allowlist
    } catch (e: Exception) {
      lastSource = "error"
      null
    }
  }
}

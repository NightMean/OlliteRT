package com.ollite.llm.server.ui.navigation

/** Navigation route constants for the Ollite app. */
object OlliteRoutes {
  const val GETTING_STARTED = "getting_started"
  const val MODELS = "models"
  const val STATUS = "status"
  const val LOGS = "logs"
  const val SETTINGS = "settings"
  const val BENCHMARK = "benchmark/{modelName}"

  fun benchmark(modelName: String) = "benchmark/$modelName"
}

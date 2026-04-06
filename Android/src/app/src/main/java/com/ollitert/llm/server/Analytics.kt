package com.ollitert.llm.server

/** No-op analytics stub. Firebase has been removed. */
enum class OlliteRTEvent(val id: String) {
  CAPABILITY_SELECT(id = "capability_select"),
  MODEL_DOWNLOAD(id = "model_download"),
  GENERATE_ACTION(id = "generate_action"),
  BUTTON_CLICKED(id = "button_clicked"),
}

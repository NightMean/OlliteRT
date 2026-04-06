package com.ollite.llm.server.data

import com.google.gson.Gson

object ModelAllowlistJson {
  private val gson = Gson()

  fun decode(content: String): ModelAllowlist = gson.fromJson(content, ModelAllowlist::class.java)
}

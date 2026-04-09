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

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.protobuf)
  alias(libs.plugins.hilt.application)
  alias(libs.plugins.oss.licenses)
  alias(libs.plugins.ksp)
}

android {
  namespace = "com.ollitert.llm.server"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.ollitert.llm.server"
    minSdk = 31
    targetSdk = 35
    versionCode = 23
    versionName = "1.0.11"

    // Needed for HuggingFace auth workflows.
    // Use the scheme of the "Redirect URLs" in HuggingFace app.
    // Updated per-flavor below to match applicationId (with suffix).
    manifestPlaceholders["appAuthRedirectScheme"] = "com.ollitert.llm.server"
    manifestPlaceholders["applicationName"] = "com.ollitert.llm.server.OlliteRTApplication"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Product flavors: dev, beta, prod — all three can be installed side-by-side.
  // Each flavor gets its own app icon (with badge for dev/beta), splash screen,
  // and applicationIdSuffix so they coexist on the same device.
  flavorDimensions += "channel"

  productFlavors {
    create("dev") {
      dimension = "channel"
      applicationIdSuffix = ".dev"
      versionNameSuffix = "-dev"
      // App label shown in launcher and recent apps
      resValue("string", "app_label", "OlliteRT Dev")
      // BuildConfig field to identify flavor at runtime
      buildConfigField("String", "CHANNEL", "\"dev\"")
      // OAuth redirect must match the full applicationId
      manifestPlaceholders["appAuthRedirectScheme"] = "com.ollitert.llm.server.dev"
    }
    create("beta") {
      dimension = "channel"
      applicationIdSuffix = ".beta"
      versionNameSuffix = "-beta"
      resValue("string", "app_label", "OlliteRT Beta")
      buildConfigField("String", "CHANNEL", "\"beta\"")
      manifestPlaceholders["appAuthRedirectScheme"] = "com.ollitert.llm.server.beta"
    }
    create("prod") {
      dimension = "channel"
      // No suffix — this is the production release
      resValue("string", "app_label", "OlliteRT")
      buildConfigField("String", "CHANNEL", "\"prod\"")
      // Default applicationId, no override needed
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("debug")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlinOptions {
    jvmTarget = "11"
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material3)
  implementation(libs.androidx.compose.navigation)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlin.reflect)
  implementation(libs.material.icon.extended)
  implementation(libs.androidx.work.runtime)
  implementation(libs.androidx.datastore)
  implementation(libs.com.google.code.gson)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.security.crypto)
  implementation(libs.androidx.webkit)
  implementation("org.nanohttpd:nanohttpd:2.3.1")
  implementation(libs.litertlm)
  implementation(libs.commonmark)
  implementation(libs.richtext)
  implementation(libs.tflite)
  implementation(libs.tflite.gpu)
  implementation(libs.tflite.support)
  implementation(libs.openid.appauth)
  implementation(libs.androidx.splashscreen)
  implementation(libs.protobuf.javalite)
  implementation(libs.hilt.android)
  implementation(libs.hilt.navigation.compose)
  implementation(libs.play.services.oss.licenses)
  implementation(libs.androidx.exifinterface)
  implementation(libs.moshi.kotlin)
  ksp(libs.hilt.android.compiler)
  ksp(libs.moshi.kotlin.codegen)
  testImplementation(libs.junit)
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)
  androidTestImplementation(libs.hilt.android.testing)
  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)
}

protobuf {
  protoc { artifact = "com.google.protobuf:protoc:4.26.1" }
  generateProtoTasks { all().forEach { it.plugins { create("java") { option("lite") } } } }
}

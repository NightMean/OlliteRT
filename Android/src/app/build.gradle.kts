import java.util.Properties

/*
 * Copyright 2025 Google LLC
 * Modifications Copyright 2025-2026 @NightMean (https://github.com/NightMean)
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
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.protobuf)
  alias(libs.plugins.hilt.application)
  alias(libs.plugins.oss.licenses)
  alias(libs.plugins.ksp)
}

// Short git hash for BuildConfig — enables traceability in bug reports and Settings footer.
// Falls back to "unknown" when building outside a git repo (e.g. downloaded source archive).
val gitHash: String = providers.exec {
  commandLine("git", "rev-parse", "--short", "HEAD")
}.standardOutput.asText.map { it.trim() }.getOrElse("unknown")

// When CI passes a version that already contains a channel suffix (e.g. "0.9.0-dev.1"),
// skip the flavor's versionNameSuffix to avoid double-suffixing ("0.9.0-dev.1-dev").
val versionFromProperty = findProperty("APP_VERSION_NAME") as String
val ciVersionHasChannel = versionFromProperty.contains("-dev") || versionFromProperty.contains("-beta")

// Auto versionCode: when APP_VERSION_CODE is "auto", derive from git commit count.
// CI can pass an explicit number via -PAPP_VERSION_CODE=N to override.
val resolvedVersionCode: Int = run {
  val raw = findProperty("APP_VERSION_CODE") as String
  if (raw == "auto") {
    providers.exec {
      commandLine("git", "rev-list", "--count", "HEAD")
    }.standardOutput.asText.map { it.trim().toInt() }.getOrElse(1)
  } else {
    raw.toInt()
  }
}

android {
  namespace = "com.ollitert.llm.server"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.ollitert.llm.server"
    minSdk = 31
    targetSdk = 35
    versionCode = resolvedVersionCode
    versionName = findProperty("APP_VERSION_NAME") as String

    // Needed for HuggingFace auth workflows.
    // Use the scheme of the "Redirect URLs" in HuggingFace app.
    // Updated per-flavor below to match applicationId (with suffix).
    manifestPlaceholders["appAuthRedirectScheme"] = "com.ollitert.llm.server"
    manifestPlaceholders["applicationName"] = "com.ollitert.llm.server.OlliteRTApplication"

    // Git commit hash for traceability in Settings footer and future bug reports
    buildConfigField("String", "GIT_HASH", "\"$gitHash\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Release signing config: reads from keystore.properties (local dev) or
  // environment variables (CI). Falls back to debug signing if neither is set.
  signingConfigs {
    create("release") {
      val keystorePropsFile = rootProject.file("keystore.properties")
      if (keystorePropsFile.exists()) {
        val props = Properties()
        keystorePropsFile.inputStream().use { props.load(it) }
        val ksFile = props.getProperty("storeFile")
        val missing = listOfNotNull(
          if (ksFile.isNullOrBlank()) "storeFile" else null,
          if (props.getProperty("storePassword").isNullOrBlank()) "storePassword" else null,
          if (props.getProperty("keyAlias").isNullOrBlank()) "keyAlias" else null,
          if (props.getProperty("keyPassword").isNullOrBlank()) "keyPassword" else null,
        )
        if (missing.isNotEmpty()) {
          logger.warn("WARNING: keystore.properties is missing values: ${missing.joinToString()}. Release builds will use debug signing.")
        } else if (!file(ksFile).exists()) {
          logger.warn("WARNING: Keystore file not found at $ksFile. Release builds will use debug signing.")
        } else {
          storeFile = file(ksFile)
          storePassword = props.getProperty("storePassword")
          keyAlias = props.getProperty("keyAlias")
          keyPassword = props.getProperty("keyPassword")
        }
      } else if (System.getenv("KEYSTORE_FILE") != null) {
        storeFile = file(System.getenv("KEYSTORE_FILE"))
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS")
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
  }

  // Product flavors: dev, beta, stable — all three can be installed side-by-side.
  // Each flavor gets its own app icon (with badge for dev/beta), splash screen,
  // and applicationIdSuffix so they coexist on the same device.
  flavorDimensions += "channel"

  productFlavors {
    create("dev") {
      dimension = "channel"
      applicationIdSuffix = ".dev"
      if (!ciVersionHasChannel) versionNameSuffix = "-dev"
      // BuildConfig field to identify flavor at runtime
      buildConfigField("String", "CHANNEL", "\"dev\"")
      // Update channel: dev sees all releases (stable, beta, dev)
      buildConfigField("String", "UPDATE_CHANNEL", "\"dev\"")
      // OAuth redirect must match the full applicationId
      manifestPlaceholders["appAuthRedirectScheme"] = "com.ollitert.llm.server.dev"
    }
    create("beta") {
      dimension = "channel"
      applicationIdSuffix = ".beta"
      if (!ciVersionHasChannel) versionNameSuffix = "-beta"
      buildConfigField("String", "CHANNEL", "\"beta\"")
      // Update channel: beta sees beta and stable releases
      buildConfigField("String", "UPDATE_CHANNEL", "\"beta\"")
      manifestPlaceholders["appAuthRedirectScheme"] = "com.ollitert.llm.server.beta"
    }
    create("stable") {
      dimension = "channel"
      // No suffix — this is the production release
      buildConfigField("String", "CHANNEL", "\"stable\"")
      // Update channel: stable only (GitHub's /releases/latest auto-skips pre-releases)
      buildConfigField("String", "UPDATE_CHANNEL", "\"stable\"")
      // Default applicationId, no override needed
    }
  }

  // Only arm64-v8a is supported — LiteRT LM's x86_64 native library crashes
  // with SIGILL on Android emulators, and 32-bit architectures have no native
  // libraries at all.
  splits {
    abi {
      isEnable = true
      reset()
      include("arm64-v8a")
      isUniversalApk = false
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      val releaseKeystore = signingConfigs.findByName("release")?.takeIf { it.storeFile != null }
      if (releaseKeystore != null) {
        signingConfig = releaseKeystore
      } else {
        logger.warn("WARNING: Release keystore not configured — release builds will fail. Create keystore.properties to fix.")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
}

androidComponents {
  onVariants { variant ->
    val flavor = variant.flavorName ?: "stable"
    val buildTypeName = variant.buildType ?: "debug"
    variant.outputs.forEach { output ->
      output.outputFileName.set("OlliteRT-${flavor}-${gitHash}-arm64-v8a-${buildTypeName}.apk")
    }
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
  }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  // collectAsStateWithLifecycle — stops StateFlow collection when the lifecycle is STOPPED
  // (app backgrounded), preventing unnecessary recomposition and battery drain.
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material3)
  implementation(libs.androidx.compose.navigation)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.material.icon.extended)
  implementation(libs.androidx.work.runtime)
  implementation(libs.androidx.datastore)
  implementation(libs.ktor.server.core)
  implementation(libs.ktor.server.cio)
  implementation(libs.ktor.server.content.negotiation)
  implementation(libs.ktor.serialization.kotlinx.json)
  implementation(libs.ktor.server.cors)
  implementation(libs.ktor.server.status.pages)
  implementation(libs.litertlm)
  implementation(libs.commonmark)
  implementation(libs.richtext)
  implementation(libs.openid.appauth)
  implementation(libs.androidx.splashscreen)
  implementation(libs.protobuf.javalite)
  implementation(libs.hilt.android)
  implementation(libs.hilt.navigation.compose)
  implementation(libs.play.services.oss.licenses)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  implementation(libs.coil.compose)
  implementation(libs.coil.network.okhttp)
  implementation(libs.hilt.work)
  ksp(libs.hilt.android.compiler)
  ksp(libs.hilt.compiler)
  ksp(libs.androidx.room.compiler)
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.kotlinx.coroutines.test)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.room.testing)
  debugImplementation(libs.androidx.ui.tooling)
}

// Sync master allowlist → bundled asset so the app always ships with the latest models.
// The master file lives at /model_allowlists/v1/model_allowlist.json (repo root);
// this task copies it into the assets directory before the APK is assembled.
val syncAllowlist by tasks.registering(Copy::class) {
  from(rootProject.file("../../model_allowlists/v1/model_allowlist.json"))
  into(layout.projectDirectory.dir("src/main/assets"))
}
tasks.named("preBuild") { dependsOn(syncAllowlist) }

protobuf {
  protoc { artifact = "com.google.protobuf:protoc:4.34.1" }
  generateProtoTasks {
    all().forEach { task ->
      task.builtins {
        create("java") { option("lite") }
      }
    }
  }
}

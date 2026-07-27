import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.kapt)
  alias(libs.plugins.hilt)
}

// Per-developer API base URL override (e.g. a teammate's ngrok tunnel while auth-service has no
// stable dev/staging host yet). Set API_BASE_URL in local.properties (gitignored, never
// committed); falls back to the placeholder prod host for CI/release builds where it's absent.
val localProperties = Properties().apply {
  val file = rootProject.file("local.properties")
  if (file.exists()) file.inputStream().use { load(it) }
}
// Retrofit requires the base URL to end with "/"; normalize in case a developer's override omits it.
val apiBaseUrl: String = (localProperties.getProperty("API_BASE_URL")
  ?: "https://api.arogyasakhi.armman.org/api/v1/").let { if (it.endsWith("/")) it else "$it/" }

android {
  namespace = "org.armman.supervisor"
  compileSdk = 34

  defaultConfig {
    applicationId = "org.armman.supervisor"
    minSdk = 29
    targetSdk = 34
    versionCode = 1
    versionName = "1.0.0"
    buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
  }

  // Per-developer release signing, same gitignored-override pattern as API_BASE_URL above: set
  // RELEASE_KEYSTORE_(FILE|PASSWORD|KEY_ALIAS|KEY_PASSWORD) in local.properties to sign a local
  // release build (e.g. for install-and-test on a device). Absent in CI/other checkouts, so
  // assembleRelease still succeeds there — just produces an unsigned APK until CI wires its own
  // secrets-backed signing config.
  val releaseKeystoreFile = localProperties.getProperty("RELEASE_KEYSTORE_FILE")
  signingConfigs {
    if (releaseKeystoreFile != null) {
      create("release") {
        storeFile = rootProject.file(releaseKeystoreFile)
        storePassword = localProperties.getProperty("RELEASE_KEYSTORE_PASSWORD")
        keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
        keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (releaseKeystoreFile != null) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
  }
  buildFeatures { compose = true; buildConfig = true }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  // Per-app locales (Choose Language) need AppCompat below API 33.
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.navigation.compose)

  implementation(platform(libs.compose.bom))
  implementation(libs.compose.material3)
  // Icons for settings rows / password visibility toggle and design-system placeholders
  // (settings/profile/download/group/rocket) where no ic_* design asset exists yet — see ui/dashboard.
  implementation(libs.compose.material.icons.extended)
  implementation(libs.compose.ui)
  implementation(libs.compose.ui.tooling.preview)
  debugImplementation(libs.compose.ui.tooling)

  implementation(libs.hilt.android)
  kapt(libs.hilt.compiler)
  implementation(libs.hilt.navigation.compose)

  implementation(libs.retrofit)
  implementation(libs.retrofit.gson)
  implementation(libs.okhttp.logging)
  implementation(libs.coroutines.android)
  implementation(libs.androidx.security.crypto)

  // Local encrypted store for data not yet backed by a real API (e.g. inventory transactions) —
  // see AssignItemRepositoryImpl. Encrypted via SQLCipher's Room SupportFactory on-device only;
  // unit tests use a plain (unencrypted) Room in-memory database, see FakeDatabaseModule.
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  kapt(libs.androidx.room.compiler)
  implementation(libs.sqlcipher.android)

  testImplementation(libs.junit)
  testImplementation(libs.coroutines.test)
}

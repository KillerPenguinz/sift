import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Secrets/config live in local.properties (git-ignored), never committed. Add:
//   notion.clientId=...        (public integration client id)
//   notion.oauthProxyUrl=...   (deployed token-exchange proxy URL)
//   notion.devToken=...        (optional: internal integration token for debug dogfooding)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(key: String): String = (localProps.getProperty(key) ?: "").replace("\"", "\\\"")

android {
    namespace = "com.ironclinicgym.sift"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.ironclinicgym.sift"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "NOTION_CLIENT_ID", "\"${secret("notion.clientId")}\"")
        buildConfigField("String", "NOTION_OAUTH_PROXY_URL", "\"${secret("notion.oauthProxyUrl")}\"")
        // Debug dogfooding only; empty in release. Lets you test the real Notion API with a
        // personal integration token, bypassing the OAuth flow.
        buildConfigField("String", "SIFT_DEV_NOTION_TOKEN", "\"${secret("notion.devToken")}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
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

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    // Coroutines + serialization runtime (serializers are generated in :core).
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // Networking (implements core HttpTransport).
    implementation(libs.okhttp)

    // DI is manual (a small AppContainer) because Hilt's Gradle plugin is not yet
    // compatible with AGP 9.2 (it looks for the removed Android BaseExtension).

    // Local persistence (Room: task cache only) + secure storage (DataStore + Keystore).
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Background refresh.
    implementation(libs.androidx.work.runtime.ktx)

    // OAuth via Custom Tabs.
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core.splashscreen)

    // Drag-to-reorder lists.
    implementation(libs.reorderable)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
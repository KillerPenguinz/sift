// Sift core — UI-agnostic, KMP-ready.
// Pure Kotlin: token data + color math + derive(), the Notion client, OAuth logic, the
// mapping layer, domain models, and ports — all with NO Android or Compose dependencies,
// so this source set lifts into a KMP `commonMain` unchanged. The Compose/Android layer
// in :app is the only thing that knows about androidx, Room, WorkManager, Keystore, etc.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // RRULE (iCalendar recurrence) engine. Pure-JVM; kept behind the RecurrenceEngine
    // interface so a KMP swap is a single-file change if core later lifts to commonMain.
    implementation(libs.lib.recur)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.music.innertube"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.timber)
    implementation(libs.pipepipe.extractor)
    implementation(libs.brave.extractor)
    implementation(libs.ksoup.html)
    implementation(libs.ksoup.entities)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.encoding)
    implementation(libs.brotli)
    testImplementation(libs.junit)

    coreLibraryDesugaring(libs.desugaring)
}

configurations.all {
    exclude(group = "com.google.protobuf", module = "protobuf-java")
}

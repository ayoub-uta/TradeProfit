import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localEnvironment = Properties().apply {
    val envFile = rootProject.file(".env")
    if (envFile.isFile) {
        envFile.inputStream().use(::load)
    }
}

fun secret(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: localEnvironment.getProperty(name)?.takeIf { it.isNotBlank() }

android {
    namespace = "com.example.tradeprofit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.tradeprofit"
        minSdk = 24
        targetSdk = 35
        versionCode = 5
        versionName = "3.2"
    }

    buildFeatures { compose = true }

    val releaseSigningConfig = listOf(
        secret("TRADEPROFIT_KEYSTORE_PATH"),
        secret("TRADEPROFIT_KEYSTORE_PASSWORD"),
        secret("TRADEPROFIT_KEY_ALIAS"),
        secret("TRADEPROFIT_KEY_PASSWORD")
    ).takeIf { values -> values.all { !it.isNullOrBlank() } }?.let { values ->
        signingConfigs.create("releaseFromEnvironment") {
            storeFile = rootProject.file(values[0]!!)
            storePassword = values[1]
            keyAlias = values[2]
            keyPassword = values[3]
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            releaseSigningConfig?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("optimized") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

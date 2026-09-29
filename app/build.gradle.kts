import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.clearcmos.tiles"
    compileSdk = 36
    // Pinned so the read-only nix SDK is never asked to download a different revision.
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.clearcmos.tiles"
        // 34 so startActivityAndCollapse(PendingIntent) and the StatusBarManager
        // add-tile request are both available without version guards.
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        // Version-drift checks would fail on every upstream release; dependency and SDK
        // bumps are deliberate, reviewed changes here.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable", "OldTargetApi")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ktlint {
    android.set(true)
    outputToConsole.set(true)
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.jsch)
    testImplementation(libs.junit)
}

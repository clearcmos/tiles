import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ktlint)
    jacoco
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
        debug {
            enableUnitTestCoverage = true
        }
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

jacoco {
    toolVersion = "0.8.13"
}

// Only the classes the unit tests can reach without a device are measured; the tile
// services, activities, and SSH client need Android or a live host and are exempt.
val coverageClasses =
    fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
        exclude("**/R.class", "**/R\$*.class", "**/BuildConfig.*", "**/databinding/**")
    }

val coverageData =
    layout.buildDirectory.file("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")

val jacocoDebugReport by tasks.registering(JacocoReport::class) {
    dependsOn("testDebugUnitTest")
    classDirectories.setFrom(coverageClasses)
    sourceDirectories.setFrom("src/main/kotlin")
    executionData.setFrom(coverageData)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

val jacocoDebugCoverageVerification by tasks.registering(JacocoCoverageVerification::class) {
    dependsOn(jacocoDebugReport)
    classDirectories.setFrom(coverageClasses)
    sourceDirectories.setFrom("src/main/kotlin")
    executionData.setFrom(coverageData)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.15".toBigDecimal()
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.jsch)
    testImplementation(libs.junit)
}

/*
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import com.android.build.api.dsl.ApplicationExtension
import java.util.regex.Pattern

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.legacy.kapt)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.parcelize)
    alias(libs.plugins.jetbrains.kotlinx.serialization)
}

val gitWorkingBranch = providers.exec {
    commandLine("git", "rev-parse", "--abbrev-ref", "HEAD")
}.standardOutput.asText.map { it.trim() }
val defaultBranches = listOf("master", "dev")
val workingBranch = gitWorkingBranch.getOrElse("")
val normalizedWorkingBranch = workingBranch
    .replaceFirst("^[^A-Za-z]+".toRegex(), "")
    .replace("[^0-9A-Za-z]+".toRegex(), "")

kotlin {
    jvmToolchain(21)

    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "androidx.media3.common.util.UnstableApi"
        )
    }
}

configure<ApplicationExtension> {
    compileSdk {
        version = release(NEWPIPE_VERSION_SDK_COMPILE_MAJOR) {
            minorApiLevel = NEWPIPE_VERSION_SDK_COMPILE_MINOR
        }
    }
    namespace = NEWPIPE_APPLICATION_ID_OLD

    // F-Droid rejects the Google-signed dependency metadata block AGP adds to APKs by default.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    defaultConfig {
        applicationId = NEWPIPE_APPLICATION_ID_OLD
        resValue("string", "app_name", "YouFlow")
        minSdk {
            version = release(NEWPIPE_VERSION_SDK_MIN)
        }
        targetSdk {
            version = release(NEWPIPE_VERSION_SDK_TARGET)
        }

        versionCode = System.getProperty("versionCodeOverride")?.toInt() ?: NEWPIPE_VERSION_CODE

        versionName = NEWPIPE_VERSION_NAME
        System.getProperty("versionNameSuffix")?.let { versionNameSuffix = it }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isDebuggable = true

            // suffix the app id and the app name with git branch name
            if (normalizedWorkingBranch.isEmpty() || workingBranch in defaultBranches) {
                applicationIdSuffix = ".debug"
                resValue("string", "app_name", "YouFlow Debug")
            } else {
                applicationIdSuffix = ".debug.$normalizedWorkingBranch"
                resValue("string", "app_name", "YouFlow Debug")
            }
        }

        release {
            System.getProperty("packageSuffix")?.let { suffix ->
                applicationIdSuffix = suffix
                resValue("string", "app_name", "YouFlow $suffix")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        register("continuous") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isDefault = true

            // suffix the app id and the app name with git branch name
            if (normalizedWorkingBranch.isEmpty() || workingBranch in defaultBranches) {
                applicationIdSuffix = ".continuous"
                resValue("string", "app_name", "YouFlow Continuous")
            } else {
                applicationIdSuffix = ".continuous.$normalizedWorkingBranch"
                resValue("string", "app_name", "YouFlow Continuous")
            }
        }
    }

    lint {
        lintConfig = file("lint.xml")
        // Continue the debug build even when errors are found
        abortOnError = false
        // The release lint pass crashes on JDK 17 (a detector calls a JDK 21 API) and is not needed to build.
        checkReleaseBuilds = false
    }

    compileOptions {
        // Flag to enable support for the new language APIs
        isCoreLibraryDesugaringEnabled = true
        encoding = "utf-8"
    }

    sourceSets {
        getByName("androidTest") {
            assets.directories += "$projectDir/schemas"
        }
    }

    androidResources {
        generateLocaleConfig = true
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        resValues = true
    }

    packaging {
        resources {
            // remove two files which belong to jsoup
            // no idea how they ended up in the META-INF dir...
            excludes += setOf(
                "META-INF/README.md",
                "META-INF/CHANGES",
                "META-INF/COPYRIGHT" // "COPYRIGHT" belongs to RxJava...
            )
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Custom dependency configuration for ktlint
val ktlint by configurations.creating

val outputDir = project.layout.buildDirectory.dir("reports/ktlint/")
val inputFiles = fileTree("src") { include("**/*.kt") }

tasks.register<JavaExec>("runKtlint") {
    inputs.files(inputFiles)
    outputs.dir(outputDir)
    mainClass.set("com.pinterest.ktlint.Main")
    classpath = configurations.getByName("ktlint")
    args = listOf("--editorconfig=../.editorconfig", "src/**/*.kt")
    jvmArgs = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

tasks.register<JavaExec>("formatKtlint") {
    inputs.files(inputFiles)
    outputs.dir(outputDir)
    mainClass.set("com.pinterest.ktlint.Main")
    classpath = configurations.getByName("ktlint")
    args = listOf("--editorconfig=../.editorconfig", "-F", "src/**/*.kt")
    jvmArgs = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

tasks.register<CheckDependenciesOrder>("checkDependenciesOrder") {
    tomlFile = layout.projectDirectory.file("../gradle/libs.versions.toml")
}

afterEvaluate {
    tasks.named("preDebugBuild").configure {
        dependsOn("checkDependenciesOrder")
    }
}

dependencies {
    // Desugaring
    coreLibraryDesugaring(libs.android.desugar)

    // NewPipe libraries
    implementation(libs.newpipe.nanojson)
    implementation(libs.newpipe.extractor)
    implementation(libs.newpipe.filepicker)

    // ktlint (run manually through runKtlint / formatKtlint)
    ktlint(libs.pinterest.ktlint)

    // AndroidX
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.rxjava3)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.work.rxjava3)
    implementation(libs.google.android.material)
    implementation(libs.androidx.webkit)

    // Coroutines interop
    implementation(libs.kotlinx.coroutines.rx3)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    // Third-party libraries
    implementation(libs.livefront.bridge)
    implementation(libs.evernote.statesaver.core)
    kapt(libs.evernote.statesaver.compiler)

    // HTML parser
    implementation(libs.jsoup)

    // HTTP client
    implementation(libs.squareup.okhttp)
    implementation(libs.squareup.okhttp.brotli)

    // Media3 (YouFlow player)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.smoothstreaming)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui.compose)



    // Compose UI (YouFlow)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.jetbrains.compose.foundation)
    implementation(libs.jetbrains.compose.material3)
    implementation(libs.jetbrains.compose.preview)
    implementation(libs.jetbrains.compose.runtime)
    implementation(libs.jetbrains.compose.ui)
    implementation(libs.jetbrains.lifecycle.viewmodel)
    debugImplementation(libs.jetbrains.compose.tooling)

    // Image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)


    // Properly restarting
    implementation(libs.jakewharton.phoenix)

    // Reactive extensions for Java VM
    implementation(libs.reactivex.rxjava)
    implementation(libs.reactivex.rxandroid)

    // Date and time formatting
    implementation(libs.ocpsoft.prettytime)

    // Debugging and memory leak detection
    debugImplementation(libs.squareup.leakcanary.watcher)
    debugImplementation(libs.squareup.leakcanary.plumber)
    debugImplementation(libs.squareup.leakcanary.core)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.assertj.core)
}


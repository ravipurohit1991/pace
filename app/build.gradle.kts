import com.google.protobuf.gradle.id
import java.util.Properties

/**
 * Personal provisioning values live in `local.properties`, which is git-ignored. They let an owner
 * bake their own Ollama key and existing smoking history into a private build. Every key is
 * optional: absent values compile to empty/zero and the app behaves like a clean install.
 */
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun localValue(key: String, fallback: String = ""): String =
    (localProperties.getProperty(key) ?: System.getenv(key.replace('.', '_').uppercase()) ?: fallback).trim()

/**
 * Release signing material. Locally it comes from `keystore.properties` (git-ignored, and pointing at
 * a keystore that lives outside the repo); in CI the same four values arrive as environment variables
 * decoded from GitHub secrets. When neither is present the release build stays unsigned rather than
 * failing, so a plain `assembleRelease` still works for anyone checking out the source.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun signingValue(key: String, env: String): String? =
    (keystoreProperties.getProperty(key) ?: System.getenv(env))?.trim()?.takeIf(String::isNotEmpty)

// Tags drive the published version; the fallbacks keep local builds working off a bare checkout.
val paceVersionCode = (System.getenv("PACE_VERSION_CODE") ?: "1").toInt()
val paceVersionName = System.getenv("PACE_VERSION_NAME") ?: "0.1.0"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.protobuf)
}

android {
    namespace = "com.pace.reduction"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pace.reduction"
        minSdk = 26
        targetSdk = 36
        versionCode = paceVersionCode
        versionName = paceVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // Provisioning from local.properties (git-ignored). Empty defaults = clean install.
        buildConfigField("String", "SEED_OLLAMA_KEY", "\"${localValue("pace.ollamaApiKey")}\"")
        buildConfigField("String", "SEED_OLLAMA_MODEL", "\"${localValue("pace.ollamaModel")}\"")
        buildConfigField("int", "SEED_YESTERDAY_COUNT", localValue("pace.seedYesterday", "0"))
        buildConfigField("int", "SEED_TODAY_COUNT", localValue("pace.seedToday", "0"))
        buildConfigField("String", "SEED_LAST_TIME", "\"${localValue("pace.seedLastTime")}\"")
        buildConfigField("int", "SEED_CEILING", localValue("pace.seedCeiling", "0"))
        buildConfigField("int", "SEED_SPACING", localValue("pace.seedSpacing", "0"))
    }

    signingConfigs {
        create("release") {
            val store = signingValue("storeFile", "PACE_KEYSTORE_FILE")
            if (store != null) {
                storeFile = file(store)
                storePassword = signingValue("storePassword", "PACE_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "PACE_KEY_ALIAS") ?: "pace"
                keyPassword = signingValue("keyPassword", "PACE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/LICENSE.md",
            "META-INF/LICENSE-notice.md",
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

// MigrationTestHelper reads the exported schemas at runtime, so they have to travel inside the
// test APK rather than only existing in the source tree.
androidComponents {
    onVariants { variant ->
        variant.androidTest?.sources?.assets?.addStaticSourceDirectory("$projectDir/schemas")
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    generateProtoTasks {
        all().configureEach {
            builtins {
                id("java") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore)
    implementation(libs.protobuf.javalite)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.browser)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

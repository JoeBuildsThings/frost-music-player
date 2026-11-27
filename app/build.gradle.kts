import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
// Monotonic across every CI run, so each release installs over the last one.
val computedVersionCode = 100 + runNumber
// Release tags are v<major>.<minor>.<patch>; other builds get a run based dev name.
val releaseTag = System.getenv("GITHUB_REF_NAME")
    ?.takeIf { System.getenv("GITHUB_REF_TYPE") == "tag" && it.startsWith("v") }
val computedVersionName = releaseTag?.removePrefix("v")
    ?: if (runNumber > 0) "0.7.0-dev.$runNumber" else "0.7.0-dev"

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

val releaseKeystorePath = System.getenv("KEYSTORE_FILE")
    ?: keystoreProps.getProperty("storeFile")
    ?: "frost-release.jks"
val releaseKeystoreFile = file(releaseKeystorePath)
val releaseStorePassword = System.getenv("KEYSTORE_PASSWORD")
    ?: keystoreProps.getProperty("storePassword")
val releaseKeyAlias = System.getenv("KEY_ALIAS")
    ?: keystoreProps.getProperty("keyAlias")
val releaseKeyPassword = System.getenv("KEY_PASSWORD")
    ?: keystoreProps.getProperty("keyPassword")

val hasReleaseSigning = releaseKeystoreFile.exists() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

// CI passes -Pfrost.requireSigning=true for published builds. Without it, a missing keystore
// would silently fall back to a throwaway debug key and users could no longer update.
val requireReleaseSigning = providers.gradleProperty("frost.requireSigning").orNull == "true"
if (requireReleaseSigning && !hasReleaseSigning) {
    throw GradleException(
        "Release signing is required but the keystore or its passwords are missing. " +
            "Set KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS and KEY_PASSWORD."
    )
}

android {
    namespace = "com.joebuildsthings.frost"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.joebuildsthings.frost"
        minSdk = 23
        targetSdk = 35
        versionCode = computedVersionCode
        versionName = computedVersionName

        buildConfigField("String", "UPDATE_REPO", "\"JoeBuildsThings/frost-music-player\"")
        // Frost Pro is planned, not sold. Keep false until the purchase flow exists.
        val proGating = providers.gradleProperty("frost.proGating").orNull == "true"
        buildConfigField("boolean", "PRO_GATING", proGating.toString())
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    lint {
        abortOnError = true
        checkDependencies = false
        // Updating these is Dependabot's job, not a reason to fail a build.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
        xmlReport = true
        htmlReport = true
        // Print every issue into the CI log, not just the first one.
        textReport = true
        textOutput = file("stdout")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.compose.ui:ui:1.9.4")
    implementation("androidx.compose.ui:ui-graphics:1.9.4")
    implementation("androidx.compose.foundation:foundation:1.9.4")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-session:1.8.0")
    implementation("com.google.guava:guava:33.4.8-android")
    implementation("dev.chrisbanes.haze:haze:1.7.0")

    testImplementation("junit:junit:4.13.2")
    // android.jar only has stubs for org.json; the real implementation for JVM tests.
    testImplementation("org.json:json:20240303")
}

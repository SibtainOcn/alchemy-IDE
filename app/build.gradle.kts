import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Properties

apply(from = rootProject.file("gradle/test-summary.gradle.kts"))

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------
// Version
//
// One source of truth, overridable from the command line so CI can build straight from a
// tag: ./gradlew assembleRelease -PversionName=1.2.0 -PversionCode=7
// ---------------------------------------------------------------------------
val appVersionName: String = (findProperty("versionName") as String?) ?: "1.0.0"
val appVersionCode: Int = (findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

/**
 * STABLE, BETA or DEBUG - the middle word of every APK filename.
 *
 * "Release" describes how a build was compiled, not how finished it is: a beta is a
 * release build too. The channel answers the question a person downloading actually has,
 * which is whether this one is safe to rely on.
 *
 * Anything carrying a pre-release suffix (`-beta.1`, `-alpha.2`, `-rc.1`) is BETA, so a
 * pre-release can never be mistaken for a finished build sitting in the same folder.
 */
fun channelFor(buildType: String, versionName: String): String = when {
    buildType == "debug" -> "DEBUG"
    versionName.contains(Regex("(?i)-(alpha|beta|rc)")) -> "BETA"
    else -> "STABLE"
}

// ---------------------------------------------------------------------------
// Signing
//
// Credentials come from keystore.properties (local, gitignored) or from environment
// variables (CI). If neither is present the release build still runs and simply produces
// an unsigned APK, so a fork or a fresh clone is never blocked by secrets it cannot have.
// ---------------------------------------------------------------------------
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}

// Both spellings are read, newest first. The CI secrets were named for the old brand and
// renaming them is a change in the repository settings rather than in this file, so the
// build keeps answering to either until that happens.
fun signingValue(key: String, vararg envs: String): String? =
    (keystoreProperties.getProperty(key) ?: envs.firstNotNullOfOrNull { System.getenv(it) })
        ?.takeIf { it.isNotBlank() }

val storeFilePath = signingValue("storeFile", "ALCHEMY_STORE_FILE", "HAZEL_STORE_FILE")
val storePasswordValue = signingValue("storePassword", "ALCHEMY_STORE_PASSWORD", "HAZEL_STORE_PASSWORD")
val keyAliasValue = signingValue("keyAlias", "ALCHEMY_KEY_ALIAS", "HAZEL_KEY_ALIAS")
val keyPasswordValue = signingValue("keyPassword", "ALCHEMY_KEY_PASSWORD", "HAZEL_KEY_PASSWORD")

val canSign = storeFilePath != null &&
    storePasswordValue != null &&
    keyAliasValue != null &&
    keyPasswordValue != null &&
    file(storeFilePath).exists()

android {
    namespace = "com.sibtainocn.alchemy"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sibtainocn.alchemy"
        minSdk = 24
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        vectorDrawables { useSupportLibrary = true }
    }

    // Two distributions from one codebase. `fdroid` is the GitHub and F-Droid build and
    // is the one that talks to Termux; `playstore` carries no execution code at all.
    // docs/DISTRIBUTION-SPLIT.md explains where each kind of change belongs.
    flavorDimensions += "distribution"
    productFlavors {
        create("fdroid") {
            dimension = "distribution"
            // Deliberately no applicationIdSuffix: this flavour carries the plain
            // application id, and the Play Store build is the one that gets a suffix.
            //
            // The id itself changed with the rename to Alchemy, so a device holding the
            // old dev.hazel.code build will see this as a separate app rather than as an
            // update to that one. That is what an application id change always means and
            // there is no migration path around it.
        }
        create("playstore") {
            dimension = "distribution"
            applicationIdSuffix = ".ps"
        }
    }

    signingConfigs {
        if (canSign) {
            create("release") {
                storeFile = file(storeFilePath!!)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without this the release variant cannot be installed, which is what Android
            // Studio complains about when you press Run on the release build variant.
            signingConfig = if (canSign) signingConfigs.getByName("release") else null
        }
    }

    // One APK per architecture plus a universal one. The per-ABI builds are roughly a
    // third of the size because they carry a single copy of the native libraries.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // CrashGuard stamps the version into the report it writes.
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.splashscreen)

    implementation(libs.sora.editor)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}

// ---------------------------------------------------------------------------
// Packaging
//
// AGP names its outputs app-arm64-v8a-release.apk and offers no supported hook to rename
// them, so the build's own files are left alone and the ones meant for people are copied
// out under readable names:
//
//     ALCHEMY-IDE-STABLE-v1.0.0-arm64-v8a.apk
//     ALCHEMY-IDE-BETA-v2.0.0-beta.1-universal.apk
//
// The copy is what CI attaches to a GitHub release, alongside checksums.txt.
// ---------------------------------------------------------------------------
val packagedApkDir = layout.buildDirectory.dir("outputs/packaged")

tasks.register("packageReleaseApks") {
    group = "distribution"
    description = "Copies the release APKs out under ALCHEMY-IDE-<CHANNEL>-v<version>-<abi>.apk names."
    // The fdroid flavour is what ships from GitHub releases. The Play Store build is
    // uploaded from its own bundle and never goes through this task.
    dependsOn("assembleFdroidRelease")

    val sourceDir = layout.buildDirectory.dir("outputs/apk/fdroid/release")
    val targetDir = packagedApkDir
    val version = appVersionName
    val channel = channelFor("release", appVersionName)
    val signed = canSign

    doLast {
        val from = sourceDir.get().asFile
        val into = targetDir.get().asFile
        into.deleteRecursively()
        into.mkdirs()

        val apks = from.listFiles { f -> f.extension == "apk" }.orEmpty().sortedBy { it.name }
        if (apks.isEmpty()) error("No release APKs found in $from")

        val checksums = StringBuilder()
        apks.forEach { apk ->
            // app-fdroid-arm64-v8a-release.apk         -> arm64-v8a
            // app-fdroid-universal-release-unsigned.apk -> universal
            // app-fdroid-release.apk                    -> universal
            // Order matters: "-unsigned" is only present when no signing config applied.
            val abi = apk.name
                .removePrefix("app-fdroid-")
                .removeSuffix(".apk")
                .removeSuffix("-unsigned")
                .removeSuffix("-release")
                .ifBlank { "universal" }

            val target = File(into, "ALCHEMY-IDE-$channel-v$version-$abi.apk")
            apk.copyTo(target, overwrite = true)

            // `java` is a Gradle extension accessor in this DSL, so MessageDigest is
            // imported rather than fully qualified.
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(target.readBytes())
                .joinToString("") { byte -> "%02x".format(byte) }
            checksums.appendLine("$digest  ${target.name}")
        }

        File(into, "checksums.txt").writeText(checksums.toString())

        logger.lifecycle("")
        logger.lifecycle("  Packaged ${apks.size} APKs into ${into.path}")
        logger.lifecycle("  Channel: $channel   Version: $version   Signed: $signed")
        if (!signed) {
            logger.lifecycle("  NOTE: unsigned - set up keystore.properties to produce installable APKs.")
        }
        logger.lifecycle("")
    }
}

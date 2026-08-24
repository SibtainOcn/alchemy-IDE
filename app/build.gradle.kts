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
 * RELEASE, BETA or DEBUG — the middle word of every APK filename.
 *
 * Anything with a pre-release suffix (`-beta.1`, `-alpha.2`, `-rc.1`) is BETA, so a
 * pre-release build can never be mistaken for a final one sitting in the same folder.
 */
fun channelFor(buildType: String, versionName: String): String = when {
    buildType == "debug" -> "DEBUG"
    versionName.contains(Regex("(?i)-(alpha|beta|rc)")) -> "BETA"
    else -> "RELEASE"
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

fun signingValue(key: String, env: String): String? =
    (keystoreProperties.getProperty(key) ?: System.getenv(env))?.takeIf { it.isNotBlank() }

val storeFilePath = signingValue("storeFile", "HAZEL_STORE_FILE")
val storePasswordValue = signingValue("storePassword", "HAZEL_STORE_PASSWORD")
val keyAliasValue = signingValue("keyAlias", "HAZEL_KEY_ALIAS")
val keyPasswordValue = signingValue("keyPassword", "HAZEL_KEY_PASSWORD")

val canSign = storeFilePath != null &&
    storePasswordValue != null &&
    keyAliasValue != null &&
    keyPasswordValue != null &&
    file(storeFilePath).exists()

android {
    namespace = "dev.hazel.code"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.hazel.code"
        minSdk = 24
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        vectorDrawables { useSupportLibrary = true }
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

    buildFeatures { compose = true }

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

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}

// ---------------------------------------------------------------------------
// Packaging
//
// AGP names its outputs app-arm64-v8a-release.apk and offers no supported hook to rename
// them, so the build's own files are left alone and the ones meant for people are copied
// out under readable names:
//
//     HAZEL-IDE-RELEASE-v1.0.0-arm64-v8a.apk
//     HAZEL-IDE-BETA-v2.0.0-beta.1-universal.apk
//
// The copy is what CI attaches to a GitHub release, alongside checksums.txt.
// ---------------------------------------------------------------------------
val packagedApkDir = layout.buildDirectory.dir("outputs/packaged")

tasks.register("packageReleaseApks") {
    group = "distribution"
    description = "Copies the release APKs out under HAZEL-IDE-<CHANNEL>-v<version>-<abi>.apk names."
    dependsOn("assembleRelease")

    val sourceDir = layout.buildDirectory.dir("outputs/apk/release")
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
            // app-arm64-v8a-release.apk         -> arm64-v8a
            // app-universal-release-unsigned.apk -> universal
            // app-release.apk                    -> universal
            // Order matters: "-unsigned" is only present when no signing config applied.
            val abi = apk.name
                .removePrefix("app-")
                .removeSuffix(".apk")
                .removeSuffix("-unsigned")
                .removeSuffix("-release")
                .ifBlank { "universal" }

            val target = File(into, "HAZEL-IDE-$channel-v$version-$abi.apk")
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
            logger.lifecycle("  NOTE: unsigned — set up keystore.properties to produce installable APKs.")
        }
        logger.lifecycle("")
    }
}

import java.io.File
import java.util.Properties

plugins {
    // AGP 9 has built-in Kotlin support, so there is no separate Kotlin plugin.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Signing details live outside the repo. The file is gitignored; see
// keystore.properties.example and the `keystore` mise task.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// A task class rather than a doLast block: a lambda in a Kotlin build script
// captures the script object, which the configuration cache cannot serialize.
// It re-reads the file at execution time so the message always matches reality.
abstract class VerifyReleaseSigning : DefaultTask() {
    @get:Input
    abstract val propertiesPath: Property<String>

    /** Relative `storeFile` values resolve against this, as the signing config does. */
    @get:Input
    abstract val resolveStoreAgainst: Property<String>

    @TaskAction
    fun verify() {
        val propertiesFile = File(propertiesPath.get())
        if (!propertiesFile.exists()) {
            throw GradleException(
                """
                Cannot build a release APK: keystore.properties is missing.

                Create it at $propertiesFile by copying keystore.properties.example,
                or generate both the keystore and the file with:

                    mise run keystore

                The file is gitignored and the keystore itself lives outside the repo.
                """.trimIndent(),
            )
        }
        val properties = Properties().apply { propertiesFile.inputStream().use { load(it) } }
        val blank = REQUIRED.filter { properties.getProperty(it).isNullOrBlank() }
        if (blank.isNotEmpty()) {
            throw GradleException(
                "Cannot build a release APK: $propertiesFile is missing " +
                    "${blank.joinToString(", ")}. See keystore.properties.example.",
            )
        }
        val store = File(resolveStoreAgainst.get()).resolve(properties.getProperty("storeFile"))
        if (!store.exists()) {
            throw GradleException(
                "Cannot build a release APK: the keystore at $store does not exist. " +
                    "Generate one with `mise run keystore`.",
            )
        }
    }

    private companion object {
        val REQUIRED = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    }
}

val verifyReleaseSigning = tasks.register<VerifyReleaseSigning>("verifyReleaseSigning") {
    description = "Fails with an explanation when release signing details are missing."
    propertiesPath.set(keystorePropertiesFile.absolutePath)
    resolveStoreAgainst.set(rootProject.projectDir.absolutePath)
}

// preReleaseBuild is AGP's anchor task for the release variant, so the check runs
// before anything is compiled or packaged.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyReleaseSigning)
}

android {
    namespace = "ninja.notnot.countdown"
    // Android 17. Read from the installed platform, not guessed:
    // .android-sdk/platforms/android-37.1/source.properties
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "ninja.notnot.countdown"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            // rootProject.file, matching how verifyReleaseSigning resolves it.
            keystoreProperties.getProperty("storeFile")?.let { storeFile = rootProject.file(it) }
            storePassword = keystoreProperties.getProperty("storePassword")
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
            // v3 so the key can be rotated later without orphaning installs.
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }

    // 17, not the JDK's own 21: this is the bytecode level d8 has to swallow, and
    // it is independent of the JDK the build runs on.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

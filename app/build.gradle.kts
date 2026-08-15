import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.util.Properties
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

plugins {
    // AGP 9 has built-in Kotlin support, so there is no separate Kotlin plugin.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * The release keystore password: from `pass`, then $COUNTDOWN_KEYSTORE_PASSWORD,
 * then a repo-root keystore.properties. Absent when none of them has it, so a
 * debug build still configures and verifyReleaseSigning does the complaining.
 *
 * A ValueSource rather than a plain read: Gradle re-runs it when it checks a
 * configuration cache entry, so a changed password is picked up instead of a
 * stale one being reused.
 */
abstract class ReleaseSigningPassword : ValueSource<String, ReleaseSigningPassword.Parameters> {
    interface Parameters : ValueSourceParameters {
        val keystorePropertiesPath: Property<String>
    }

    override fun obtain(): String? =
        resolve(File(parameters.keystorePropertiesPath.get())).password

    companion object {
        const val PASS_ENTRY = "countdown/release-keystore/password"
        const val ENV_VAR = "COUNTDOWN_KEYSTORE_PASSWORD"

        /** Where the password came from. The name is safe to print; the password is not. */
        class Resolved(val password: String?, val source: String)

        fun resolve(keystoreProperties: File): Resolved {
            fromPass()?.let { return Resolved(it, "pass ($PASS_ENTRY)") }
            System.getenv(ENV_VAR)?.ifBlank { null }?.let { return Resolved(it, "\$$ENV_VAR") }
            if (keystoreProperties.isFile) {
                val properties = Properties().apply {
                    keystoreProperties.inputStream().use { load(it) }
                }
                properties.getProperty("storePassword")?.ifBlank { null }
                    ?.let { return Resolved(it, keystoreProperties.name) }
            }
            return Resolved(null, "nowhere")
        }

        private fun fromPass(): String? {
            val storeDir = System.getenv("PASSWORD_STORE_DIR")
                ?: "${System.getProperty("user.home")}/.password-store"
            // No entry, no process: a machine without one stays fast and gpg
            // stays out of it.
            if (!File(storeDir, "$PASS_ENTRY.gpg").isFile) return null
            return try {
                val process = ProcessBuilder("pass", "show", PASS_ENTRY)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .apply {
                        // gpg fails rather than opening a passphrase prompt that
                        // no one is there to answer. Unlock the key once outside
                        // the build and the agent serves it from then on.
                        environment()["PASSWORD_STORE_GPG_OPTS"] = "--batch --pinentry-mode error"
                    }
                    .start()
                process.outputStream.close()
                // Read on another thread: a gpg that blocks anyway would hold the
                // pipe open and the timeout below would never be reached.
                val output = CompletableFuture.supplyAsync {
                    process.inputStream.bufferedReader().use { it.readText() }
                }
                if (!process.waitFor(GPG_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    return null
                }
                if (process.exitValue() != 0) return null
                output.get(GPG_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .lineSequence().first().ifBlank { null }
            } catch (_: Exception) {
                // pass missing, gpg locked, anything else: the caller falls
                // through to the next source and reports what it tried.
                null
            }
        }

        private const val GPG_TIMEOUT_SECONDS = 10L
    }
}

// Where the key lives and what it is called are not secret, so they stay plain
// build config. A keystore.properties can override them to point the build at a
// keystore someone already has.
val keystorePropertiesFile = rootProject.layout.projectDirectory.file("keystore.properties")
val keystoreProperties = Properties().apply {
    providers.fileContents(keystorePropertiesFile).asText.orNull?.let { load(it.reader()) }
}
val releaseKeystore = rootProject.file(
    keystoreProperties.getProperty("storeFile")
        ?: "${providers.systemProperty("user.home").get()}/.android-keystores/countdown-release.jks",
)
val releaseKeyAlias: String = keystoreProperties.getProperty("keyAlias") ?: "countdown"

// AGP's signing DSL takes a String, not a Provider, so this one read happens
// while the script is evaluated — there is no hook that hands AGP a password
// later. The ValueSource is what keeps that honest: the configuration cache is
// still reused, and Gradle re-runs the lookup when it checks the entry.
// verifyReleaseSigning resolves it again itself, at execution time, so its
// messages describe the machine as it is now.
val releasePassword = providers.of(ReleaseSigningPassword::class) {
    parameters.keystorePropertiesPath.set(keystorePropertiesFile.asFile.absolutePath)
}

// A task class rather than a doLast block: a lambda in a Kotlin build script
// captures the script object, which the configuration cache cannot serialize.
// It resolves the password itself, at execution time, so the message always
// matches reality — and it opens the keystore, so a wrong password fails here
// with an explanation rather than deep inside packaging.
abstract class VerifyReleaseSigning : DefaultTask() {
    @get:Input
    abstract val keystorePath: Property<String>

    @get:Input
    abstract val keyAlias: Property<String>

    @get:Input
    abstract val propertiesPath: Property<String>

    @TaskAction
    fun verify() {
        val keystore = File(keystorePath.get())
        if (!keystore.isFile) {
            throw GradleException(
                """
                Cannot build a release APK: there is no keystore at $keystore.

                Generate one, and store its password in pass, with:

                    mise run keystore
                """.trimIndent(),
            )
        }
        val resolved = ReleaseSigningPassword.resolve(File(propertiesPath.get()))
        val password = resolved.password ?: throw GradleException(
            """
            Cannot build a release APK: no password for $keystore.

            Looked, in order, in:
              the pass entry ${ReleaseSigningPassword.PASS_ENTRY}
              $${ReleaseSigningPassword.ENV_VAR}
              ${propertiesPath.get()} (storePassword)

            Put it in pass with `mise run keystore`. If pass has it but gpg could
            not unlock the key, run `pass show ${ReleaseSigningPassword.PASS_ENTRY}`
            once and let the agent cache it; the build never asks for a
            passphrase itself. On a machine with no pass, set
            $${ReleaseSigningPassword.ENV_VAR}.
            """.trimIndent(),
        )
        val store = KeyStore.getInstance("PKCS12")
        try {
            keystore.inputStream().use { store.load(it, password.toCharArray()) }
        } catch (_: IOException) {
            throw GradleException(
                "Cannot build a release APK: the password from ${resolved.source} " +
                    "does not open $keystore.",
            )
        }
        if (!store.containsAlias(keyAlias.get())) {
            throw GradleException(
                "Cannot build a release APK: $keystore has no key named '${keyAlias.get()}'.",
            )
        }
    }
}

val verifyReleaseSigning = tasks.register<VerifyReleaseSigning>("verifyReleaseSigning") {
    description = "Fails with an explanation when release signing details are missing."
    keystorePath.set(releaseKeystore.absolutePath)
    keyAlias.set(releaseKeyAlias)
    propertiesPath.set(keystorePropertiesFile.asFile.absolutePath)
}

// preReleaseBuild is AGP's anchor task for the release variant, so the check runs
// before anything is compiled or packaged.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyReleaseSigning)
}

// The git tag is what a version is: the release workflow turns a v0.2.0 tag into
// -PversionName=0.2.0. A local build has no tag behind it, so it gets the dev
// version below.
val taggedVersionName: String? = providers.gradleProperty("versionName").orNull

/**
 * The whole number Android compares when deciding whether one APK replaces
 * another, so it has to rise with every release. Two digits each for minor and
 * patch: 0.2.0 is 200 and 1.0.0 is 10000, which stays in order as long as
 * neither of them reaches 100.
 */
fun versionCodeOf(versionName: String): Int {
    val match = Regex("""(\d+)\.(\d+)\.(\d+)""").matchEntire(versionName)
        ?: throw GradleException(
            "versionName '$versionName' is not MAJOR.MINOR.PATCH. Tag releases as v1.2.3.",
        )
    val (major, minor, patch) = match.destructured.toList().map(String::toInt)
    if (minor > 99 || patch > 99) {
        throw GradleException(
            "versionName '$versionName' has a minor or patch part over 99, which would " +
                "give it the same versionCode as a later version.",
        )
    }
    val code = major * 10_000 + minor * 100 + patch
    if (code < 1) {
        throw GradleException("versionName '$versionName' leaves no room below it. Start at 0.0.1.")
    }
    return code
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
        // A local build's versionCode of 1 is below every release's, so a release
        // APK installs over a local one and not the other way round.
        versionCode = taggedVersionName?.let(::versionCodeOf) ?: 1
        versionName = taggedVersionName ?: "0.0.0-dev"
    }

    signingConfigs {
        create("release") {
            storeFile = releaseKeystore
            keyAlias = releaseKeyAlias
            // The store is PKCS12, so one password covers the store and the key.
            storePassword = releasePassword.orNull
            keyPassword = releasePassword.orNull
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
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    // The back arrow, which is the only icon the app draws from a library.
    // Material 3 used to bring the core icons with it and no longer does, so
    // they are asked for by name. Core, not extended: extended is thousands of
    // icons for the one.
    implementation(libs.compose.material.icons.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

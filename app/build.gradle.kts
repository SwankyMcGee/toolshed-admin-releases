// The Learning Toolshed — Toolshed Admin
// One module, no server of its own: it talks to the Ideas Engine
// Apps Script (SwankyMcGee.github.io/ideas-engine), signed in with
// Google (API 6) and, for older deployments, with a shared key.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/* Where the signing keystore lives. A Gradle property wins, then an
   environment variable (what CI sets), then the checkout-local
   default. Returns null when nothing is actually there — an absent
   keystore leaves the release config unset, which is what lets the
   test job run on Linux without the secret. */
fun keystorePath(): String? {
    val candidate = providers.gradleProperty("toolshedKeystore").orNull
        ?: providers.environmentVariable("TOOLSHED_KEYSTORE").orNull
        ?: "C:/Users/Tanner/Desktop/Toolshed App/toolshed-release.keystore"
    return if (File(candidate).exists()) candidate else null
}

/* A Gradle property wins over an environment variable, which is what CI
   sets from a repository secret. Null when neither is defined.

   There is deliberately no default for any of these. A password written
   into this file is public the moment the repo is, and this repo is:
   anyone could read it. Without the keystore file it signs nothing, but
   it is still half of a two-part secret published for free, and the
   moment that file ever escapes a laptop or a backup the pair is
   complete. Android trusts the signing key to decide whether an APK is
   a legitimate update, so a complete pair means someone could push an
   update to every teacher's phone. */
fun secret(envName: String, gradleProperty: String): String? =
    providers.gradleProperty(gradleProperty).orNull
        ?: providers.environmentVariable(envName).orNull
        ?.takeIf { it.isNotBlank() }

android {
    namespace = "com.learningtoolshed.admin"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.learningtoolshed.admin"
        minSdk = 24
        targetSdk = 35
        versionCode = 16
        versionName = "1.15"
    }

    signingConfigs {
        /* Local builds use the keystore beside the project; CI passes the
           same values in as Gradle properties or environment variables, so
           the signing config has one definition and two sources.

           The config is only created when the keystore *and* all three
           secrets are present. A missing password must not quietly fall
           back to something guessable — it must stop the build, because
           an unsigned APK can't be installed over a signed one anyway. */
        val keystore = keystorePath()
        val storePassword = secret("TOOLSHED_KEYSTORE_PASSWORD", "toolshedKeystorePassword")
        val keyAlias = secret("TOOLSHED_KEY_ALIAS", "toolshedKeyAlias")
        val keyPassword = secret("TOOLSHED_KEY_PASSWORD", "toolshedKeyPassword")

        if (keystore != null && storePassword != null && keyAlias != null && keyPassword != null) {
            create("release") {
                storeFile = file(keystore)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        } else if (keystore != null) {
            logger.warn(
                "Found the keystore at $keystore but not its passwords. " +
                    "Set TOOLSHED_KEYSTORE_PASSWORD, TOOLSHED_KEY_ALIAS and " +
                    "TOOLSHED_KEY_PASSWORD (env), or toolshedKeystorePassword / " +
                    "toolshedKeyAlias / toolshedKeyPassword in ~/.gradle/gradle.properties. " +
                    "Release builds will be UNSIGNED until then."
            )
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    /* Google sign-in (API 6). Credential Manager owns the account
       picker and the silent re-auth; the Google ID library turns what
       comes back into the ID token the backend verifies. Three small
       dependencies rather than play-services-auth, which would drag the
       whole of Play services in for one button. */
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")

    /* The pure logic — Stats, UsageStats, the registry lookups — is
       plain Kotlin with no Android in it, so it can be tested on the
       JVM in a second rather than on a device. */
    testImplementation("junit:junit:4.13.2")
}
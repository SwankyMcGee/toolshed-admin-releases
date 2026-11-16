// The Learning Toolshed — Toolshed Admin
// One module, no server of its own: it talks to the Ideas Engine
// Apps Script (SwankyMcGee.github.io/ideas-engine) with a shared key.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/* Where the signing keystore lives. A Gradle property wins, then an
   environment variable (what CI sets), then the checkout-local
   default. Keeping it in one place means the release APK is signed the
   same way on a laptop and in a workflow. */
fun keystorePath(): String =
    providers.gradleProperty("toolshedKeystore").orNull
        ?: providers.environmentVariable("TOOLSHED_KEYSTORE").orNull
        ?: "C:/Users/Tanner/Desktop/Toolshed App/toolshed-release.keystore"

fun secret(envName: String, fallback: String): String =
    providers.gradleProperty(envName.lowercase().replace('_', '.')).orNull
        ?: providers.environmentVariable(envName).orNull
        ?: fallback

android {
    namespace = "com.learningtoolshed.admin"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.learningtoolshed.admin"
        minSdk = 24
        targetSdk = 35
    versionCode = 8
    versionName = "1.7"
    }

    signingConfigs {
        create("release") {
            /* Local builds use the keystore beside the project; CI passes
               the same values in as Gradle properties or environment
               variables, so the signing config has one definition and
               two sources. */
            storeFile = file(keystorePath())
            storePassword = secret("TOOLSHED_KEYSTORE_PASSWORD", "toolshed123")
            keyAlias = secret("TOOLSHED_KEY_ALIAS", "toolshed")
            keyPassword = secret("TOOLSHED_KEY_PASSWORD", "toolshed123")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
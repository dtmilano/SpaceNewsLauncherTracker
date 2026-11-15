import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Bedrock configuration is supplied per-developer and never committed. `local.properties`
// wins; otherwise we fall back to the standard AWS environment variables so that a shell
// with freshly-minted credentials can build a working APK with no extra setup.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun bedrockSetting(key: String, env: String, default: String = ""): String =
    localProperties.getProperty("bedrock.$key")
        ?: System.getenv(env)
        ?: default

android {
    namespace = "com.example.spacenewslaunchertracker"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.spacenewslaunchertracker"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BEDROCK_REGION", "\"${bedrockSetting("region", "AWS_REGION", "us-west-2")}\"")
        buildConfigField(
            "String",
            "BEDROCK_MODEL_ID",
            "\"${bedrockSetting("modelId", "BEDROCK_MODEL_ID", "us.anthropic.claude-haiku-4-5-20251001-v1:0")}\"",
        )
        buildConfigField("String", "AWS_ACCESS_KEY_ID", "\"${bedrockSetting("accessKeyId", "AWS_ACCESS_KEY_ID")}\"")
        buildConfigField(
            "String",
            "AWS_SECRET_ACCESS_KEY",
            "\"${bedrockSetting("secretAccessKey", "AWS_SECRET_ACCESS_KEY")}\"",
        )
        buildConfigField("String", "AWS_SESSION_TOKEN", "\"${bedrockSetting("sessionToken", "AWS_SESSION_TOKEN")}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/INDEX.LIST",
            )
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.aws.bedrockruntime)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

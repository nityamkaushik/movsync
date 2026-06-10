import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun localProperty(name: String, fallback: String): String {
    return localProperties.getProperty(name)?.takeIf { it.isNotBlank() } ?: fallback
}

android {
    namespace = "com.nityam.movsync"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.nityam.movsync"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "1.5.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"${localProperty("SUPABASE_URL", "https://placeholder.supabase.co")}\""
        )
        buildConfigField(
            "String",
            "SUPABASE_KEY",
            "\"${localProperty("SUPABASE_KEY", "replace-with-supabase-anon-key")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))
    implementation(platform(libs.supabase.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

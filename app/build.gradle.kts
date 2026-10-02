plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// En CI el número de versión sale del número de ejecución del workflow.
val ciBuildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.josenavarro.tvmundo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.josenavarro.tvmundo"
        minSdk = 21
        targetSdk = 36
        versionCode = ciBuildNumber
        versionName = "1.0.$ciBuildNumber"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        // Keystore de depuración incluido en el repo (contraseña "android").
        // Así todas las compilaciones (local y CI) usan la misma firma y la app
        // se puede actualizar en la TV sin desinstalar. No sirve para Google Play.
        create("tvmundo") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("tvmundo")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("tvmundo")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all {
            // Permite probar con la API real: IPTV_API_DIR=carpeta/con/los/json
            it.environment("IPTV_API_DIR", System.getenv("IPTV_API_DIR") ?: "")
        }
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.tv.material)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit)
}

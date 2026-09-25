import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Read keys from local.properties (git-ignored, injected by CI) or -P / env fallbacks.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun secret(name: String): String =
    (localProps.getProperty(name)
        ?: project.findProperty(name) as String?
        ?: System.getenv(name)
        ?: "").trim()

android {
    namespace = "com.xzo.agent"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.xzo.agent"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        vectorDrawables { useSupportLibrary = true }

        ndk {
            // arm32 only – smallest APK, widest legacy-device compatibility
            abiFilters += "armeabi-v7a"
        }

        buildConfigField("String", "GROQ_API_KEY", "\"${secret("GROQ_API_KEY")}\"")
        buildConfigField("String", "OPENROUTER_API_KEY", "\"${secret("OPENROUTER_API_KEY")}\"")
    }

    signingConfigs {
        create("releaseIfAvailable") {
            val storeFilePath = secret("SIGNING_STORE_FILE")
            if (storeFilePath.isNotEmpty() && rootProject.file(storeFilePath).exists()) {
                storeFile = rootProject.file(storeFilePath)
                storePassword = secret("SIGNING_STORE_PASSWORD")
                keyAlias = secret("SIGNING_KEY_ALIAS")
                keyPassword = secret("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // Keep R8 off so the headless CI build is deterministic and never
            // strips reflection-based serialization/Room code.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val sc = signingConfigs.getByName("releaseIfAvailable")
            if (sc.storeFile != null) signingConfig = sc
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/*.kotlin_module"
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.work.runtime)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.animation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // On-device ML: OCR and translation that keep working with no network and no API key.
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.language.id)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}

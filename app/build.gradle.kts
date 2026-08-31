plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.gravijet.daydrop"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gravijet.daydrop"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2.0"
        vectorDrawables.useSupportLibrary = true
    }

    // The release key lives in the repo so that every build - local or CI -
    // produces an APK that installs over the previous one. CI may override it
    // by exporting DAYDROP_KEYSTORE / DAYDROP_KEYSTORE_PASSWORD /
    // DAYDROP_KEY_ALIAS / DAYDROP_KEY_PASSWORD.
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("DAYDROP_KEYSTORE") ?: "daydrop-release.jks")
            storePassword = System.getenv("DAYDROP_KEYSTORE_PASSWORD") ?: "daydrop"
            keyAlias = System.getenv("DAYDROP_KEY_ALIAS") ?: "daydrop"
            keyPassword = System.getenv("DAYDROP_KEY_PASSWORD") ?: "daydrop"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
    }

    testOptions {
        unitTests.all {
            // The content tests read the asset JSON straight off disk.
            it.workingDir = projectDir
            it.testLogging { showStandardStreams = true }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

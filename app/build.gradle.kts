import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.services)
}

val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) {
        versionPropsFile.inputStream().use { stream ->
            load(stream)
        }
    }
}

val vMajor = (versionProps.getProperty("VERSION_MAJOR") ?: "1").toInt()
val vMinor = (versionProps.getProperty("VERSION_MINOR") ?: "0").toInt()
val vPatch = (versionProps.getProperty("VERSION_PATCH") ?: "1").toInt()
val vBuild = (versionProps.getProperty("VERSION_BUILD") ?: "2").toInt()

val semVerCode = vMajor * 10000 + vMinor * 1000 + vPatch * 100 + vBuild
val semVerName = "$vMajor.$vMinor.$vPatch"

android {
    namespace = "com.videocalls.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.videocalls.app"
        minSdk = 26
        targetSdk = 34
        versionCode = semVerCode
        versionName = semVerName

        buildConfigField("String", "SEMVER_NAME", "\"$semVerName\"")
        buildConfigField("int", "BUILD_NUMBER", "$vBuild")

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    signingConfigs {
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this
            if (output is com.android.build.gradle.internal.api.BaseVariantOutputImpl) {
                output.outputFileName = "videocalls-v${semVerName}-${variant.buildType.name}.apk"
            }
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
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
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.play.services.auth)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.messaging.ktx)

    // WebRTC
    implementation(libs.stream.webrtc)

    // Libphonenumber
    implementation(libs.google.libphonenumber)

    // Coil
    implementation(libs.coil.compose)

    // ML Kit Face Detection
    implementation(libs.mlkit.face.detection)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

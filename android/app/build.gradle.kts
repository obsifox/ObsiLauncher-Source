import java.util.Properties
import com.android.build.api.variant.impl.VariantOutputImpl

// ObsiLauncher release identity (bump here and in defaultConfig)
val APP_VERSION_NAME = "1.13.0"
val APP_VERSION_CODE = 11300

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ObsiLauncher is free software (GPL-3.0-or-later) and ships its release keystore
// publicly so anyone can reproduce signed builds — see android/KEYSTORE.md.
val keystoreProps = Properties().apply {
    setProperty("store_password", "ObsiFox#1.2.0-free-launcher")
    setProperty("key_password", "ObsiFox#1.2.0-free-launcher")
}

android {
    namespace = "studio.obsifox.obsilauncher"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "studio.obsifox.obsilauncher"
        minSdk = 26
        targetSdk = 34
        versionCode = APP_VERSION_CODE
        versionName = APP_VERSION_NAME

        ndk {
            // Deliverable APK is arm64-v8a; add more ABIs here for wider builds.
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            ndkBuild {
                arguments += "NDK_APPLICATION_MK:=src/main/cpp/Application.mk"
            }
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/cpp/Android.mk")
        }
    }
    ndkVersion = "27.2.12479018"

    signingConfigs {
        create("releaseBuild") {
            storeFile = file("obsilauncher.jks")
            storePassword = keystoreProps.getProperty("store_password")
            keyAlias = "obsilauncher"
            keyPassword = keystoreProps.getProperty("key_password")
        }
    }

    buildTypes {
        release {
            // R8 shrinking needs >3 GB heap; keep the build reproducible on modest
            // machines instead. Code is small enough that shrinking saves little.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("releaseBuild")
        }
        debug {
            signingConfig = signingConfigs.getByName("releaseBuild")
        }
    }

    packaging {
        jniLibs {
            // Extract native libraries at install time so the runtime pack can
            // find our own bridge .so through applicationInfo.nativeLibraryDir.
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // AGP 9 has built-in Kotlin support; jvmTarget follows the Java target automatically.
    buildFeatures {
        compose = true
        buildConfig = true
        prefab = true
    }
}

// name the deliverable ObsiLauncher-<version>-<abi>.apk
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = if (project.findProperty("obsi_abi") != null) project.property("obsi_abi") as String else "arm64-v8a"
            (output as VariantOutputImpl).outputFileName.set("ObsiLauncher-$APP_VERSION_NAME-$abi.apk")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // exit() interception for the in-process game JVM (Pojav/Zalith exithook)
    implementation("com.bytedance:bytehook:1.0.10")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.coroutines.android)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.coil.compose)
}

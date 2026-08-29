plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.nobodyiran.nobodyvpn"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nobodyiran.nobodyvpn"
        minSdk = 29
        targetSdk = 37
        versionCode = 2
        versionName = "0.6.0-beta"

        // Optional per-ABI build: ./gradlew assembleRelease -Pabi=arm64-v8a
        (project.findProperty("abi") as? String)?.let { abi ->
            ndk { abiFilters.add(abi) }
        }
    }

    signingConfigs {
        create("release") {
            // Keystore is intentionally NOT committed. If the file exists, use it
            // (override credentials via -P properties when needed).
            val ks = file("nobodyvpn.keystore")
            if (ks.exists()) {
                storeFile = ks
                storePassword = (project.findProperty("NB_STORE_PASSWORD") as String?) ?: "nobodyvpn123"
                keyAlias = (project.findProperty("NB_KEY_ALIAS") as String?) ?: "nobodyvpn"
                keyPassword = (project.findProperty("NB_KEY_PASSWORD") as String?) ?: "nobodyvpn123"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (file("nobodyvpn.keystore").exists()) {
                signingConfig = signingConfigs.getByName("release")
            } // otherwise produce an unsigned APK (CI builds use assembleDebug)
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // For per-ABI APKs enable splits:
    // splits { abi { isEnable = true; isUniversalApk = false; reset(); include("arm64-v8a") } }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.navigation:navigation-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    val camerax = "1.6.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
}

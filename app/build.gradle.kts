// My Control Panel — version lives here (versionName) and is shown in Settings → About and the dashboard footer.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.mycontrolpanel"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.mycontrolpanel"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // One fixed key for every build, so a new APK installs over the old one and keeps your data.
    signingConfigs {
        create("panel") {
            storeFile = rootProject.file("keystore/panel.jks")
            storePassword = "mycontrolpanel"
            keyAlias = "panel"
            keyPassword = "mycontrolpanel"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("panel")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("panel")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    androidTestImplementation(bom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

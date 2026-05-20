plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.floatingbuttonapp"
    compileSdk = 35 

    defaultConfig {
        applicationId = "com.example.floatingbuttonapp"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Ajoute ".test" à l'ID de l'app pour ne pas écraser l'originale
            applicationIdSuffix = ".test"
            versionNameSuffix = "-TEST"
            
            // On définit une variable pour le nom de l'app
            manifestPlaceholders["appLabel"] = "Assistant (TEST)"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            manifestPlaceholders["appLabel"] = "Mon Assistant"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

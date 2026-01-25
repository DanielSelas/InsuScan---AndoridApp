plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")

}

android {
    namespace = "com.example.insuscan"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.insuscan"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Navigation Component
    implementation(libs.androidx.navigation.fragment.ktx.v296)
    implementation(libs.androidx.navigation.ui.ktx.v296)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ARCore
    implementation(libs.google.ar.core)

    // OpenCV
    implementation(libs.opencv.android)

    // Retrofit - HTTP client
    implementation(libs.retrofit)
    implementation(libs.converter.gson)

    // OkHttp - for logging and interceptors
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)

    // Gson - JSON parsing
    implementation(libs.gson)

    // Coroutines - async calls
    implementation(libs.kotlinx.coroutines.android)

    // ViewModel + LiveData
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    // Firebase Auth
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.play.services.auth)

    implementation(libs.firebase.storage.ktx)
    // Glide
    implementation(libs.glide)

    // Paging
    implementation(libs.androidx.paging.runtime)
}
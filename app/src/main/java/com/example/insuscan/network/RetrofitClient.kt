package com.example.insuscan.network

// OS imports (to detect if running on emulator)
import android.os.Build

// Import config created in Gradle (for 127.0.0.1 address)
import com.example.insuscan.BuildConfig

// Networking imports (OkHttp + Retrofit)
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // Logger setup to view traffic in Logcat
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // Client setup with 30-second timeouts
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // --- Logic to determine the correct Base URL ---
    private val currentBaseUrl: String
        get() {
            // Comprehensive check for emulator environment
            val isEmulator = Build.FINGERPRINT.contains("generic") ||
                    Build.FINGERPRINT.contains("unknown") ||
                    Build.MODEL.contains("google_sdk") ||
                    Build.MODEL.contains("Emulator") ||
                    Build.MODEL.contains("Android SDK built for x86") ||
                    Build.MANUFACTURER.contains("Genymotion") ||
                    (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
                    "google_sdk" == Build.PRODUCT

            return if (isEmulator) {
                // If emulator -> Use Android's special localhost alias
                "http://10.0.2.2:9693/"
            } else {
                // If real device -> Use local address (via USB/ADB)
                BuildConfig.BASE_URL
            }
        }

    // Create Retrofit instance with the selected URL
    private val retrofit = Retrofit.Builder()
        .baseUrl(currentBaseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    // Create the API interface
    val api: InsuScanApi = retrofit.create(InsuScanApi::class.java)
}
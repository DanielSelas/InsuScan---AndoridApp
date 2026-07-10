package com.example.insuscan.network

import android.os.Build
import com.example.insuscan.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Provides a configured Retrofit instance for communicating with the InsuScan backend.
 *
 * The client uses a local emulator URL when running on an Android emulator,
 * and the configured production/development base URL on physical devices.
 * It also applies HTTP logging and timeout settings suitable for image analysis
 * and network operations that may take longer than a standard request.
 */
object RetrofitClient {
    private const val EMULATOR_BASE_URL = "http://10.0.2.2:9693/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val currentBaseUrl: String
        get() {
            val isEmulator = Build.FINGERPRINT.contains("generic") ||
                    Build.FINGERPRINT.contains("unknown") ||
                    Build.MODEL.contains("google_sdk") ||
                    Build.MODEL.contains("Emulator") ||
                    Build.MODEL.contains("Android SDK built for x86") ||
                    Build.MANUFACTURER.contains("Genymotion") ||
                    (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
                    "google_sdk" == Build.PRODUCT

            return if (isEmulator) EMULATOR_BASE_URL else BuildConfig.BASE_URL
        }

    private val retrofit = Retrofit.Builder()
        .baseUrl(currentBaseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: InsuScanApi = retrofit.create(InsuScanApi::class.java)
}
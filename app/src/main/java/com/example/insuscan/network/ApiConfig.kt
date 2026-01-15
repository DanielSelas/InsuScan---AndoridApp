package com.example.insuscan.network

object ApiConfig {
    // Change this based on your testing environment:
    // - Emulator: "http://10.0.2.2:9693/"
    // - Real device (same WiFi): "http://YOUR_PC_IP:9693/"
    // - Production: "https://your-server.com/"

    private const val EMULATOR_URL = "http://10.0.2.2:9693/"
    private const val LOCAL_NETWORK_URL = "http://192.168.1.100:9693/" // Update with your IP

    // Set to true when testing on emulator
    private const val USE_EMULATOR = true

    val BASE_URL: String
        get() = if (USE_EMULATOR) EMULATOR_URL else LOCAL_NETWORK_URL

    const val SYSTEM_ID = "insuscan"
}
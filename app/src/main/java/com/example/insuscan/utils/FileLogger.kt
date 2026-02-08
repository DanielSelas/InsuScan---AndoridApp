package com.example.insuscan.utils

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes logs to a local file "insuscan_client_flow.txt" in the app's private storage.
 * This allows the user to export the file for debugging.
 */
object FileLogger {

    private const val LOG_FILE_NAME = "insuscan_client_flow.txt"
    private val TIME_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var logFile: File? = null

    fun init(context: Context) {
        logFile = File(context.getExternalFilesDir(null), LOG_FILE_NAME)
    }

    @Synchronized
    fun append(message: String) {
        val file = logFile ?: return
        try {
            PrintWriter(FileWriter(file, true)).use { out ->
                val timestamp = TIME_FMT.format(Date())
                out.println("[$timestamp] $message")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun log(tag: String, message: String) {
        append("[$tag] $message")
    }

    fun getLogFilePath(): String {
        return logFile?.absolutePath ?: "Not initialized"
    }
    
    fun clear() {
        val file = logFile ?: return
        try {
            PrintWriter(FileWriter(file, false)).use { it.print("") }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

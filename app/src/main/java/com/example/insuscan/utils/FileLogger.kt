package com.example.insuscan.utils

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Appends timestamped log lines to `insuscan_client_flow.txt` in the app's external files dir.
 * The file can be pulled from the device for debugging without a USB connection.
 *
 * Must call [init] once (e.g. in Application.onCreate) before any other method.
 */
object FileLogger {

    private const val LOG_FILE_NAME = "insuscan_client_flow.txt"
    private val TIME_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var logFile: File? = null

    /** Initialises the log file path. Safe to call multiple times. */
    fun init(context: Context) {
        logFile = File(context.getExternalFilesDir(null), LOG_FILE_NAME)
    }

    /** Appends a raw [message] line with a timestamp prefix. Thread-safe. */
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

    /** Appends a tagged log line in the format `[tag] message`. */
    fun log(tag: String, message: String) {
        append("[$tag] $message")
    }

    /** Returns the absolute path of the log file, or `"Not initialized"` if [init] was not called. */
    fun getLogFilePath(): String {
        return logFile?.absolutePath ?: "Not initialized"
    }

    /** Truncates the log file to zero bytes. */
    fun clear() {
        val file = logFile ?: return
        try {
            PrintWriter(FileWriter(file, false)).use { it.print("") }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

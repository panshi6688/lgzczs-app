package com.lgzczs.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val timestamp: Long,
    val type: LogType,
    val source: String,
    val message: String
)

enum class LogType {
    JS_DEBUG, JS_LOG, JS_WARN, JS_ERROR,
    NETWORK_REQ, NETWORK_RESP,
    HTTP_ERROR, ERROR
}

object WebViewDiagnostics {
    private val logs = mutableListOf<LogEntry>()
    private const val MAX_LOGS = 500

    fun add(type: LogType, source: String, message: String) {
        synchronized(logs) {
            if (logs.size >= MAX_LOGS) logs.removeAt(0)
            logs.add(LogEntry(System.currentTimeMillis(), type, source, message))
        }
    }

    fun getLogs(): List<LogEntry> = synchronized(logs) { logs.toList() }

    fun clear() { synchronized(logs) { logs.clear() } }

    fun getLogsText(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return synchronized(logs) {
            logs.joinToString("\n") { entry ->
                val time = sdf.format(Date(entry.timestamp))
                "[${entry.type.name}] [$time] ${entry.source} | ${entry.message}"
            }
        }
    }

    fun copyToClipboard(context: Context) {
        val text = getLogsText()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("WebView Logs", text))
    }
}

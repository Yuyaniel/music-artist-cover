package com.musictag.artistcover.data

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel { INFO, WARN, ERROR }

data class LogEntry(
    val time: String,
    val level: LogLevel,
    val message: String,
)

/** 运行日志：环形缓冲，供日志页直接订阅。 */
object AppLog {
    private const val MAX_ENTRIES = 400

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    val entries = mutableStateListOf<LogEntry>()

    fun i(message: String) = append(LogLevel.INFO, message)

    fun w(message: String) = append(LogLevel.WARN, message)

    fun e(message: String) = append(LogLevel.ERROR, message)

    fun clear() {
        entries.clear()
    }

    @Synchronized
    private fun append(level: LogLevel, message: String) {
        if (entries.size >= MAX_ENTRIES) {
            entries.removeAt(0)
        }
        entries.add(LogEntry(timeFormat.format(Date()), level, message))
    }
}

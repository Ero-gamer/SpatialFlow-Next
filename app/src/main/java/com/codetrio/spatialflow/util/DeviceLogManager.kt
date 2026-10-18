package com.codetrio.spatialflow.util

import android.os.Process
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicLong

enum class LogcatLevel(val letter: Char, val label: String, val priority: Int) {
    ALL('*', "All", 0),
    DEBUG('D', "Debug", 3),
    INFO('I', "Info", 4),
    WARN('W', "Warn", 5),
    ERROR('E', "Error", 6);

    companion object {
        fun fromChar(c: Char): LogcatLevel = when (c) {
            'V', 'D' -> DEBUG
            'I' -> INFO
            'W' -> WARN
            'E', 'A' -> ERROR
            else -> DEBUG
        }
    }
}

data class LogcatEntry(
    val id: Long,
    val timestamp: String,
    val pid: Int,
    val level: LogcatLevel,
    val tag: String,
    val message: String,
    val rawLine: String
)

/**
 * Lightweight, zero-lag logcat streamer filtering specifically for this app process.
 * Eliminates OS-wide log flooding and heavy regex parsing to deliver silky smooth UI performance.
 */
object DeviceLogManager {

    private const val MAX_LOG_ENTRIES = 500
    val myPid: Int = Process.myPid()

    private val idGenerator = AtomicLong(0)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var streamJob: Job? = null

    private val entryBuffer = ArrayList<LogcatEntry>(MAX_LOG_ENTRIES)
    private val _logsFlow = MutableStateFlow<List<LogcatEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogcatEntry>> = _logsFlow.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    fun startLogging() {
        if (streamJob?.isActive == true) return

        streamJob = scope.launch {
            try {
                // Filter natively in logcat daemon via --pid to eliminate OS-wide log flooding
                val process = try {
                    ProcessBuilder("logcat", "-v", "time", "--pid=$myPid").start()
                } catch (e: Exception) {
                    ProcessBuilder("logcat", "-v", "time").start()
                }

                val reader = BufferedReader(InputStreamReader(process.inputStream), 4096)
                val pendingBatch = ArrayList<LogcatEntry>(32)
                var lastEmitTime = System.currentTimeMillis()

                while (isActive) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue

                    val entry = parseLineFast(line)
                    if (entry != null && !_isPaused.value) {
                        pendingBatch.add(entry)
                    }

                    val now = System.currentTimeMillis()
                    // Batch emissions at 400ms intervals or when 30 items accumulate
                    if (pendingBatch.size >= 30 || (now - lastEmitTime > 400 && pendingBatch.isNotEmpty())) {
                        synchronized(entryBuffer) {
                            entryBuffer.addAll(pendingBatch)
                            if (entryBuffer.size > MAX_LOG_ENTRIES) {
                                val overflow = entryBuffer.size - MAX_LOG_ENTRIES
                                entryBuffer.subList(0, overflow).clear()
                            }
                            _logsFlow.value = ArrayList(entryBuffer)
                        }
                        pendingBatch.clear()
                        lastEmitTime = now
                    }
                }
            } catch (e: Exception) {
                // Stream ended
            }
        }
    }

    /**
     * Fast parser for format: "09-28 00:27:34.123 D/AudioTrack( 1234): Message"
     * Avoids regex compilation on the hot loop.
     */
    private fun parseLineFast(line: String): LogcatEntry? {
        if (line.length < 22) return null
        val levelChar = line.getOrNull(19) ?: return null
        if (line.getOrNull(20) != '/') return null

        val time = if (line.length >= 18) line.substring(6, 18) else ""
        val level = LogcatLevel.fromChar(levelChar)

        // Find tag ending at '(' or ':'
        val tagStart = 21
        val colonIdx = line.indexOf(':', tagStart)
        if (colonIdx == -1) return null

        val parenIdx = line.indexOf('(', tagStart)
        val tagEnd = if (parenIdx in (tagStart + 1) until colonIdx) parenIdx else colonIdx
        val tag = line.substring(tagStart, tagEnd).trim()
        val message = line.substring(colonIdx + 1).trim()

        return LogcatEntry(
            id = idGenerator.incrementAndGet(),
            timestamp = time,
            pid = myPid,
            level = level,
            tag = tag,
            message = message,
            rawLine = line
        )
    }

    fun togglePause() {
        _isPaused.value = !_isPaused.value
    }

    fun clear() {
        synchronized(entryBuffer) {
            entryBuffer.clear()
            _logsFlow.value = emptyList()
        }
    }

    fun getExportText(entries: List<LogcatEntry>): String {
        return buildString {
            appendLine("=== SpatialFlow App Logs (PID: $myPid) ===")
            appendLine("Entries: ${entries.size}")
            appendLine()
            entries.forEach { entry ->
                appendLine("${entry.timestamp} [${entry.level.letter}] ${entry.tag}: ${entry.message}")
            }
        }
    }
}

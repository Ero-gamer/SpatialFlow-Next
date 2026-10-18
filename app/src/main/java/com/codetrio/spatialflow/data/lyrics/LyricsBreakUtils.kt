package com.codetrio.spatialflow.data.lyrics

/**
 * Utility to automatically detect and inject instrumental breaks into synchronized lyrics.
 *
 * Detects:
 * 1. Intro breaks: when the first vocal entry starts >= 5000ms into the song.
 * 2. Inter-verse gaps: when any gap between consecutive vocal lines >= 5000ms.
 * 3. Outro breaks: when the trailing time after the last vocal line >= 5000ms.
 */
object LyricsBreakUtils {

    private const val INSTRUMENTAL_GAP_THRESHOLD_MS = 5000L
    private const val INSTRUMENTAL_INTRO_START_MS = 1000L
    private const val INSTRUMENTAL_OUTRO_VOCAL_TAIL_MS = 2500L

    fun insertInstrumentalBreaks(
        entries: List<LyricLine>,
        songDurationMs: Long = 0L,
    ): List<LyricLine> {
        if (entries.isEmpty()) return entries

        // Filter out any existing corrupted or empty non-timed entries
        val cleanEntries = entries.filter { it.startTimeMs >= 0L }
        if (cleanEntries.isEmpty()) return entries

        val result = mutableListOf<LyricLine>()

        // 1. Check for Intro Instrumental Break
        val firstTimedVocal = cleanEntries.firstOrNull { !it.isInterlude && it.content.isNotBlank() }
        if (firstTimedVocal != null) {
            val introGapMs = firstTimedVocal.startTimeMs - INSTRUMENTAL_INTRO_START_MS
            if (introGapMs >= INSTRUMENTAL_GAP_THRESHOLD_MS) {
                result.add(
                    LyricLine(
                        startTimeMs = INSTRUMENTAL_INTRO_START_MS,
                        content = "",
                        isInterlude = true,
                        isWordByWord = false,
                    )
                )
            }
        }

        // 2. Iterate through entries and insert inter-verse gaps if >= 5000ms
        for (i in cleanEntries.indices) {
            val current = cleanEntries[i]
            result.add(current)

            if (i < cleanEntries.size - 1) {
                val next = cleanEntries[i + 1]
                if (!current.isInterlude && !next.isInterlude) {
                    val currentEndMs = if (current.isWordByWord && current.words.isNotEmpty()) {
                        val lastWord = current.words.last()
                        (lastWord.absoluteStartTimeMs + lastWord.durationMs).coerceAtLeast(current.startTimeMs + 2000L)
                    } else {
                        current.startTimeMs + 3000L
                    }

                    val gapMs = next.startTimeMs - currentEndMs
                    if (gapMs >= INSTRUMENTAL_GAP_THRESHOLD_MS) {
                        result.add(
                            LyricLine(
                                startTimeMs = currentEndMs,
                                content = "",
                                isInterlude = true,
                                isWordByWord = false,
                            )
                        )
                    }
                }
            }
        }

        // 3. Check for Outro Instrumental Break
        if (songDurationMs > 0L) {
            val lastVocal = cleanEntries.lastOrNull { !it.isInterlude && it.content.isNotBlank() }
            if (lastVocal != null) {
                val lastVocalEndMs = if (lastVocal.isWordByWord && lastVocal.words.isNotEmpty()) {
                    val lastWord = lastVocal.words.last()
                    (lastWord.absoluteStartTimeMs + lastWord.durationMs).coerceAtLeast(lastVocal.startTimeMs + 2000L)
                } else {
                    lastVocal.startTimeMs + INSTRUMENTAL_OUTRO_VOCAL_TAIL_MS
                }

                val outroDurationMs = songDurationMs - lastVocalEndMs
                if (outroDurationMs >= INSTRUMENTAL_GAP_THRESHOLD_MS) {
                    result.add(
                        LyricLine(
                            startTimeMs = lastVocalEndMs,
                            content = "",
                            isInterlude = true,
                            isWordByWord = false,
                        )
                    )
                }
            }
        }

        return result
    }
}

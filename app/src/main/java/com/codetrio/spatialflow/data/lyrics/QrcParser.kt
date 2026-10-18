package com.codetrio.spatialflow.data.lyrics

import android.util.Log

/**
 * High-performance parser for QQ Music QRC (Quick Response Code) word-by-word synchronized lyrics.
 *
 * Handles:
 * - XML wrapped envelope: <QrcInfos><QrcInfo><LyricInfo><LyricContent="..."
 * - Standard QRC: [startMs,durationMs](wordStart,wordDuration)wordText...
 * - Timestamp QRC: [mm:ss.xx,durationMs](wordStart,wordDuration)wordText...
 * - Bracket variants: (start,dur) and <start,dur>
 * - Leading vs trailing word tag conventions
 * - Filtering metadata/credits header noise (title/artist echo, lyricist/composer credits)
 */
object QrcParser {
    private const val TAG = "QrcParser"
    private const val INTERLUDE_THRESHOLD_MS = 5000L

    private val LYRIC_ATTR_REGEX = Regex("LyricContent\\s*=\\s*[\"']([\\s\\S]*?)[\"']\\s*(?:/?>|[a-zA-Z0-9_]+=)", RegexOption.IGNORE_CASE)
    private val LYRIC_TAG_REGEX = Regex("<LyricContent(?:\\s+[^>]*)?>([\\s\\S]*?)</LyricContent>", RegexOption.IGNORE_CASE)
    private val CDATA_REGEX = Regex("<!\\[CDATA\\[([\\s\\S]*?)\\]\\]>", RegexOption.IGNORE_CASE)
    private val LINE_TAG_REGEX = Regex("^\\[(\\d+)(?:[:.](\\d+)(?:[.:](\\d+))?)?(?:,(\\d+))?\\](.*)$")
    private val SYLLABLE_REGEX = Regex("[\\(<](\\d+),(\\d+)[\\)>]")

    private val CREDIT_KEYWORDS = setOf(
        "词", "作词", "曲", "作曲", "编曲", "和声", "混音", "吉他", "制作人",
        "演唱", "原唱", "翻唱", "后期", "和音", "录音", "策划", "伴奏", "美工",
        "海报", "旁白", "writtenby", "producedby", "composedby", "arrangedby",
        "mixing", "mastering", "vocal", "vocals", "guitar", "bass", "drums",
        "producer", "lyricist", "composer", "arranger", "lyricsby"
    )

    /**
     * Determines whether the content is a QRC document.
     */
    fun isQrc(content: String?): Boolean {
        if (content.isNullOrBlank()) return false
        val trimmed = content.trim()
        if (trimmed.contains("<QrcInfos>", ignoreCase = true) ||
            trimmed.contains("<QrcInfo>", ignoreCase = true) ||
            trimmed.contains("<LyricInfo", ignoreCase = true) ||
            trimmed.contains("LyricContent=", ignoreCase = true) ||
            trimmed.contains("<LyricContent", ignoreCase = true) ||
            trimmed.contains("filetype=\"qrc\"", ignoreCase = true)) {
            return true
        }
        val sample = trimmed.take(800)
        val hasLineTag = sample.contains(Regex("\\[\\d+,\\d+\\]")) ||
                sample.contains(Regex("\\[\\d+:\\d+[.,]\\d+,\\d+\\]"))
        val hasWordTag = sample.contains(Regex("\\(\\d+,\\d+\\)")) ||
                sample.contains(Regex("<\\d+,\\d+>"))
        return hasLineTag && hasWordTag
    }

    /**
     * Parses QRC content into a sorted list of [LyricLine]s with word-by-word timing.
     */
    fun parse(
        rawQrc: String?,
        songDurationMs: Long = 0L,
        songTitle: String? = null,
        artistName: String? = null
    ): List<LyricLine> {
        if (rawQrc.isNullOrBlank()) return emptyList()

        val text = extractLyricContent(rawQrc)
        val lines = text.lines()
        val parsedLines = mutableListOf<LyricLine>()

        var processedCount = 0
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() ||
                line.startsWith("[ti:", ignoreCase = true) ||
                line.startsWith("[ar:", ignoreCase = true) ||
                line.startsWith("[al:", ignoreCase = true) ||
                line.startsWith("[by:", ignoreCase = true) ||
                line.startsWith("[offset:", ignoreCase = true) ||
                line.startsWith("[hash:", ignoreCase = true) ||
                line.startsWith("[sign:", ignoreCase = true) ||
                line.startsWith("[qq:", ignoreCase = true) ||
                line.startsWith("[total:", ignoreCase = true)
            ) {
                continue
            }

            val parsedLine = parseLine(line) ?: continue

            // Drop early noise lines (credits / title / artist echos) in first 5 lines
            if (processedCount < 5 && isMetadataOrCreditLine(parsedLine.content, songTitle, artistName)) {
                processedCount++
                continue
            }

            parsedLines.add(parsedLine)
            processedCount++
        }

        if (parsedLines.isEmpty()) return emptyList()

        parsedLines.sort()
        return insertInterludes(parsedLines)
    }

    private fun extractLyricContent(input: String): String {
        val trimmed = input.trim()
        val rawContent = LYRIC_ATTR_REGEX.find(trimmed)?.groupValues?.get(1)
            ?: LYRIC_TAG_REGEX.find(trimmed)?.groupValues?.get(1)
            ?: trimmed

        val unCdata = CDATA_REGEX.find(rawContent)?.groupValues?.get(1) ?: rawContent

        return unCdata
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&#34;", "\"")
            .replace("&#38;", "&")
            .replace("&#10;", "\n")
            .replace("&#13;", "\r")
            .replace("&#xA;", "\n", ignoreCase = true)
            .replace("&#xD;", "\r", ignoreCase = true)
    }

    private fun parseLine(line: String): LyricLine? {
        val match = LINE_TAG_REGEX.find(line) ?: return null
        val g1 = match.groupValues[1]
        val g2 = match.groupValues[2]
        val g3 = match.groupValues[3]
        val g4 = match.groupValues[4]
        val rest = match.groupValues[5]

        val startTimeMs: Long
        val durationMs: Long

        if (g2.isNotEmpty()) {
            // [mm:ss.xx,dur] format
            val min = g1.toLongOrNull() ?: 0L
            val sec = g2.toLongOrNull() ?: 0L
            val fractionStr = if (g3.isNotEmpty()) g3.padEnd(3, '0').take(3) else "0"
            val fraction = fractionStr.toLongOrNull() ?: 0L
            startTimeMs = min * 60_000L + sec * 1_000L + fraction
            durationMs = g4.takeIf { it.isNotEmpty() }?.toLongOrNull() ?: 0L
        } else {
            // [startMs,durationMs] format
            startTimeMs = g1.toLongOrNull() ?: return null
            durationMs = g4.takeIf { it.isNotEmpty() }?.toLongOrNull() ?: 0L
        }

        val syllables = parseSyllables(rest, startTimeMs)
        val fullContent = if (syllables.isNotEmpty()) {
            syllables.joinToString("") { it.text }
        } else {
            rest.replace(SYLLABLE_REGEX, "").trim()
        }

        if (fullContent.isBlank()) return null

        val isRightAligned = fullContent.startsWith("[Singer 2", ignoreCase = true) ||
                fullContent.startsWith("[Artist 2", ignoreCase = true) ||
                fullContent.startsWith("(Singer 2", ignoreCase = true)

        return LyricLine(
            startTimeMs = startTimeMs,
            content = fullContent,
            isInterlude = false,
            isWordByWord = syllables.isNotEmpty(),
            words = syllables,
            isRightAligned = isRightAligned
        )
    }

    /**
     * Parses syllable timing tokens `(start, dur)` or `<start, dur>`.
     * Handles both trailing (`word(start,dur)`) and leading (`(start,dur)word`) formats,
     * calculating precise `charRange` indexes for real-time word animation.
     */
    private fun parseSyllables(rest: String, lineStartTimeMs: Long): List<LyricWord> {
        val matches = SYLLABLE_REGEX.findAll(rest).toList()
        if (matches.isEmpty()) return emptyList()

        val syllables = mutableListOf<LyricWord>()
        val firstMatch = matches.first()
        val isLeading = rest.substring(0, firstMatch.range.first).isBlank()

        val firstRawTime = firstMatch.groupValues[1].toLongOrNull() ?: 0L
        val isRelative = (lineStartTimeMs > 3000L && firstRawTime < (lineStartTimeMs - 2000L))

        var currentPosition = 0

        if (isLeading) {
            // Leading format: (time, dur)text(time, dur)text...
            for (i in matches.indices) {
                val currentMatch = matches[i]
                val textStart = currentMatch.range.last + 1
                val textEnd = if (i + 1 < matches.size) matches[i + 1].range.first else rest.length
                val text = if (textStart <= textEnd) rest.substring(textStart, textEnd) else ""

                val rawTime = currentMatch.groupValues[1].toLongOrNull() ?: 0L
                val dur = currentMatch.groupValues[2].toLongOrNull() ?: 0L
                val absTime = if (isRelative) lineStartTimeMs + rawTime else rawTime

                if (text.isNotEmpty() || dur > 0) {
                    val startChar = currentPosition
                    val endChar = (currentPosition + text.length - 1).coerceAtLeast(startChar)
                    currentPosition += text.length

                    syllables.add(
                        LyricWord(
                            text = text,
                            absoluteStartTimeMs = absTime,
                            durationMs = dur,
                            charRange = startChar..endChar
                        )
                    )
                }
            }
        } else {
            // Trailing format: text(time, dur)text(time, dur)...
            var lastIndex = 0
            for (match in matches) {
                val text = rest.substring(lastIndex, match.range.first)
                lastIndex = match.range.last + 1

                val rawTime = match.groupValues[1].toLongOrNull() ?: 0L
                val dur = match.groupValues[2].toLongOrNull() ?: 0L
                val absTime = if (isRelative) lineStartTimeMs + rawTime else rawTime

                if (text.isNotEmpty() || dur > 0) {
                    val startChar = currentPosition
                    val endChar = (currentPosition + text.length - 1).coerceAtLeast(startChar)
                    currentPosition += text.length

                    syllables.add(
                        LyricWord(
                            text = text,
                            absoluteStartTimeMs = absTime,
                            durationMs = dur,
                            charRange = startChar..endChar
                        )
                    )
                }
            }
            if (lastIndex < rest.length) {
                val trailing = rest.substring(lastIndex)
                if (trailing.isNotEmpty()) {
                    if (syllables.isNotEmpty()) {
                        val last = syllables.removeAt(syllables.lastIndex)
                        val newText = last.text + trailing
                        val startChar = last.charRange.first
                        val endChar = startChar + newText.length - 1
                        syllables.add(
                            last.copy(
                                text = newText,
                                charRange = startChar..endChar
                            )
                        )
                    }
                }
            }
        }

        return syllables
    }

    private fun isMetadataOrCreditLine(line: String, songTitle: String?, artistName: String?): Boolean {
        val clean = line.lowercase().replace(Regex("[^a-z0-9\\u4e00-\\u9fa5]"), "")
        if (clean.isEmpty()) return true

        // Check for artist or title match
        val cleanTitle = songTitle?.lowercase()?.replace(Regex("[^a-z0-9\\u4e00-\\u9fa5]"), "")
        val cleanArtist = artistName?.lowercase()?.replace(Regex("[^a-z0-9\\u4e00-\\u9fa5]"), "")

        if (!cleanTitle.isNullOrEmpty() && clean.contains(cleanTitle)) {
            if (!cleanArtist.isNullOrEmpty() && clean.contains(cleanArtist)) return true
            if (clean.length < cleanTitle.length + 12) return true
        }

        if (!cleanArtist.isNullOrEmpty() && clean == cleanArtist) return true

        // Check for role/credit words
        for (keyword in CREDIT_KEYWORDS) {
            if (clean.startsWith(keyword) || clean.contains(":$keyword") || clean.contains("：$keyword")) {
                return true
            }
        }

        return false
    }

    private fun insertInterludes(sorted: List<LyricLine>): List<LyricLine> {
        if (sorted.size < 2) return sorted
        val result = mutableListOf<LyricLine>()

        for (i in sorted.indices) {
            val current = sorted[i]
            result.add(current)

            if (i + 1 < sorted.size) {
                val gap = sorted[i + 1].startTimeMs - current.startTimeMs
                if (gap > INTERLUDE_THRESHOLD_MS && sorted[i + 1].content.isNotEmpty()) {
                    val padding = (gap / 3).coerceAtMost(3500)
                    val interludeTime = current.startTimeMs + padding
                    result.add(LyricLine(interludeTime, "♪", isInterlude = true))
                }
            }
        }
        return result
    }
}

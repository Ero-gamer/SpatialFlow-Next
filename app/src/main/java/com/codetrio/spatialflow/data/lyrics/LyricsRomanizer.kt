package com.codetrio.spatialflow.data.lyrics

import android.content.Context
import android.icu.text.Transliterator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.Character.UnicodeScript

/**
 * User preferences for lyric romanization.
 */
data class LyricsRomanizationPreferences(
    val masterEnabled: Boolean = true,
    val romanizeJapanese: Boolean = true,
    val romanizeKorean: Boolean = true,
    val romanizeChinese: Boolean = true,
    val romanizeHindi: Boolean = true,
    val romanizeOther: Boolean = true
) {
    val isEnabled: Boolean
        get() = masterEnabled && (romanizeJapanese || romanizeKorean || romanizeChinese || romanizeHindi || romanizeOther)

    companion object {
        fun fromContext(context: Context): LyricsRomanizationPreferences {
            val prefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            return LyricsRomanizationPreferences(
                masterEnabled = prefs.getBoolean("lyrics_romanization_enabled", true),
                romanizeJapanese = prefs.getBoolean("lyrics_romanize_japanese", true),
                romanizeKorean = prefs.getBoolean("lyrics_romanize_korean", true),
                romanizeChinese = prefs.getBoolean("lyrics_romanize_chinese", true),
                romanizeHindi = prefs.getBoolean("lyrics_romanize_hindi", true),
                romanizeOther = prefs.getBoolean("lyrics_romanize_other", true)
            )
        }
    }
}

/**
 * Production-ready lyric romanization engine.
 * Features:
 * - Japanese: Kuromoji morphological tokenizer reading resolution (Kanji -> Katakana -> Romaji)
 *   with cross-token sokuon consonant doubling and character-offset word-by-word alignment.
 * - Korean: Hangul jamo decomposition with full batch consonant assimilation rules.
 * - Chinese: Hanzi to Pinyin via Android ICU Transliterator.
 * - Hindi: Devanagari to Latin with implicit vowel & matra handling.
 * - Other languages: ICU Any-Latin Transliterator (Cyrillic, Greek, Arabic, Thai, Hebrew, etc.).
 * - Whole-song language context: prevents pure-Kanji lines in Japanese songs from being misclassified as Chinese Pinyin.
 */
object LyricsRomanizer {

    private val WHITESPACE_REGEX = "\\s+".toRegex()
    private const val GENERIC_ROMANIZATION_TRANSFORM = "Any-Latin; Latin-ASCII"

    private val OTHER_ROMANIZATION_EXCLUDED_SCRIPTS = setOf(
        UnicodeScript.LATIN,
        UnicodeScript.COMMON,
        UnicodeScript.INHERITED,
        UnicodeScript.HAN,
        UnicodeScript.HIRAGANA,
        UnicodeScript.KATAKANA,
        UnicodeScript.HANGUL,
        UnicodeScript.DEVANAGARI
    )

    private val genericTransliterator = ThreadLocal.withInitial {
        try {
            Transliterator.getInstance(GENERIC_ROMANIZATION_TRANSFORM)
        } catch (_: Throwable) {
            null
        }
    }

    private val KANA_ROMAJI_MAP: Map<String, String> = mapOf(
        // Digraphs / Yoon
        "キャ" to "kya", "キュ" to "kyu", "キョ" to "kyo",
        "シャ" to "sha", "シュ" to "shu", "ショ" to "sho",
        "チャ" to "cha", "チュ" to "chu", "チョ" to "cho",
        "ニャ" to "nya", "ニュ" to "nyu", "ニョ" to "nyo",
        "ヒャ" to "hya", "ヒュ" to "hyu", "ヒョ" to "hyo",
        "ミャ" to "mya", "ミュ" to "myu", "ミョ" to "myo",
        "リャ" to "rya", "リュ" to "ryu", "リョ" to "ryo",
        "ギャ" to "gya", "ギュ" to "gyu", "ギョ" to "gyo",
        "ジャ" to "ja", "ジュ" to "ju", "ジョ" to "jo",
        "ヂャ" to "ja", "ヂュ" to "ju", "ヂョ" to "jo",
        "ビャ" to "bya", "ビュ" to "byu", "ビョ" to "byo",
        "ピャ" to "pya", "ピュ" to "pyu", "ピョ" to "pyo",

        // Extended loanword digraphs
        "ティ" to "ti", "ディ" to "di",
        "ウィ" to "wi", "ウェ" to "we", "ウォ" to "wo",
        "チェ" to "che", "シェ" to "she", "ジェ" to "je",
        "ファ" to "fa", "フィ" to "fi", "フェ" to "fe", "フォ" to "fo",
        "ツァ" to "tsa", "ツィ" to "tsi", "ツェ" to "tse", "ツォ" to "tso",
        "トゥ" to "tu", "ドゥ" to "du",
        "ヴァ" to "va", "ヴィ" to "vi", "ヴ" to "vu", "ヴェ" to "ve", "ヴォ" to "vo",

        // Gojuon
        "ア" to "a", "イ" to "i", "ウ" to "u", "エ" to "e", "オ" to "o",
        "カ" to "ka", "キ" to "ki", "ク" to "ku", "ケ" to "ke", "コ" to "ko",
        "サ" to "sa", "シ" to "shi", "ス" to "su", "セ" to "se", "ソ" to "so",
        "タ" to "ta", "チ" to "chi", "ツ" to "tsu", "テ" to "te", "ト" to "to",
        "ナ" to "na", "ニ" to "ni", "ヌ" to "nu", "ネ" to "ne", "ノ" to "no",
        "ハ" to "ha", "ヒ" to "hi", "フ" to "fu", "ヘ" to "he", "ホ" to "ho",
        "マ" to "ma", "ミ" to "mi", "ム" to "mu", "メ" to "me", "モ" to "mo",
        "ヤ" to "ya", "ユ" to "yu", "ヨ" to "yo",
        "ラ" to "ra", "リ" to "ri", "ル" to "ru", "レ" to "re", "ロ" to "ro",
        "ワ" to "wa", "ヲ" to "o", "ン" to "n",

        // Dakuon
        "ガ" to "ga", "ギ" to "gi", "グ" to "gu", "ゲ" to "ge", "ゴ" to "go",
        "ザ" to "za", "ジ" to "ji", "ズ" to "zu", "ゼ" to "ze", "ゾ" to "zo",
        "ダ" to "da", "ヂ" to "ji", "ヅ" to "zu", "デ" to "de", "ド" to "do",
        "バ" to "ba", "ビ" to "bi", "ブ" to "bu", "ベ" to "be", "ボ" to "bo",
        "パ" to "pa", "ピ" to "pi", "プ" to "pu", "ペ" to "pe", "ポ" to "po",

        // Small vowels fallback
        "ァ" to "a", "ィ" to "i", "ゥ" to "u", "ェ" to "e", "ォ" to "o",
        "ヵ" to "ka", "ヶ" to "ke", "ヮ" to "wa",

        // Chouonpu
        "ー" to ""
    )

    private val HANGUL_ROMAJA_MAP: Map<String, Map<String, String>> = mapOf(
        "cho" to mapOf(
            "ᄀ" to "g", "ᄁ" to "kk", "ᄂ" to "n", "ᄃ" to "d", "ᄄ" to "tt",
            "ᄅ" to "r", "ᄆ" to "m", "ᄇ" to "b", "ᄈ" to "pp", "ᄉ" to "s",
            "ᄊ" to "ss", "ᄋ" to "", "ᄌ" to "j", "ᄍ" to "jj", "ᄎ" to "ch",
            "ᄏ" to "k", "ᄐ" to "t", "ᄑ" to "p", "ᄒ" to "h"
        ),
        "jung" to mapOf(
            "ᅡ" to "a", "ᅢ" to "ae", "ᅣ" to "ya", "ᅤ" to "yae", "ᅥ" to "eo",
            "ᅦ" to "e", "ᅧ" to "yeo", "ᅨ" to "ye", "ᅩ" to "o", "ᅪ" to "wa",
            "ᅫ" to "wae", "ᅬ" to "oe", "ᅭ" to "yo", "ᅮ" to "u", "ᅯ" to "wo",
            "ᅰ" to "we", "ᅱ" to "wi", "ᅲ" to "yu", "ᅳ" to "eu", "ᅴ" to "eui",
            "ᅵ" to "i"
        ),
        "jong" to mapOf(
            "ᆨ" to "k", "ᆨᄋ" to "g", "ᆨᄂ" to "ngn", "ᆨᄅ" to "ngn", "ᆨᄆ" to "ngm", "ᆨᄒ" to "kh",
            "ᆩ" to "kk", "ᆩᄋ" to "kg", "ᆩᄂ" to "ngn", "ᆩᄅ" to "ngn", "ᆩᄆ" to "ngm", "ᆩᄒ" to "kh",
            "ᆪ" to "k", "ᆪᄋ" to "ks", "ᆪᄂ" to "ngn", "ᆪᄅ" to "ngn", "ᆪᄆ" to "ngm", "ᆪᄒ" to "kch",
            "ᆫ" to "n", "ᆫᄅ" to "ll",
            "ᆬ" to "n", "ᆬᄋ" to "nj", "ᆬᄂ" to "nn", "ᆬᄅ" to "nn", "ᆬᄆ" to "nm", "ᆬᄒ" to "nch", "ᆬㅎ" to "nch",
            "ᆭ" to "n", "ᆭᄋ" to "nh", "ᆭᄅ" to "nn",
            "ᆮ" to "t", "ᆮᄋ" to "d", "ᆮᄂ" to "nn", "ᆮᄅ" to "nn", "ᆮᄆ" to "nm", "ᆮᄒ" to "th",
            "ᆯ" to "l", "ᆯᄋ" to "r", "ᆯᄂ" to "ll", "ᆯᄅ" to "ll",
            "ᆰ" to "k", "ᆰᄋ" to "lg", "ᆰᄂ" to "ngn", "ᆰᄅ" to "ngn", "ᆰᄆ" to "ngm", "ᆰᄒ" to "lkh",
            "ᆱ" to "m", "ᆱᄋ" to "lm", "ᆱᄂ" to "mn", "ᆱᄅ" to "mn", "ᆱᄆ" to "mm", "ᆱᄒ" to "lmh",
            "ᆲ" to "p", "ᆲᄋ" to "lb", "ᆲᄂ" to "mn", "ᆲᄅ" to "mn", "ᆲᄆ" to "mm", "ᆲᄒ" to "lph",
            "ᆳ" to "t", "ᆳᄋ" to "ls", "ᆳᄂ" to "nn", "ᆳᄅ" to "nn", "ᆳᄆ" to "nm", "ᆳᄒ" to "lsh",
            "ᆴ" to "t", "ᆴᄋ" to "lt", "ᆴᄂ" to "nn", "ᆴᄅ" to "nn", "ᆴᄆ" to "nm", "ᆴᄒ" to "lth",
            "ᆵ" to "p", "ᆵᄋ" to "lp", "ᆵᄂ" to "mn", "ᆵᄅ" to "mn", "ᆵᄆ" to "mm", "ᆵᄒ" to "lph",
            "ᆶ" to "l", "ᆶᄋ" to "lh", "ᆶᄂ" to "ll", "ᆶᄅ" to "ll", "ᆶᄆ" to "lm", "ᆶᄒ" to "lh",
            "ᆷ" to "m", "ᆷᄅ" to "mn",
            "ᆸ" to "p", "ᆸᄋ" to "b", "ᆸᄂ" to "mn", "ᆸᄅ" to "mn", "ᆸᄆ" to "mm", "ᆸᄒ" to "ph",
            "ᆹ" to "p", "ᆹᄋ" to "ps", "ᆹᄂ" to "mn", "ᆹᄅ" to "mn", "ᆹᄆ" to "mm", "ᆹᄒ" to "psh",
            "ᆺ" to "t", "ᆺᄋ" to "s", "ᆺᄂ" to "nn", "ᆺᄅ" to "nn", "ᆺᄆ" to "nm", "ᆺᄒ" to "sh",
            "ᆻ" to "t", "ᆻᄋ" to "ss", "ᆻᄂ" to "tn", "ᆻᄅ" to "tn", "ᆻᄆ" to "nm", "ᆻᄒ" to "th",
            "ᆼ" to "ng",
            "ᆽ" to "t", "ᆽᄋ" to "j", "ᆽᄂ" to "nn", "ᆽᄅ" to "nn", "ᆽᄆ" to "nm", "ᆽᄒ" to "ch",
            "ᆾ" to "t", "ᆾᄋ" to "ch", "ᆾᄂ" to "nn", "ᆾᄅ" to "nn", "ᆾᄆ" to "nm", "ᆾᄒ" to "ch",
            "ᆿ" to "k", "ᆿᄋ" to "k", "ᆿᄂ" to "ngn", "ᆿᄅ" to "ngn", "ᆿᄆ" to "ngm", "ᆿᄒ" to "kh",
            "ᇀ" to "t", "ᇀᄋ" to "t", "ᇀᄂ" to "nn", "ᇀᄅ" to "nn", "ᇀᄆ" to "nm", "ᇀᄒ" to "th",
            "ᇁ" to "p", "ᇁᄋ" to "p", "ᇁᄂ" to "mn", "ᇁᄅ" to "mn", "ᇁᄆ" to "mm", "ᇁᄒ" to "ph",
            "ᇂ" to "t", "ᇂᄋ" to "h", "ᇂᄂ" to "nn", "ᇂᄅ" to "nn", "ᇂᄆ" to "mm", "ᇂᄒ" to "t", "ᇂᄀ" to "k"
        )
    )

    private val DEVANAGARI_INDEPENDENT_VOWELS = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ii", 'उ' to "u", 'ऊ' to "uu",
        'ऋ' to "ri", 'ॠ' to "rii", 'ऌ' to "lri", 'ॡ' to "lrii",
        'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au"
    )

    private val DEVANAGARI_MATRAS = mapOf(
        'ा' to "aa", 'ि' to "i", 'ी' to "ii", 'ु' to "u", 'ू' to "uu",
        'ृ' to "ri", 'ॄ' to "rii", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au",
        'ॅ' to "e", 'ॉ' to "o", 'ॢ' to "lri", 'ॣ' to "lrii"
    )

    private val DEVANAGARI_CONSONANTS = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "ng",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "ny",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v",
        'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h", 'ळ' to "l"
    )

    private val DEVANAGARI_OTHER = mapOf(
        'ॐ' to "om", '।' to ".", '॥' to "..", 'ऽ' to "'",
        'ं' to "n", 'ः' to "h", 'ँ' to "n", '्' to ""
    )

    private val DEVANAGARI_NUMERALS = mapOf(
        '०' to "0", '१' to "1", '२' to "2", '३' to "3", '४' to "4",
        '५' to "5", '६' to "6", '७' to "7", '८' to "8", '९' to "9"
    )

    private val LABIALS = setOf('प', 'फ', 'ब', 'भ', 'म')

    fun hiraganaToKatakana(text: String): String {
        if (text.isEmpty()) return text
        val sb = StringBuilder(text.length)
        for (ch in text) {
            sb.append(
                if (ch in '\u3041'..'\u3096') {
                    (ch.code + 0x60).toChar()
                } else {
                    ch
                }
            )
        }
        return sb.toString()
    }

    fun katakanaToRomaji(katakana: String?, nextKatakana: String? = null): String {
        if (katakana.isNullOrEmpty()) return ""

        val romajiBuilder = StringBuilder(katakana.length)
        var i = 0
        val n = katakana.length
        while (i < n) {
            var consumed = false

            // Try 2-char digraph
            if (i + 1 < n) {
                val twoCharCandidate = katakana.substring(i, i + 2)
                val mappedTwoChar = KANA_ROMAJI_MAP[twoCharCandidate]
                if (mappedTwoChar != null) {
                    romajiBuilder.append(mappedTwoChar)
                    i += 2
                    consumed = true
                }
            }

            // Sokuon ッ handling (consonant doubling)
            if (!consumed && katakana[i] == 'ッ') {
                val nextCharInSameString = katakana.getOrNull(i + 1)
                val nextCharToDouble = nextCharInSameString ?: nextKatakana?.getOrNull(0)
                if (nextCharToDouble != null) {
                    val nextCharRomaji = KANA_ROMAJI_MAP[nextCharToDouble.toString()]
                        ?: nextCharToDouble.toString()
                    val firstLetter = nextCharRomaji.firstOrNull()?.lowercase()?.trim()
                    if (firstLetter != null && firstLetter.isNotEmpty()) {
                        romajiBuilder.append(firstLetter)
                    }
                }
                i += 1
                consumed = true
            }

            // Chouonpu ー handling (vowel extension)
            if (!consumed && katakana[i] == 'ー') {
                val lastChar = romajiBuilder.lastOrNull()
                val extension = when (lastChar) {
                    'a' -> "a"
                    'i' -> "i"
                    'u' -> "u"
                    'e' -> "e"
                    'o' -> "o"
                    else -> ""
                }
                romajiBuilder.append(extension)
                i += 1
                consumed = true
            }

            // 1-char mapping
            if (!consumed) {
                val oneCharCandidate = katakana[i].toString()
                val mappedOneChar = KANA_ROMAJI_MAP[oneCharCandidate]
                if (mappedOneChar != null) {
                    romajiBuilder.append(mappedOneChar)
                } else {
                    romajiBuilder.append(oneCharCandidate)
                }
                i += 1
            }
        }
        return romajiBuilder.toString().lowercase()
    }

    /**
     * Romanize Japanese text using Kuromoji morphological analyzer for accurate Kanji readings.
     */
    fun romanizeJapanese(text: String): String {
        val tokenizer = JapaneseLanguagePackManager.tokenizerOrNull()
        if (tokenizer == null) {
            // Fallback for Kana-only text when dictionary is not loaded
            val katakana = hiraganaToKatakana(text)
            return katakanaToRomaji(katakana)
        }

        return try {
            val rawTokens = tokenizer.tokenize(text)
            val tokens = rawTokens.map { it as? com.atilika.kuromoji.ipadic.Token }
            val romanizedTokens = tokens.mapIndexed { index, token ->
                val reading = token?.reading
                val currentReading = if (reading.isNullOrEmpty() || reading == "*") {
                    token?.surface ?: ""
                } else {
                    reading
                }
                val katakanaReading = hiraganaToKatakana(currentReading)

                val nextTokenReading = if (index + 1 < tokens.size) {
                    val nextToken = tokens[index + 1]
                    val nr = nextToken?.reading
                    val nextReading = if (nr.isNullOrEmpty() || nr == "*") nextToken?.surface ?: "" else nr
                    hiraganaToKatakana(nextReading)
                } else {
                    null
                }
                katakanaToRomaji(katakanaReading, nextTokenReading)
            }
            romanizedTokens.joinToString(" ")
        } catch (_: Throwable) {
            val katakana = hiraganaToKatakana(text)
            katakanaToRomaji(katakana)
        }
    }

    /**
     * Romanize Japanese words for word-by-word karaoke lines using character offsets.
     */
    fun romanizeJapaneseWordsForLine(
        words: List<String>,
        lineText: String
    ): List<String?> {
        if (words.isEmpty()) return emptyList()
        val tokenizer = JapaneseLanguagePackManager.tokenizerOrNull()
            ?: return words.map { word -> romanizeJapanese(word).takeIf { it.isNotBlank() } }

        return try {
            val rawTokens = tokenizer.tokenize(lineText)
            if (rawTokens.isEmpty()) return words.map { null }
            val tokens = rawTokens.map { it as? com.atilika.kuromoji.ipadic.Token }

            val tokenCount = tokens.size
            val tokenStarts = IntArray(tokenCount)
            val tokenEnds = IntArray(tokenCount)
            val tokenRomaji = ArrayList<String>(tokenCount)
            for (i in 0 until tokenCount) {
                val token = tokens[i]
                val surface = token?.surface ?: ""
                val pos = token?.position ?: 0
                tokenStarts[i] = pos
                tokenEnds[i] = pos + surface.length

                val reading = token?.reading
                val currentReading = if (reading.isNullOrEmpty() || reading == "*") {
                    surface
                } else {
                    reading
                }
                val katakanaReading = hiraganaToKatakana(currentReading)
                val nextTokenReading = if (i + 1 < tokenCount) {
                    val nextToken = tokens[i + 1]
                    val nr = nextToken?.reading
                    val nextReading = if (nr.isNullOrEmpty() || nr == "*") nextToken?.surface ?: "" else nr
                    hiraganaToKatakana(nextReading)
                } else {
                    null
                }
                tokenRomaji.add(katakanaToRomaji(katakanaReading, nextTokenReading))
            }

            val result = ArrayList<String?>(words.size)
            var scanOffset = 0
            var tokenIdx = 0
            for (word in words) {
                val wordStart = lineText.indexOf(word, startIndex = scanOffset)
                if (wordStart < 0) {
                    result.add(null)
                    continue
                }
                val wordEnd = wordStart + word.length

                while (tokenIdx < tokenCount && tokenEnds[tokenIdx] <= wordStart) {
                    tokenIdx++
                }

                val romajiBuilder = StringBuilder()
                var tIdx = tokenIdx
                while (tIdx < tokenCount && tokenStarts[tIdx] < wordEnd) {
                    if (tokenEnds[tIdx] > wordStart) {
                        romajiBuilder.append(tokenRomaji[tIdx])
                    }
                    tIdx++
                }

                result.add(romajiBuilder.toString().takeIf { it.isNotEmpty() })
                scanOffset = wordEnd
            }
            result
        } catch (_: Throwable) {
            words.map { word -> romanizeJapanese(word).takeIf { it.isNotBlank() } }
        }
    }

    fun romanizeKorean(text: String): String {
        val romajaBuilder = StringBuilder()
        var prevFinal: String? = null

        for (i in text.indices) {
            val char = text[i]

            if (char in '\uAC00'..'\uD7A3') {
                val syllableIndex = char.code - 0xAC00
                val choIndex = syllableIndex / (21 * 28)
                val jungIndex = (syllableIndex % (21 * 28)) / 28
                val jongIndex = syllableIndex % 28

                val choChar = (0x1100 + choIndex).toChar().toString()
                val jungChar = (0x1161 + jungIndex).toChar().toString()
                val jongChar = if (jongIndex == 0) null else (0x11A7 + jongIndex).toChar().toString()

                if (prevFinal != null) {
                    val contextKey = prevFinal + choChar
                    val jong = HANGUL_ROMAJA_MAP["jong"]?.get(contextKey)
                        ?: HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal)
                        ?: prevFinal
                    romajaBuilder.append(jong)
                }

                val cho = HANGUL_ROMAJA_MAP["cho"]?.get(choChar) ?: choChar
                val jung = HANGUL_ROMAJA_MAP["jung"]?.get(jungChar) ?: jungChar
                romajaBuilder.append(cho).append(jung)

                prevFinal = jongChar
            } else {
                if (prevFinal != null) {
                    val jong = HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal) ?: prevFinal
                    romajaBuilder.append(jong)
                    prevFinal = null
                }
                romajaBuilder.append(char)
            }
        }

        if (prevFinal != null) {
            val jong = HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal) ?: prevFinal
            romajaBuilder.append(jong)
        }

        return romajaBuilder.toString()
    }

    fun romanizeHindi(text: String): String {
        val sb = StringBuilder(text.length * 2)
        var i = 0
        val n = text.length
        while (i < n) {
            val ch = text[i]

            if (DEVANAGARI_NUMERALS[ch] != null) {
                sb.append(DEVANAGARI_NUMERALS[ch])
                i++
                continue
            }

            if (ch == 'ं') {
                val next = text.getOrNull(i + 1)
                sb.append(if (next != null && next in LABIALS) "m" else "n")
                i++
                continue
            }

            if (ch == 'ँ') {
                sb.append("n")
                i++
                continue
            }

            if (ch == 'ः') {
                sb.append("h")
                i++
                continue
            }

            if (ch == '्') {
                i++
                continue
            }

            val matra = DEVANAGARI_MATRAS[ch]
            if (matra != null) {
                sb.append(matra)
                i++
                continue
            }

            val independentVowel = DEVANAGARI_INDEPENDENT_VOWELS[ch]
            if (independentVowel != null) {
                sb.append(independentVowel)
                i++
                continue
            }

            val consonant = DEVANAGARI_CONSONANTS[ch]
            if (consonant != null) {
                val next = text.getOrNull(i + 1)
                val suppressInherentA = next != null && (
                    next in DEVANAGARI_MATRAS ||
                    next == '्' ||
                    next == 'ं' ||
                    next == 'ः' ||
                    next == 'ँ'
                )
                sb.append(consonant)
                if (!suppressInherentA) {
                    sb.append('a')
                }
                i++
                continue
            }

            val other = DEVANAGARI_OTHER[ch]
            if (other != null) {
                sb.append(other)
                i++
                continue
            }

            sb.append(ch)
            i++
        }
        return sb.toString()
    }

    fun romanizeWithIcu(text: String): String {
        return try {
            val transliterator = genericTransliterator.get()
            transliterator?.transliterate(text) ?: text
        } catch (_: Throwable) {
            text
        }
    }

    /**
     * Determines whether text or a line looks Japanese:
     * - Returns true if Hiragana, Katakana, or Japanese repetition markers are found.
     * - If Kanji is present: returns true if whole-song contains Kana OR if Kuromoji tokenizer is available.
     */
    fun looksJapanese(text: String, songHasKana: Boolean = false): Boolean {
        if (text.any {
                hasScript(it, UnicodeScript.HIRAGANA) ||
                hasScript(it, UnicodeScript.KATAKANA) ||
                it == '々' || it == '〆' || it == 'ヶ'
            }
        ) {
            return true
        }

        val hasKanji = text.any { it in '\u4E00'..'\u9FFF' }
        if (hasKanji) {
            if (songHasKana) return true
            return JapaneseLanguagePackManager.tokenizerOrNull() != null
        }
        return false
    }

    fun isKorean(text: String): Boolean {
        return text.any { it in '\uAC00'..'\uD7A3' } || text.any { hasScript(it, UnicodeScript.HANGUL) }
    }

    fun isChinese(text: String, songHasKana: Boolean = false): Boolean {
        if (songHasKana || text.isEmpty()) return false
        val hanCharCount = text.count { hasScript(it, UnicodeScript.HAN) }
        if (hanCharCount == 0) return false
        val japaneseKanaCount = text.count { hasScript(it, UnicodeScript.HIRAGANA) || hasScript(it, UnicodeScript.KATAKANA) }
        val hangulCount = text.count { hasScript(it, UnicodeScript.HANGUL) }
        return japaneseKanaCount == 0 && hangulCount == 0
    }

    fun isHindi(text: String): Boolean {
        return text.any { hasScript(it, UnicodeScript.DEVANAGARI) }
    }

    fun hasOtherRomanizableScript(text: String): Boolean {
        return text.any { char ->
            if (!char.isLetter()) return@any false
            val script = UnicodeScript.of(char.code)
            script !in OTHER_ROMANIZATION_EXCLUDED_SCRIPTS
        }
    }

    fun hasSongJapaneseKana(lyricsText: String): Boolean {
        return lyricsText.any {
            hasScript(it, UnicodeScript.HIRAGANA) || hasScript(it, UnicodeScript.KATAKANA)
        }
    }

    /**
     * Returns true if the given lyric lines contain any non-Latin scripts
     * that SpatialFlow can romanize (Japanese, Korean, Chinese, Hindi, Cyrillic, Greek, Arabic, etc.).
     */
    fun hasRomanizableScript(lines: List<LyricLine>?): Boolean {
        if (lines.isNullOrEmpty()) return false
        val allText = StringBuilder()
        for (line in lines) {
            if (!line.isInterlude && line.content.isNotBlank()) {
                allText.append(line.content).append('\n')
            }
        }
        val text = allText.toString()
        if (text.isBlank()) return false
        val songHasKana = hasSongJapaneseKana(text)
        return looksJapanese(text, songHasKana) ||
                isKorean(text) ||
                isHindi(text) ||
                isChinese(text, songHasKana) ||
                hasOtherRomanizableScript(text)
    }

    fun shouldRomanizeLine(
        text: String,
        preferences: LyricsRomanizationPreferences,
        songHasKana: Boolean = false
    ): Boolean {
        if (!preferences.isEnabled || text.isBlank()) return false
        return when {
            preferences.romanizeJapanese && looksJapanese(text, songHasKana) -> true
            preferences.romanizeKorean && isKorean(text) -> true
            preferences.romanizeHindi && isHindi(text) -> true
            preferences.romanizeChinese && isChinese(text, songHasKana) -> true
            preferences.romanizeOther && hasOtherRomanizableScript(text) -> true
            else -> false
        }
    }

    fun romanizeText(
        text: String,
        preferences: LyricsRomanizationPreferences,
        songHasKana: Boolean = false
    ): String? {
        if (!shouldRomanizeLine(text, preferences, songHasKana)) return null
        val rawResult = when {
            preferences.romanizeJapanese && looksJapanese(text, songHasKana) -> romanizeJapanese(text)
            preferences.romanizeKorean && isKorean(text) -> romanizeKorean(text)
            preferences.romanizeHindi && isHindi(text) -> romanizeHindi(text)
            preferences.romanizeChinese && isChinese(text, songHasKana) -> romanizeWithIcu(text)
            preferences.romanizeOther && hasOtherRomanizableScript(text) -> romanizeWithIcu(text)
            else -> null
        }
        val normalized = rawResult?.replace(WHITESPACE_REGEX, " ")?.trim()?.takeIf { it.isNotEmpty() }
        return normalized?.takeUnless { it.equals(text.trim(), ignoreCase = true) }
    }

    fun romanizeWord(
        word: String,
        lineContext: String,
        preferences: LyricsRomanizationPreferences,
        songHasKana: Boolean = false
    ): String? {
        if (word.isBlank()) return null
        val rawResult = when {
            preferences.romanizeJapanese && looksJapanese(lineContext, songHasKana) -> romanizeJapanese(word)
            preferences.romanizeKorean && isKorean(lineContext) -> romanizeKorean(word)
            preferences.romanizeHindi && isHindi(lineContext) -> romanizeHindi(word)
            preferences.romanizeChinese && isChinese(lineContext, songHasKana) -> romanizeWithIcu(word)
            preferences.romanizeOther && hasOtherRomanizableScript(lineContext) -> romanizeWithIcu(word)
            else -> null
        }
        val normalized = rawResult?.replace(WHITESPACE_REGEX, " ")?.trim()?.takeIf { it.isNotEmpty() }
        return normalized?.takeUnless { it.equals(word.trim(), ignoreCase = true) }
    }

    fun romanizeLine(
        line: LyricLine,
        preferences: LyricsRomanizationPreferences,
        songHasKana: Boolean = false
    ): LyricLine {
        if (line.isInterlude || line.content.isBlank() || !shouldRomanizeLine(line.content, preferences, songHasKana)) {
            return if (line.romanizedContent != null || line.romanizedWords.isNotEmpty()) {
                line.copy(romanizedContent = null, romanizedWords = emptyList())
            } else {
                line
            }
        }

        val romContent = romanizeText(line.content, preferences, songHasKana)
            ?: return line.copy(romanizedContent = null, romanizedWords = emptyList())

        val romWords = if (line.isWordByWord && line.words.isNotEmpty()) {
            if (preferences.romanizeJapanese && looksJapanese(line.content, songHasKana)) {
                romanizeJapaneseWordsForLine(line.words.map { it.text }, line.content)
            } else {
                line.words.map { word ->
                    romanizeWord(word.text, line.content, preferences, songHasKana)
                }
            }
        } else {
            emptyList()
        }

        return line.copy(
            romanizedContent = romContent,
            romanizedWords = romWords
        )
    }

    fun romanizeLines(
        lines: List<LyricLine>,
        preferences: LyricsRomanizationPreferences
    ): List<LyricLine> {
        if (lines.isEmpty()) return lines
        if (!preferences.isEnabled) {
            return lines.map {
                if (it.romanizedContent != null || it.romanizedWords.isNotEmpty()) {
                    it.copy(romanizedContent = null, romanizedWords = emptyList())
                } else {
                    it
                }
            }
        }
        val allLyricsText = lines.joinToString("\n") { it.content }
        val songHasKana = hasSongJapaneseKana(allLyricsText)
        return lines.map { line -> romanizeLine(line, preferences, songHasKana) }
    }

    fun romanizePlainLyrics(
        plainLyrics: String,
        preferences: LyricsRomanizationPreferences
    ): String {
        if (!preferences.isEnabled || plainLyrics.isBlank()) return plainLyrics
        val songHasKana = hasSongJapaneseKana(plainLyrics)
        val lines = plainLyrics.lines()
        val result = mutableListOf<String>()
        for (line in lines) {
            result.add(line)
            if (shouldRomanizeLine(line, preferences, songHasKana)) {
                val rom = romanizeText(line, preferences, songHasKana)
                if (!rom.isNullOrBlank()) {
                    result.add("  ($rom)")
                }
            }
        }
        return result.joinToString("\n")
    }

    private fun hasScript(char: Char, script: UnicodeScript): Boolean {
        return char.isLetter() && UnicodeScript.of(char.code) == script
    }
}

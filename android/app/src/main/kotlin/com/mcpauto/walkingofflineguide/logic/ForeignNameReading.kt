package com.mcpauto.walkingofflineguide.logic

import java.text.Normalizer

/**
 * 현지어 고유명사 → 한글 **철자·발음 읽기** (의미 번역·해석 아님).
 * 예: Colosseo → 콜로세오 (≠ 콜로세움)
 */
object ForeignNameReading {

    fun containsHangul(text: String): Boolean =
        text.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.HANGUL_SYLLABLES ||
            Character.UnicodeBlock.of(it) == Character.UnicodeBlock.HANGUL_JAMO }

    fun isLatinDominant(text: String): Boolean {
        var latin = 0
        var other = 0
        for (ch in text) {
            if (ch.isWhitespace() || !ch.isLetter()) continue
            if (ch.code <= 0x024F) latin++ else other++
        }
        return latin > 0 && latin >= other
    }

    fun toKoreanReading(localName: String, sourceLang: String): String {
        val trimmed = localName.trim()
        if (trimmed.isBlank()) return trimmed
        if (containsHangul(trimmed)) return trimmed
        if (!isLatinDominant(trimmed)) return trimmed

        val lang = sourceLang.lowercase().substringBefore('-')
        return buildString {
            val part = StringBuilder()
            fun flushPart() {
                if (part.isEmpty()) return
                append(wordToHangul(normalizeAccents(part.toString().lowercase()), lang))
                part.clear()
            }
            for (ch in trimmed) {
                if (ch.isLetter() || ch == '\'') {
                    part.append(ch)
                } else {
                    flushPart()
                    append(ch)
                }
            }
            flushPart()
        }.ifBlank { trimmed }
    }

    private fun normalizeAccents(text: String): String {
        val nfd = Normalizer.normalize(text, Normalizer.Form.NFD)
        return buildString {
            for (ch in nfd) {
                if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt()) append(ch)
            }
        }
    }

    private fun wordToHangul(word: String, lang: String): String {
        val phonemes = tokenize(word, lang)
        return phonemesToHangul(phonemes)
    }

    private fun tokenize(word: String, lang: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < word.length) {
            val ch = word[i]
            if (!ch.isLetter()) {
                i++
                continue
            }
            val triple = word.substring(i, (i + 3).coerceAtMost(word.length))
            val pair = word.substring(i, (i + 2).coerceAtMost(word.length))
            when (lang) {
                "it" -> when {
                    triple.startsWith("gli") -> { out += "l"; out += "i"; i += 3 }
                    pair == "gn" -> { out += "n"; out += "i"; i += 2 }
                    triple.startsWith("sch") -> { out += "s"; out += "k"; i += 3 }
                    pair == "ch" && hasNextVowel(word, i + 2) -> { out += "k"; i += 2 }
                    pair == "sc" && hasNextVowel(word, i + 2) -> { out += "sh"; i += 2 }
                    pair == "gl" && hasNextVowel(word, i + 2) -> { out += "l"; i += 2 }
                    ch == 'c' && hasNextVowel(word, i + 1) -> { out += "k"; i++ }
                    ch == 'g' && hasNextVowel(word, i + 1) -> { out += "g"; i++ }
                    ch == 'h' -> i++
                    else -> { out += mapLetter(ch, lang); i++ }
                }
                "fr" -> when {
                    pair == "ch" -> { out += "sh"; i += 2 }
                    pair == "ph" -> { out += "f"; i += 2 }
                    pair == "ou" -> { out += "u"; i += 2 }
                    pair == "oi" -> { out += "w"; out += "a"; i += 2 }
                    ch == 'h' -> i++
                    else -> { out += mapLetter(ch, lang); i++ }
                }
                "de" -> when {
                    pair == "sch" -> { out += "sh"; i += 3 }
                    pair == "ch" -> { out += "k"; i += 2 }
                    pair == "ph" -> { out += "f"; i += 2 }
                    ch == 'w' -> { out += "v"; i++ }
                    ch == 'v' -> { out += "f"; i++ }
                    ch == 'z' -> { out += "ts"; i++ }
                    else -> { out += mapLetter(ch, lang); i++ }
                }
                else -> when {
                    pair == "ch" -> { out += "ch"; i += 2 }
                    pair == "sh" -> { out += "sh"; i += 2 }
                    pair == "ph" -> { out += "f"; i += 2 }
                    pair == "th" -> { out += "th"; i += 2 }
                    else -> { out += mapLetter(ch, lang); i++ }
                }
            }
        }
        return out
    }

    private const val VOWELS = "aeiou"

    private fun hasNextVowel(word: String, idx: Int): Boolean =
        idx < word.length && word[idx] in VOWELS

    private fun mapLetter(ch: Char, lang: String): String = when (ch) {
        'a' -> "a"
        'e' -> "e"
        'i' -> "i"
        'o' -> "o"
        'u' -> "u"
        'y' -> "i"
        'b' -> "b"
        'c' -> if (lang == "it") "k" else "k"
        'd' -> "d"
        'f' -> "f"
        'g' -> "g"
        'h' -> ""
        'j' -> "j"
        'k' -> "k"
        'l' -> "l"
        'm' -> "m"
        'n' -> "n"
        'p' -> "p"
        'q' -> "k"
        'r' -> "r"
        's' -> "s"
        't' -> "t"
        'v' -> "b"
        'w' -> "w"
        'x' -> "ks"
        'z' -> if (lang == "it") "ts" else "z"
        else -> ch.toString()
    }

    private fun phonemesToHangul(phonemes: List<String>): String {
        val sb = StringBuilder()
        var onset: String? = null
        fun flushOnsetOnly() {
            val o = onset
            if (o != null) {
                sb.append(syllable(o, "eu"))
                onset = null
            }
        }
        for (p in phonemes) {
            if (p.isEmpty()) continue
            if (p in VOWELS) {
                sb.append(syllable(onset ?: "", p))
                onset = null
            } else if (p.length == 1 && p[0] in VOWELS) {
                sb.append(syllable(onset ?: "", p))
                onset = null
            } else {
                for (ch in p) {
                    val s = ch.toString()
                    if (s in VOWELS) {
                        sb.append(syllable(onset ?: "", s))
                        onset = null
                    } else {
                        flushOnsetOnly()
                        val merged = onset.orEmpty() + s
                        if (merged.length > 2) {
                            sb.append(syllable(merged.dropLast(1), "eu"))
                            onset = s
                        } else {
                            onset = merged
                        }
                    }
                }
            }
        }
        flushOnsetOnly()
        return sb.toString()
    }

    private fun syllable(onset: String, vowel: String): String {
        val ini = ONSET_INDEX[onset] ?: ONSET_INDEX[onset.takeLast(1)] ?: 11
        val med = VOWEL_INDEX[vowel] ?: 0
        val code = 0xAC00 + (ini * 21 + med) * 28
        return code.toChar().toString()
    }

    private val VOWEL_INDEX = mapOf(
        "a" to 0, "e" to 5, "i" to 20, "o" to 8, "u" to 13, "eu" to 18,
    )

    private val ONSET_INDEX = mapOf(
        "" to 11, "b" to 7, "ch" to 14, "d" to 3, "f" to 17, "g" to 0,
        "h" to 18, "j" to 12, "k" to 15, "l" to 5, "m" to 6, "n" to 2,
        "p" to 17, "r" to 5, "s" to 9, "sh" to 9, "t" to 16, "th" to 16,
        "ts" to 12, "v" to 7, "w" to 11, "ks" to 9, "z" to 12,
    )
}

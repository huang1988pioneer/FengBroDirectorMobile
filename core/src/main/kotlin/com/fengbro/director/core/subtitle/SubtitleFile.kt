package com.fengbro.director.core.subtitle

import com.fengbro.director.core.model.LyricWord
import com.fengbro.director.core.model.MediaItem
import com.fengbro.director.core.model.SubtitleCue
import com.fengbro.director.core.model.SubtitleParseResult
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlin.math.max
import kotlin.math.pow

object SubtitleFile {
    private val timedLine = Regex(
        """((?:\d{1,2}:)?\d{1,2}:\d{2}[,.]\d{1,3})\s*-->\s*((?:\d{1,2}:)?\d{1,2}:\d{2}[,.]\d{1,3})""",
    )
    private val assTime = Regex("""^(\d+):(\d{2}):(\d{2})\.(\d{1,2})$""")
    private val lrcMeta = Regex("""^\[(ti|ar|al|au|by|offset|length|re|ve|id):(.*)\]\s*$""", RegexOption.IGNORE_CASE)
    private val lrcTime = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val lrcWordAbs = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val microDvd = Regex("""^\{(\d+)\}\{(\d+)\}(.*)$""")
    private val samiSync = Regex("""<SYNC\s+Start\s*=\s*"?(\d+)"?\s*>""", RegexOption.IGNORE_CASE)
    private val overrideBlock = Regex("""\{[^}]*}""")
    private val htmlTag = Regex("""<[^>]+>""")

    fun isSubtitlePath(path: String): Boolean {
        val ext = File(path).extension.lowercase()
        return ext in setOf("srt", "vtt", "ass", "ssa", "sub", "lrc", "sbv", "smi", "sami")
    }

    fun isLrcPath(path: String): Boolean =
        File(path).extension.equals("lrc", ignoreCase = true)

    fun hydrate(item: MediaItem) {
        try {
            val parsed = if (File(item.path).exists()) parseDetailed(item.path)
            else SubtitleParseResult(cues = item.cues.orEmpty())
            val cues = parsed.cues
            if (cues.isNotEmpty()) {
                item.cues = cues.toMutableList()
                item.duration = cues.maxOf { it.end }
                if (!parsed.title.isNullOrBlank()) item.name = parsed.title
                if (!parsed.artist.isNullOrBlank() && item.note.isNullOrBlank()) item.note = parsed.artist
            } else if (!item.cues.isNullOrEmpty()) {
                item.duration = item.cues!!.maxOf { it.end }
            } else {
                item.cues = mutableListOf()
            }
        } catch (_: Exception) {
            if (!item.cues.isNullOrEmpty()) item.duration = item.cues!!.maxOf { it.end }
            else item.cues = mutableListOf()
        }
        item.hasVideo = false
        item.hasAudio = false
        runCatching { item.sizeBytes = File(item.path).length() }
    }

    fun parse(path: String): List<SubtitleCue> {
        if (!File(path).exists()) return emptyList()
        return parseDetailed(path).cues
    }

    fun parseDetailed(path: String): SubtitleParseResult {
        if (!File(path).exists()) return SubtitleParseResult.Empty
        return parseTextDetailed(readText(File(path)), File(path).extension)
    }

    fun parseText(text: String, extension: String? = null): List<SubtitleCue> =
        parseTextDetailed(text, extension).cues

    fun parseTextDetailed(text: String, extension: String? = null): SubtitleParseResult {
        if (text.isBlank()) return SubtitleParseResult.Empty
        var ext = (extension ?: "").lowercase()
        if (!ext.startsWith(".") && ext.isNotEmpty()) ext = ".$ext"

        if (ext == ".lrc") return parseLrc(text)

        val cues = when (ext) {
            ".vtt" -> parseTimedBlocks(text, vtt = true)
            ".ass", ".ssa" -> parseAss(text)
            ".sbv" -> parseSbv(text)
            ".smi", ".sami" -> parseSami(text)
            ".sub" -> if (looksLikeMicroDvd(text)) parseMicroDvd(text) else parseTimedBlocks(text, vtt = false)
            else -> parseTimedBlocks(text, vtt = ext == ".vtt")
        }
        return SubtitleParseResult(cues = cues.filter { it.text.isNotBlank() })
    }

    fun readText(file: File): String {
        val bytes = file.readBytes()
        if (bytes.isEmpty()) return ""
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            if (bytes.size >= 4 && bytes[2] == 0.toByte() && bytes[3] == 0.toByte()) {
                return String(bytes, 4, bytes.size - 4, Charset.forName("UTF-32LE"))
            }
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
        }
        return try {
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            String(bytes, Charset.defaultCharset())
        }
    }

    private fun parseTimedBlocks(text: String, vtt: Boolean): List<SubtitleCue> {
        val lines = splitLines(text)
        val cues = mutableListOf<SubtitleCue>()
        var i = 0
        if (vtt && i < lines.size && lines[i].startsWith("WEBVTT", ignoreCase = true)) i++

        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) {
                i++; continue
            }
            if (vtt && startsWithWord(line, "NOTE", "STYLE", "REGION")) {
                i++
                while (i < lines.size && lines[i].trim().isNotEmpty()) i++
                continue
            }

            var match = timedLine.find(line)
            if (match == null) {
                if (i + 1 < lines.size && timedLine.containsMatchIn(lines[i + 1])) {
                    i++
                    match = timedLine.find(lines[i])
                } else {
                    i++; continue
                }
            }
            if (match == null) {
                i++; continue
            }

            val start = parseClock(match.groupValues[1])
            var end = parseClock(match.groupValues[2])
            i++
            val body = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().isNotEmpty()) {
                body.add(lines[i])
                i++
            }
            val cueText = cleanText(body.joinToString("\n"))
            if (cueText.isBlank()) continue
            if (end <= start) end = start + 0.2
            cues.add(SubtitleCue(start = start, end = end, text = cueText))
        }
        return cues
    }

    private fun parseAss(text: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        for (raw in splitLines(text)) {
            val line = raw.trim()
            if (!line.startsWith("Dialogue:", ignoreCase = true)) continue
            val colon = line.indexOf(':')
            if (colon < 0) continue
            val rest = line.substring(colon + 1)
            var commas = 0
            var fieldStart = 0
            var startRaw: String? = null
            var endRaw: String? = null
            for (i in rest.indices) {
                if (rest[i] != ',') continue
                commas++
                if (commas == 2) startRaw = rest.substring(fieldStart, i).trim()
                if (commas == 3) endRaw = rest.substring(fieldStart, i).trim()
                if (commas == 9) {
                    val start = parseAssClock(startRaw)
                    var end = parseAssClock(endRaw)
                    val cueText = cleanText(
                        rest.substring(i + 1).replace("\\N", "\n").replace("\\n", "\n"),
                    )
                    if (cueText.isNotBlank()) {
                        if (end <= start) end = start + 0.2
                        cues.add(SubtitleCue(start = start, end = end, text = cueText))
                    }
                    break
                }
                fieldStart = i + 1
            }
        }
        return cues
    }

    private data class LrcHit(val time: Double, val text: String, val words: List<LyricWord>?)

    private fun parseLrc(text: String): SubtitleParseResult {
        val hits = mutableListOf<LrcHit>()
        var offset = 0.0
        var length: Double? = null
        var title: String? = null
        var artist: String? = null

        for (raw in splitLines(text)) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            val meta = lrcMeta.matchEntire(line)
            if (meta != null) {
                val key = meta.groupValues[1].lowercase()
                val value = meta.groupValues[2].trim()
                when (key) {
                    "ti" -> if (value.isNotEmpty()) title = value
                    "ar" -> if (value.isNotEmpty()) artist = value
                    "offset" -> value.toDoubleOrNull()?.let { offset = it / 1000.0 }
                    "length" -> length = parseLrcLength(value)
                }
                continue
            }

            val taken = tryTakeLeadingTimes(line) ?: continue
            val lineAbs = taken.starts[0] + offset
            val (lyric, words) = parseLrcPayload(taken.payload, lineAbs, offset)
            for (start in taken.starts) {
                val abs = start + offset
                hits.add(LrcHit(abs, lyric, shiftWords(words, abs - lineAbs)))
            }
        }

        hits.sortBy { it.time }
        val cues = mutableListOf<SubtitleCue>()
        for (i in hits.indices) {
            val hit = hits[i]
            val next = hits.getOrNull(i + 1)?.time
            var end = next ?: wordEnd(hit) ?: length ?: (hit.time + 4)
            if (length != null && length > 0 && end > length) end = length
            if (end <= hit.time) end = hit.time + 0.2
            if (hit.text.isBlank()) continue
            cues.add(
                SubtitleCue(
                    start = hit.time,
                    end = end,
                    text = hit.text,
                    words = cloneWords(hit.words, hit.time, end),
                ),
            )
        }
        return SubtitleParseResult(title = title, artist = artist, cues = cues)
    }

    private data class LeadingTimes(val starts: List<Double>, val payload: String)

    private fun tryTakeLeadingTimes(line: String): LeadingTimes? {
        val starts = mutableListOf<Double>()
        var i = 0
        while (i < line.length) {
            while (i < line.length && line[i].isWhitespace()) i++
            if (i >= line.length || line[i] != '[') break
            val match = lrcTime.find(line, i) ?: break
            if (match.range.first != i) break
            starts.add(parseLrcStamp(match))
            i = match.range.last + 1
        }
        if (starts.isEmpty()) return null
        val payload = if (i < line.length) line.substring(i) else ""
        return LeadingTimes(starts, payload)
    }

    private fun parseLrcPayload(
        payload: String,
        lineAbs: Double,
        offset: Double,
    ): Pair<String, List<LyricWord>?> {
        if (payload.isBlank()) return "" to null
        val words = mutableListOf<LyricWord>()
        var i = 0
        var pending: Double? = null
        var tagged = false
        while (i < payload.length) {
            val tag = tryReadWordTag(payload, i, offset)
            if (tag != null) {
                tagged = true
                pending = if (tag.relative) lineAbs + tag.time else tag.time
                i = tag.end
                continue
            }
            val next = nextTagIndex(payload, i)
            val chunk = payload.substring(i, next)
            i = next
            if (chunk.isEmpty()) continue
            val start = pending ?: if (words.isEmpty()) lineAbs else words.last().end
            pending = null
            words.add(LyricWord(text = chunk, start = start, end = start))
        }
        if (!tagged) return payload.trim() to null

        val stripped = stripDuplicatedLyricPrefix(words)
        for (w in stripped.indices) {
            var end = if (w + 1 < stripped.size) stripped[w + 1].start else stripped[w].start + 0.35
            if (end <= stripped[w].start) end = stripped[w].start + 0.12
            stripped[w].end = end
        }
        val text = stripped.joinToString("") { it.text }.trim()
        return text to stripped.ifEmpty { null }
    }

    private fun stripDuplicatedLyricPrefix(words: List<LyricWord>): MutableList<LyricWord> {
        if (words.size < 2) return words.toMutableList()
        val prefix = words[0].text
        val rest = words.drop(1).joinToString("") { it.text }
        if (rest.isEmpty()) return words.toMutableList()
        val prefixNorm = compactLyric(prefix)
        val restNorm = compactLyric(rest)
        if (prefixNorm.isEmpty()) return words.drop(1).toMutableList()
        if (prefixNorm == restNorm || restNorm.startsWith(prefixNorm)) return words.drop(1).toMutableList()
        if (prefix.endsWith(rest)) {
            val leftover = prefix.substring(0, prefix.length - rest.length)
            if (leftover.isBlank()) return words.drop(1).toMutableList()
            words[0].text = leftover
        }
        return words.toMutableList()
    }

    private fun compactLyric(text: String): String =
        if (text.isBlank()) "" else text.filter { !it.isWhitespace() }

    private fun nextTagIndex(payload: String, start: Int): Int {
        for (i in start until payload.length) {
            if (payload[i] == '<' || payload[i] == '[') return i
        }
        return payload.length
    }

    private data class WordTag(val end: Int, val time: Double, val relative: Boolean)

    private fun tryReadWordTag(payload: String, index: Int, offset: Double): WordTag? {
        if (index >= payload.length) return null
        if (payload[index] == '<') {
            val close = payload.indexOf('>', index + 1)
            if (close < 0) return null
            val inner = payload.substring(index + 1, close)
            val comma = inner.indexOf(',')
            if (comma > 0) {
                val relMs = inner.substring(0, comma).trim().toDoubleOrNull()
                val dur = inner.substring(comma + 1).trim().toDoubleOrNull()
                if (relMs != null && dur != null) {
                    return WordTag(close + 1, relMs / 1000.0, relative = true)
                }
            }
            val abs = lrcWordAbs.find(payload, index)
            if (abs != null && abs.range.first == index) {
                return WordTag(abs.range.last + 1, parseLrcStamp(abs) + offset, relative = false)
            }
        }
        if (payload[index] == '[') {
            val abs = lrcTime.find(payload, index)
            if (abs != null && abs.range.first == index) {
                return WordTag(abs.range.last + 1, parseLrcStamp(abs) + offset, relative = false)
            }
        }
        return null
    }

    private fun parseLrcStamp(match: MatchResult): Double {
        val minutes = match.groupValues[1].toInt()
        val seconds = match.groupValues[2].toInt()
        val frac = if (match.groupValues.size > 3 && match.groupValues[3].isNotEmpty()) match.groupValues[3] else "0"
        val fracValue = frac.toInt() / 10.0.pow(frac.length)
        return minutes * 60 + seconds + fracValue
    }

    private fun parseLrcLength(value: String): Double? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        val match = lrcTime.find("[" + trimmed.trim('[', ']') + "]")
        if (match != null) return parseLrcStamp(match)
        return trimmed.toDoubleOrNull()
    }

    private fun wordEnd(hit: LrcHit): Double? = hit.words?.maxOfOrNull { it.end }

    private fun shiftWords(words: List<LyricWord>?, delta: Double): List<LyricWord>? {
        if (words.isNullOrEmpty()) return null
        if (kotlin.math.abs(delta) < 0.0005) return words
        return words.map { LyricWord(it.text, it.start + delta, it.end + delta) }
    }

    private fun cloneWords(words: List<LyricWord>?, lineStart: Double, lineEnd: Double): List<LyricWord>? {
        if (words.isNullOrEmpty()) return null
        val list = mutableListOf<LyricWord>()
        for (word in words) {
            val start = max(0.0, word.start - lineStart)
            val end = max(start + 0.04, minOf(lineEnd, word.end) - lineStart)
            if (word.text.isEmpty()) continue
            list.add(LyricWord(word.text, start, end))
        }
        return list.ifEmpty { null }
    }

    private fun parseSbv(text: String): List<SubtitleCue> {
        val lines = splitLines(text)
        val cues = mutableListOf<SubtitleCue>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) {
                i++; continue
            }
            val parts = line.split(',')
            if (parts.size >= 2 && looksLikeClock(parts[0]) && looksLikeClock(parts[1])) {
                val start = parseClock(parts[0])
                var end = parseClock(parts[1])
                i++
                val body = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().isNotEmpty()) {
                    body.add(lines[i]); i++
                }
                val cueText = cleanText(body.joinToString("\n"))
                if (cueText.isBlank()) continue
                if (end <= start) end = start + 0.2
                cues.add(SubtitleCue(start = start, end = end, text = cueText))
                continue
            }
            i++
        }
        return cues
    }

    private fun parseSami(text: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val matches = samiSync.findAll(text).toList()
        for (i in matches.indices) {
            val start = matches[i].groupValues[1].toInt() / 1000.0
            val from = matches[i].range.last + 1
            val to = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
            val chunk = text.substring(from, to)
            val cueText = cleanText(chunk)
            if (cueText.isBlank() || cueText == "\u00A0") continue
            var end = if (i + 1 < matches.size) matches[i + 1].groupValues[1].toInt() / 1000.0 else start + 3
            if (end <= start) end = start + 0.2
            cues.add(SubtitleCue(start = start, end = end, text = cueText))
        }
        return cues
    }

    private fun parseMicroDvd(text: String): List<SubtitleCue> {
        val fps = 25.0
        val cues = mutableListOf<SubtitleCue>()
        for (raw in splitLines(text)) {
            val match = microDvd.matchEntire(raw.trim()) ?: continue
            val start = match.groupValues[1].toInt() / fps
            var end = match.groupValues[2].toInt() / fps
            val cueText = cleanText(match.groupValues[3].replace('|', '\n'))
            if (cueText.isBlank()) continue
            if (end <= start) end = start + 0.2
            cues.add(SubtitleCue(start = start, end = end, text = cueText))
        }
        return cues
    }

    private fun looksLikeMicroDvd(text: String): Boolean {
        for (line in splitLines(text)) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            return microDvd.matches(trimmed)
        }
        return false
    }

    private fun looksLikeClock(value: String): Boolean {
        val v = value.trim()
        return timedLine.containsMatchIn("$v --> $v") || assTime.matches(v)
    }

    private fun parseClock(rawIn: String): Double {
        val raw = rawIn.trim().replace(',', '.')
        val parts = raw.split(':')
        return try {
            when (parts.size) {
                3 -> parseNum(parts[0]) * 3600 + parseNum(parts[1]) * 60 + parseNum(parts[2])
                2 -> parseNum(parts[0]) * 60 + parseNum(parts[1])
                else -> parseNum(raw)
            }
        } catch (_: Exception) {
            0.0
        }
    }

    private fun parseAssClock(raw: String?): Double {
        if (raw.isNullOrBlank()) return 0.0
        val match = assTime.matchEntire(raw.trim()) ?: return parseClock(raw)
        val hours = parseNum(match.groupValues[1])
        val minutes = parseNum(match.groupValues[2])
        val seconds = parseNum(match.groupValues[3])
        val frac = match.groupValues[4]
        val centi = parseNum(frac) / 10.0.pow(frac.length)
        return hours * 3600 + minutes * 60 + seconds + centi
    }

    private fun parseNum(raw: String): Double = raw.toDouble()

    private fun cleanText(textIn: String): String {
        var text = textIn.replace("\\h", " ")
        text = overrideBlock.replace(text, "")
        text = htmlTag.replace(text, "")
        text = text
            .replace("&nbsp;", " ", ignoreCase = true)
            .replace("&#160;", " ", ignoreCase = true)
            .replace("&amp;", "&", ignoreCase = true)
            .replace("&lt;", "<", ignoreCase = true)
            .replace("&gt;", ">", ignoreCase = true)
            .replace("&quot;", "\"", ignoreCase = true)
        val lines = text.replace("\r\n", "\n").replace('\r', '\n')
            .split('\n')
            .map { it.replace(Regex("[ \\t]+"), " ").trim() }
            .filter { it.isNotEmpty() }
        return lines.joinToString("\n")
    }

    private fun splitLines(text: String): List<String> =
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n')

    private fun startsWithWord(line: String, vararg words: String): Boolean {
        for (word in words) {
            if (line.startsWith(word, ignoreCase = true) &&
                (line.length == word.length || line[word.length].isWhitespace())
            ) return true
        }
        return false
    }
}

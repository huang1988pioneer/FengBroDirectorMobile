package com.fengbro.director.core

import com.fengbro.director.core.subtitle.SubtitleFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubtitleFileTest {
    @Test
    fun lrc_simpleLines_useNextStampAsEnd() {
        val cues = SubtitleFile.parseText(
            """
            [ti:Demo]
            [00:12.00]第一句
            [00:16.50]第二句
            [00:20.00]
            """.trimIndent(),
            ".lrc",
        )
        assertEquals(2, cues.size)
        assertEquals("第一句", cues[0].text)
        assertEquals(12.0, cues[0].start, 0.001)
        assertEquals(16.5, cues[0].end, 0.001)
        assertEquals("第二句", cues[1].text)
        assertEquals(16.5, cues[1].start, 0.001)
        assertEquals(20.0, cues[1].end, 0.001)
        assertNull(cues[0].words)
    }

    @Test
    fun lrc_enhancedWords_stripTagsAndKeepTiming() {
        val cues = SubtitleFile.parseText(
            "[00:12.00]<00:12.00>你<00:12.40>好<00:12.80>嗎\n[00:16.00]",
            ".lrc",
        )
        assertEquals(1, cues.size)
        assertEquals("你好嗎", cues[0].text)
        assertFalse(cues[0].text.contains("<"))
        assertEquals(3, cues[0].words!!.size)
        assertEquals("你", cues[0].words!![0].text)
        assertEquals(0.0, cues[0].words!![0].start, 0.001)
        assertEquals(0.4, cues[0].words!![1].start, 0.001)
        assertEquals("嗎", cues[0].words!![2].text)
    }

    @Test
    fun lrc_inlineBrackets_areWordTimes() {
        val cues = SubtitleFile.parseText(
            "[00:10.00]你[00:10.40]好[00:10.80]嗎\n[00:14.00]下一句",
            ".lrc",
        )
        assertEquals(2, cues.size)
        assertEquals("你好嗎", cues[0].text)
        assertEquals(3, cues[0].words!!.size)
        assertEquals(0.4, cues[0].words!![1].start, 0.001)
        assertEquals("下一句", cues[1].text)
        assertNull(cues[1].words)
    }

    @Test
    fun lrc_krcRelativeWords_useMilliseconds() {
        val cues = SubtitleFile.parseText(
            "[00:05.00]<0,400>春<400,350>風\n[00:08.00]",
            ".lrc",
        )
        assertEquals(1, cues.size)
        assertEquals("春風", cues[0].text)
        assertEquals(2, cues[0].words!!.size)
        assertEquals(0.0, cues[0].words!![0].start, 0.001)
        assertEquals(0.4, cues[0].words!![1].start, 0.001)
    }

    @Test
    fun lrc_offsetAndRepeatedStamp() {
        val cues = SubtitleFile.parseText(
            """
            [offset:500]
            [00:10.00][00:20.00]副歌
            [00:12.00]
            """.trimIndent(),
            ".lrc",
        )
        assertEquals(2, cues.size)
        assertEquals("副歌", cues[0].text)
        assertEquals(10.5, cues[0].start, 0.001)
        assertEquals(12.5, cues[0].end, 0.001)
        assertEquals(20.5, cues[1].start, 0.001)
    }

    @Test
    fun lrc_colonFraction_andTitle() {
        val parsed = SubtitleFile.parseTextDetailed(
            "[ti:夜車]\n[ar:鋒兄]\n[00:01:50]上路",
            ".lrc",
        )
        assertEquals("夜車", parsed.title)
        assertEquals("鋒兄", parsed.artist)
        assertEquals(1, parsed.cues.size)
        assertEquals(1.5, parsed.cues[0].start, 0.001)
        assertEquals("上路", parsed.cues[0].text)
    }

    @Test
    fun lrc_millisecondPrecision() {
        val cues = SubtitleFile.parseText("[00:01.250]一字\n[00:02.000]二字", ".lrc")
        assertEquals(1.25, cues[0].start, 0.001)
        assertEquals(2.0, cues[0].end, 0.001)
    }

    @Test
    fun isLrcPath_acceptsExtension() {
        assertTrue(SubtitleFile.isLrcPath("""C:\music\song.lrc"""))
        assertTrue(SubtitleFile.isLrcPath("Song.LRC"))
        assertFalse(SubtitleFile.isLrcPath("song.srt"))
    }

    @Test
    fun lrc_fullLineThenWordTags_doesNotDuplicateText() {
        val cues = SubtitleFile.parseText(
            "[00:12.00]你好嗎<00:12.00>你<00:12.40>好<00:12.80>嗎\n[00:16.00]",
            ".lrc",
        )
        assertEquals(1, cues.size)
        assertEquals("你好嗎", cues[0].text)
        assertEquals(3, cues[0].words!!.size)
        assertEquals("你", cues[0].words!![0].text)
        assertEquals("嗎", cues[0].words!![2].text)
    }

    @Test
    fun lrc_bilingualPrefixKeepsTranslationOnce() {
        val cues = SubtitleFile.parseText(
            "[00:10.00]Hello 你好<00:10.00>你<00:10.40>好\n[00:14.00]",
            ".lrc",
        )
        assertEquals(1, cues.size)
        assertEquals("Hello 你好", cues[0].text)
        assertEquals(3, cues[0].words!!.size)
        assertEquals("Hello ", cues[0].words!![0].text)
        assertEquals("你", cues[0].words!![1].text)
    }

    @Test
    fun srt_basicCues() {
        val cues = SubtitleFile.parseText(
            """
            1
            00:00:01,000 --> 00:00:03,500
            你好

            2
            00:00:04,000 --> 00:00:06,000
            世界
            """.trimIndent(),
            ".srt",
        )
        assertEquals(2, cues.size)
        assertEquals(1.0, cues[0].start, 0.001)
        assertEquals(3.5, cues[0].end, 0.001)
        assertEquals("你好", cues[0].text)
        assertEquals("世界", cues[1].text)
    }
}

package com.fengbro.director

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fengbro.director.core.subtitle.SubtitleFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubtitleFileAndroidTest {
    @Test
    fun testInitializesAndStripsAssOverrideBlocks() {
        assertFalse(SubtitleFile.isSubtitlePath("frame.png"))

        val cues = SubtitleFile.parseText(
            """
            1
            00:00:01,000 --> 00:00:02,000
            {\an8}Visible text
            """.trimIndent(),
            ".srt",
        )

        assertEquals(1, cues.size)
        assertEquals("Visible text", cues.single().text)
    }
}

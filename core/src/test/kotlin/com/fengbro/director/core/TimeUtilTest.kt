package com.fengbro.director.core

import com.fengbro.director.core.model.MediaKind
import com.fengbro.director.core.time.TimeUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimeUtilTest {
    @Test
    fun timelinePanScroll_pullsContentWithTheMouse() {
        assertEquals(80.0, TimeUtil.timelinePanScroll(100.0, 40.0, 60.0))
        assertEquals(130.0, TimeUtil.timelinePanScroll(100.0, 40.0, 10.0))
        assertFalse(TimeUtil.timelineDragExceededSlop(40.0, 42.0))
        assertTrue(TimeUtil.timelineDragExceededSlop(40.0, 50.0))
    }

    @Test
    fun workspaceSeconds_growsWithLongImportedMedia() {
        assertEquals(180.0, TimeUtil.workspaceSeconds(0.0))
        assertEquals(180.0, TimeUtil.workspaceSeconds(100.0))
        assertTrue(TimeUtil.workspaceSeconds(600.0) >= 645)
        assertTrue(TimeUtil.workspaceSeconds(10.0, 200.0) >= 245)
    }

    @Test
    fun fitPixelsPerSecond_showsTwoMinuteClipInLaptopWindow() {
        val pps = TimeUtil.fitPixelsPerSecond(144.0, 1440.0)
        assertTrue(pps < 12)
        val visible = (1440 - TimeUtil.TIMELINE_GUTTER) / pps
        assertTrue(visible >= 144)
    }

    @Test
    fun placeClipTime_appendsWhenDroppedAtStart() {
        assertEquals(8.0, TimeUtil.placeClipTime(0.0, listOf(8.0)))
        assertEquals(12.0, TimeUtil.placeClipTime(12.0, listOf(8.0)))
        assertEquals(4.0, TimeUtil.placeClipTime(4.0, emptyList()))
    }

    @Test
    fun mediaClipDuration_usesFallbackWhenProbeMissing() {
        assertEquals(5.0, TimeUtil.mediaClipDuration(MediaKind.Video, 0.0))
        assertEquals(5.0, TimeUtil.mediaClipDuration(MediaKind.Image, 0.0))
        assertEquals(12.5, TimeUtil.mediaClipDuration(MediaKind.Video, 12.5))
    }

    @Test
    fun durationSliderCeiling_coversLibraryBeforeAClipIsSelected() {
        assertEquals(60.0, TimeUtil.durationSliderCeiling(0.0, 0.0, emptyList(), emptyList()))
        assertEquals(174.13, TimeUtil.durationSliderCeiling(0.0, 0.0, listOf(144.13), emptyList()), 0.01)
        assertEquals(174.13, TimeUtil.durationSliderCeiling(5.0, 5.0, listOf(144.13), listOf(5.0)), 0.01)
    }

    @Test
    fun placeOnLane_pushesPastOverlap() {
        val start = TimeUtil.placeOnLane(59.0, 2.0, listOf(0.0 to 60.0))
        assertEquals(60.0, start, 1e-5)
        assertFalse(TimeUtil.rangesOverlap(0.0, 60.0, start, start + 2))
    }

    @Test
    fun placeOnLane_allowsTouchingAndUsesAFittingGap() {
        assertEquals(12.0, TimeUtil.placeOnLane(12.0, 5.0, listOf(0.0 to 8.0)))
        assertEquals(10.0, TimeUtil.placeOnLane(8.0, 5.0, listOf(0.0 to 10.0, 20.0 to 30.0)))
        assertEquals(30.0, TimeUtil.placeOnLane(12.0, 12.0, listOf(0.0 to 10.0, 20.0 to 30.0)))
        assertFalse(TimeUtil.rangesOverlap(0.0, 60.0, 60.0, 62.0))
        assertTrue(TimeUtil.rangesOverlap(0.0, 60.0, 59.0, 61.0))
    }

    @Test
    fun formatClock_minutesAndHours() {
        assertEquals("00:05.00", TimeUtil.formatClock(5.0))
        assertEquals("01:00:00", TimeUtil.formatClock(3600.0))
    }
}

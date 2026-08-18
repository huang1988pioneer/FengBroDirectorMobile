package com.fengbro.director.core

import com.fengbro.director.core.layout.EditorWindowSpec
import com.fengbro.director.core.layout.HeightClass
import com.fengbro.director.core.layout.WidthClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EditorWindowSpecTest {
    @Test
    fun phonePortrait_usesStackedSheets() {
        val spec = EditorWindowSpec.from(411, 891)
        assertEquals(WidthClass.Compact, spec.widthClass)
        assertFalse(spec.useBench)
        assertFalse(spec.useLandscapeSplit)
        assertTrue(spec.useLibrarySheet)
        assertTrue(spec.useInspectorSheet)
        assertFalse(spec.showStartupTwoPane)
        assertEquals(188, spec.timelineHeightDp)
    }

    @Test
    fun phoneLandscape_splitsPreviewAndTimeline() {
        val spec = EditorWindowSpec.from(891, 411)
        assertEquals(WidthClass.Expanded, spec.widthClass)
        assertEquals(HeightClass.Compact, spec.heightClass)
        assertFalse(spec.useBench)
        assertTrue(spec.useLandscapeSplit)
        assertTrue(spec.useLibrarySheet)
        assertTrue(spec.timelineHeightDp in 140..220)
    }

    @Test
    fun tabletPortrait_usesDesktopBench() {
        val spec = EditorWindowSpec.from(800, 1280)
        assertEquals(WidthClass.Medium, spec.widthClass)
        assertTrue(spec.useBench)
        assertFalse(spec.useLibrarySheet)
        assertTrue(spec.showStartupTwoPane)
        assertEquals(260, spec.libraryWidthDp)
        assertEquals(236, spec.inspectorWidthDp)
        assertEquals(280, spec.timelineHeightDp)
    }

    @Test
    fun tabletLandscape_widensLibraryToDesktop300() {
        val spec = EditorWindowSpec.from(1280, 800)
        assertEquals(WidthClass.Expanded, spec.widthClass)
        assertTrue(spec.useBench)
        assertEquals(300, spec.libraryWidthDp)
        assertEquals(268, spec.inspectorWidthDp)
        assertTrue(spec.useExportDialog)
    }

    @Test
    fun splitScreenCompact_staysPhoneShaped() {
        val spec = EditorWindowSpec.from(400, 800)
        assertEquals(WidthClass.Compact, spec.widthClass)
        assertFalse(spec.useBench)
        assertFalse(spec.showStartupTwoPane)
    }

    @Test
    fun innerFoldable_isTabletWorkspace() {
        val spec = EditorWindowSpec.from(673, 841)
        assertEquals(WidthClass.Medium, spec.widthClass)
        assertEquals(HeightClass.Medium, spec.heightClass)
        assertTrue(spec.useBench)
        assertTrue(spec.showStartupTwoPane)
    }
}

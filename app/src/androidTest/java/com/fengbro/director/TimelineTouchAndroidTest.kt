package com.fengbro.director

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import com.fengbro.director.core.model.ClipKind
import com.fengbro.director.core.model.TimelineClip
import com.fengbro.director.core.model.TimelineTrack
import com.fengbro.director.core.model.TrackKind
import com.fengbro.director.ui.components.TimelineView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TimelineTouchAndroidTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tappingClipSelectsItAndMovesPlayhead() {
        val clip = TimelineClip(
            id = "clip",
            kind = ClipKind.Video,
            start = 0.0,
            duration = 10.0,
        )
        val track = TimelineTrack(
            id = "video",
            kind = TrackKind.Video,
            clips = mutableListOf(clip),
        )
        var selectedClipId: String? = null
        var soughtTime = -1.0

        compose.setContent {
            TimelineView(
                tracks = listOf(track),
                playhead = 0.0,
                workspaceSeconds = 12.0,
                pixelsPerSecond = 48.0,
                selectedClipId = selectedClipId,
                onViewportWidth = {},
                onSeek = { soughtTime = it },
                onSelect = { clipId, _ -> selectedClipId = clipId },
                modifier = Modifier.width(360.dp).height(120.dp),
            )
        }

        compose.onNodeWithTag("timeline-track-video").performTouchInput { click(center) }

        compose.runOnIdle {
            assertEquals("clip", selectedClipId)
            assertTrue("Timeline tap did not move the playhead", soughtTime > 0.0)
        }
    }
}

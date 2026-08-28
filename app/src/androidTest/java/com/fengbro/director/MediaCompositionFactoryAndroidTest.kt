package com.fengbro.director

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fengbro.director.core.export.ExportPlan
import com.fengbro.director.media.CompositionPreview
import com.fengbro.director.media.MediaCompositionFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class MediaCompositionFactoryAndroidTest {
    @Test
    fun emptyTimelineStillBuildsPlayableVideoComposition() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val plan = ExportPlan(
            durationSec = 2.0,
            width = 320,
            height = 180,
            frameRate = 24.0,
            visuals = emptyList(),
            audios = emptyList(),
            titles = emptyList(),
            watermark = true,
            background = "#000000",
        )

        val composition = MediaCompositionFactory(context).build(plan)

        assertEquals(1, composition.sequences.size)
        val video = composition.sequences.single()
        assertTrue(video.trackTypes.contains(C.TRACK_TYPE_VIDEO))
        assertEquals(2_000_000L, video.editedMediaItems.single().durationUs)
        assertEquals(2, composition.effects.videoEffects.size)
    }

    @Test
    fun compositionPlayerPreparesFactoryOutput() = runBlocking(Dispatchers.Main) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val factory = MediaCompositionFactory(context)
        val preview = CompositionPreview(context, factory)
        val plan = ExportPlan(
            durationSec = 1.0,
            width = 320,
            height = 180,
            frameRate = 24.0,
            visuals = emptyList(),
            audios = emptyList(),
            titles = emptyList(),
            watermark = false,
            background = "#000000",
        )

        try {
            preview.load(plan, positionSeconds = 0.25, playWhenReady = false)
            withTimeout(5_000) {
                while (preview.player.playbackState != Player.STATE_READY) delay(50)
            }
            assertEquals(1, preview.player.mediaItemCount)
            assertTrue(preview.player.currentPosition in 200L..350L)
        } finally {
            preview.release()
        }
    }
}

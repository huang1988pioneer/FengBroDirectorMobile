package com.fengbro.director

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fengbro.director.core.export.ExportPlan
import com.fengbro.director.core.model.MediaItem
import com.fengbro.director.core.model.MediaKind
import com.fengbro.director.media.MediaImporter
import com.fengbro.director.media.TransformerExporter
import com.fengbro.director.ui.components.MediaCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LibraryMediaAndroidTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun longPressDragPlacesMediaOnTimeline() {
        var placements = 0
        compose.setContent {
            MediaCard(
                item = MediaItem(id = "video-1", name = "測試影片", kind = MediaKind.Video, duration = 6.0),
                onPlace = { placements += 1 },
                onRemove = {},
            )
        }

        compose.onNodeWithTag("media-card-video-1").performTouchInput {
            down(center)
            advanceEventTime(650)
            moveBy(Offset(0f, -120f))
            up()
        }

        compose.runOnIdle { assertEquals(1, placements) }
    }

    @Test
    fun importingVideoCreatesReusableThumbnail() = runBlocking(Dispatchers.Main) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val plan = ExportPlan(
            durationSec = 0.5,
            width = 320,
            height = 180,
            frameRate = 24.0,
            visuals = emptyList(),
            audios = emptyList(),
            titles = emptyList(),
            watermark = false,
            background = "#000000",
        )
        val exported = TransformerExporter(context).export(plan, "thumbnail-source.mp4") { }
        var imported: MediaItem? = null
        try {
            imported = MediaImporter(context).importUri(requireNotNull(exported.galleryUri))
            val thumbnail = imported?.thumbPath?.let(::File)
            org.junit.Assert.assertTrue("Video thumbnail was not created", thumbnail?.isFile == true)
            org.junit.Assert.assertTrue("Video thumbnail was empty", (thumbnail?.length() ?: 0L) > 0L)
        } finally {
            imported?.let { File(it.path).delete() }
            imported?.thumbPath?.let { File(it).delete() }
            exported.galleryUri?.let { context.contentResolver.delete(it, null, null) }
            exported.file.delete()
        }
    }

    @Test
    fun mediaCardShowsGeneratedThumbnail() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val thumbnail = File(context.cacheDir, "library-card-thumbnail.jpg")
        val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(40, 160, 220))
        }
        thumbnail.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        try {
            compose.setContent {
                MediaCard(
                    item = MediaItem(
                        id = "thumb-1",
                        name = "縮圖影片",
                        kind = MediaKind.Video,
                        duration = 6.0,
                        thumbPath = thumbnail.absolutePath,
                    ),
                    onPlace = {},
                    onRemove = {},
                )
            }
            compose.onNodeWithContentDescription("影片：縮圖影片").assertIsDisplayed()
        } finally {
            thumbnail.delete()
        }
    }
}

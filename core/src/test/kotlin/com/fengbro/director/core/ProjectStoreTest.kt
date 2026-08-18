package com.fengbro.director.core

import com.fengbro.director.core.model.MediaItem
import com.fengbro.director.core.model.MediaKind
import com.fengbro.director.core.store.ProjectStore
import com.fengbro.director.core.timeline.EditorSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempFile

class ProjectStoreTest {
    @Test
    fun snapshotRoundTrip_keepsClip() {
        val session = EditorSession()
        val media = MediaItem(name = "a", kind = MediaKind.Video, duration = 4.0, path = "a.mp4", hasVideo = true)
        session.addMedia(media)
        session.placeMedia(media, null, 0.0)
        session.project.includeWatermark = true
        val json = ProjectStore.snapshot(session.project)
        val restored = ProjectStore.restore(json, null)
        assertEquals(session.project.name, restored.name)
        assertTrue(restored.includeWatermark)
        assertEquals(1, restored.tracks.first { it.kind == com.fengbro.director.core.model.TrackKind.Video }.clips.size)
        assertEquals(4.0, restored.duration, 0.01)
    }

    @Test
    fun saveAndLoad_file() {
        val session = EditorSession()
        val media = MediaItem(name = "still", kind = MediaKind.Image, duration = 5.0, path = "still.png")
        session.addMedia(media)
        session.placeMedia(media, null, 0.0)
        val file = createTempFile(suffix = ".fbdproj").toFile()
        try {
            ProjectStore.save(session.project, file.absolutePath)
            val loaded = ProjectStore.load(file.absolutePath)
            assertEquals("still", loaded.media.single().name)
            assertEquals(5.0, loaded.duration, 0.01)
        } finally {
            file.delete()
        }
    }
}

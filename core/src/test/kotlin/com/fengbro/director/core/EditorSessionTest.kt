package com.fengbro.director.core

import com.fengbro.director.core.model.MediaItem
import com.fengbro.director.core.model.MediaKind
import com.fengbro.director.core.model.TimelineClip
import com.fengbro.director.core.model.TrackKind
import com.fengbro.director.core.time.TimeUtil
import com.fengbro.director.core.timeline.EditorSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EditorSessionTest {
    @Test
    fun placeMedia_longVideo_zoomsOutSoTheClipFits() {
        val session = EditorSession()
        session.timelineViewportWidth = 1440.0
        session.pixelsPerSecond = 48.0
        val video = MediaItem(
            name = "litvideo",
            kind = MediaKind.Video,
            duration = 144.13,
            path = "litvideo.mp4",
            hasVideo = true,
        )
        session.addMedia(video)
        session.placeMedia(video, null, 0.0)

        assertTrue(session.selectedClip!!.duration in 144.0..145.0)
        assertTrue(session.project.duration in 144.0..145.0)
        assertTrue(session.pixelsPerSecond < 12)
        val visible = (1440 - TimeUtil.TIMELINE_GUTTER) / session.pixelsPerSecond
        assertTrue(visible >= 144)
    }

    @Test
    fun durationSliderMax_growsWhenLongMediaEntersTheLibrary() {
        val session = EditorSession()
        assertEquals(60.0, session.durationSliderMax, 0.01)
        session.addMedia(
            MediaItem(
                name = "litvideo",
                kind = MediaKind.Video,
                duration = 144.13,
                path = "litvideo.mp4",
                hasVideo = true,
                hasAudio = true,
            ),
        )
        assertTrue(session.durationSliderMax in 174.0..175.0)
    }

    @Test
    fun placeMedia_secondImportExtendsTimeline() {
        val session = EditorSession()
        val first = MediaItem(name = "one", kind = MediaKind.Image, duration = 5.0, path = "one.png")
        val second = MediaItem(name = "two", kind = MediaKind.Image, duration = 5.0, path = "two.png")
        session.addMedia(first)
        session.addMedia(second)

        session.placeMedia(first, null, 0.0)
        assertEquals(5.0, session.project.duration, 0.01)

        session.placeMedia(second, null, 0.0)
        assertEquals(10.0, session.project.duration, 0.01)
        assertEquals(5.0, session.selectedClip!!.start, 0.01)
        assertTrue(session.workspaceSeconds >= 55)
    }

    @Test
    fun placeMedia_videoWithAudio_createsCompanionSound() {
        val session = EditorSession()
        val video = MediaItem(
            name = "litvideo",
            kind = MediaKind.Video,
            duration = 144.13,
            path = "litvideo.mp4",
            hasVideo = true,
            hasAudio = true,
        )
        session.addMedia(video)
        session.placeMedia(video, null, 0.0)

        val picture = session.project.tracks.first { it.kind == TrackKind.Video }.clips.single()
        val sound = session.project.tracks.first { it.kind == TrackKind.Audio }.clips.single()
        assertTrue(picture.duration in 144.0..145.0)
        assertTrue(sound.duration in 144.0..145.0)
        assertEquals(picture.duration, sound.duration, 0.01)
    }

    @Test
    fun trimClip_canLengthenImportedImage() {
        val session = EditorSession()
        val photo = MediaItem(name = "still", kind = MediaKind.Image, duration = 5.0, path = "still.png")
        session.addMedia(photo)
        session.placeMedia(photo, null, 0.0)
        session.trimClip(session.selectedClip!!, 0.0, 20.0, left = false)
        assertEquals(20.0, session.selectedClip!!.duration, 0.01)
        assertEquals(20.0, session.project.duration, 0.01)
        assertTrue(session.workspaceSeconds >= 65)
    }

    @Test
    fun trimClip_extendingVideoPastSource_setsFreeze() {
        val session = EditorSession()
        val video = MediaItem(
            name = "clip",
            kind = MediaKind.Video,
            duration = 3.0,
            path = "clip.mp4",
            hasVideo = true,
        )
        session.addMedia(video)
        session.placeMedia(video, null, 0.0)
        assertEquals(3.0, session.selectedClip!!.duration, 0.01)
        session.trimClip(session.selectedClip!!, 0.0, 8.0, left = false)
        assertEquals(8.0, session.selectedClip!!.duration, 0.01)
        assertTrue(session.selectedClip!!.freezeFrame)
        assertEquals(8.0, session.project.duration, 0.01)
    }

    @Test
    fun placeMedia_overlappingDrop_goesAfterExisting() {
        val session = EditorSession()
        session.snapEnabled = false
        val first = MediaItem(name = "one", kind = MediaKind.Video, duration = 60.0, path = "one.mp4", hasVideo = true)
        val second = MediaItem(name = "two", kind = MediaKind.Video, duration = 2.0, path = "two.mp4", hasVideo = true)
        session.addMedia(first)
        session.addMedia(second)
        val track = session.project.tracks.first { it.kind == TrackKind.Video }
        session.placeMedia(first, track, 0.0)
        session.placeMedia(second, track, 59.0)

        assertEquals(2, track.clips.size)
        assertEquals(0.0, track.clips[0].start, 0.01)
        assertEquals(60.0, track.clips[0].duration, 0.01)
        assertEquals(60.0, track.clips[1].start, 0.01)
        assertEquals(2.0, track.clips[1].duration, 0.01)
        assertFalse(
            TimeUtil.rangesOverlap(
                track.clips[0].start, track.clips[0].end,
                track.clips[1].start, track.clips[1].end,
            ),
        )
    }

    @Test
    fun moveClip_cannotOverlapNeighborOnSameLane() {
        val session = EditorSession()
        session.snapEnabled = false
        val track = session.project.tracks.first { it.kind == TrackKind.Video }
        val a = TimelineClip(kind = com.fengbro.director.core.model.ClipKind.Video, name = "a", start = 0.0, duration = 60.0)
        val b = TimelineClip(kind = com.fengbro.director.core.model.ClipKind.Video, name = "b", start = 60.0, duration = 2.0)
        track.clips.add(a)
        track.clips.add(b)
        session.moveClip(b, track, track, 59.0)
        assertEquals(60.0, b.start, 0.01)
        assertFalse(TimeUtil.rangesOverlap(a.start, a.end, b.start, b.end))
    }

    @Test
    fun trimClip_cannotExtendIntoNeighbor() {
        val session = EditorSession()
        session.snapEnabled = false
        val track = session.project.tracks.first { it.kind == TrackKind.Video }
        val a = TimelineClip(kind = com.fengbro.director.core.model.ClipKind.Video, name = "a", start = 0.0, duration = 60.0)
        val b = TimelineClip(kind = com.fengbro.director.core.model.ClipKind.Video, name = "b", start = 60.0, duration = 2.0)
        track.clips.add(a)
        track.clips.add(b)
        session.trimClip(a, 0.0, 80.0, left = false)
        assertEquals(60.0, a.duration, 0.01)
    }

    @Test
    fun splitAtPlayhead_cutsSelectedClip() {
        val session = EditorSession()
        val video = MediaItem(name = "v", kind = MediaKind.Video, duration = 10.0, path = "v.mp4", hasVideo = true)
        session.addMedia(video)
        session.placeMedia(video, null, 0.0)
        session.playhead = 4.0
        session.splitAtPlayhead()
        val clips = session.project.tracks.first { it.kind == TrackKind.Video }.clips.sortedBy { it.start }
        assertEquals(2, clips.size)
        assertEquals(4.0, clips[0].duration, 0.01)
        assertEquals(4.0, clips[1].start, 0.01)
        assertEquals(6.0, clips[1].duration, 0.01)
        assertEquals(4.0, clips[1].inPoint, 0.01)
    }

    @Test
    fun undo_restoresThePreviousClipStart() {
        val session = EditorSession()
        val media = MediaItem(name = "a", kind = MediaKind.Video, duration = 4.0, path = "a.mp4", hasVideo = true)
        session.addMedia(media)
        session.placeMedia(media, null, 0.0)
        val clip = session.selectedClip!!
        val track = session.selectedTrack!!
        assertEquals(0.0, clip.start)

        session.moveClip(clip, track, track, 3.0)
        assertEquals(3.0, clip.start)
        assertTrue(session.canUndo)

        session.undo()
        assertEquals(0.0, session.project.tracks.flatMap { it.clips }.first().start)
        assertEquals("已復原上一步。", session.statusText)

        session.redo()
        assertEquals(3.0, session.project.tracks.flatMap { it.clips }.first().start)
        assertEquals("已重做。", session.statusText)
    }

    @Test
    fun undo_withEmptyStack_saysSo() {
        val session = EditorSession()
        session.undo()
        assertEquals("沒有可以復原的動作。", session.statusText)
        assertFalse(session.canUndo)
    }

    @Test
    fun defaultTracks_areThreeLanes() {
        val session = EditorSession()
        assertEquals(3, session.project.tracks.size)
        assertEquals(TrackKind.Video, session.project.tracks[0].kind)
        assertEquals(TrackKind.Subtitle, session.project.tracks[1].kind)
        assertEquals(TrackKind.Audio, session.project.tracks[2].kind)
    }
}

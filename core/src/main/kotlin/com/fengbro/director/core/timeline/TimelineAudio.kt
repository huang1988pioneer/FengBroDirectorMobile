package com.fengbro.director.core.timeline

import com.fengbro.director.core.model.ClipKind
import com.fengbro.director.core.model.EditorProject
import com.fengbro.director.core.model.MediaItem
import com.fengbro.director.core.model.MediaKind
import com.fengbro.director.core.model.TimelineClip
import com.fengbro.director.core.model.TrackKind
import com.fengbro.director.core.time.TimeUtil
import java.io.File

object TimelineAudio {
    data class Audible(val clip: TimelineClip, val media: MediaItem?)

    fun collect(project: EditorProject, atTime: Double? = null): List<Audible> {
        val list = mutableListOf<Audible>()

        for (track in project.tracks.filter { !it.muted && it.kind == TrackKind.Audio }) {
            for (clip in track.clips) {
                if (!isAudible(clip, atTime)) continue
                val media = project.findMedia(clip.mediaId)
                if (media?.kind == MediaKind.Image) continue
                if (media == null &&
                    clip.kind != ClipKind.Audio &&
                    clip.mediaId.isNullOrBlank()
                ) continue
                list.add(Audible(clip, media))
            }
        }

        for (track in project.tracks.filter { !it.muted && it.kind != TrackKind.Audio }) {
            for (clip in track.clips) {
                if (clip.kind != ClipKind.Video && clip.kind != ClipKind.NestedProject) continue
                if (clip.disabled || clip.muted) continue
                if (atTime != null && (atTime < clip.start || atTime >= clip.end)) continue
                val media = project.findMedia(clip.mediaId)
                if (media?.kind == MediaKind.Image) continue
                if (media != null && !media.hasAudio) continue
                if (media == null && clip.mediaId.isNullOrBlank()) continue
                if (hasCompanion(project, clip, media, atTime)) continue
                list.add(Audible(clip, media))
            }
        }

        return dedupe(project, list)
    }

    fun hasCompanion(
        project: EditorProject,
        picture: TimelineClip,
        media: MediaItem?,
        atTime: Double? = null,
    ): Boolean {
        for (track in project.tracks.filter { it.kind == TrackKind.Audio }) {
            for (clip in track.clips) {
                if (clip.disabled) continue
                if (atTime != null) {
                    if (atTime < clip.start || atTime >= clip.end) continue
                } else if (!overlaps(clip, picture)) {
                    continue
                }
                if (sameSource(clip, project.findMedia(clip.mediaId), picture, media)) return true
            }
        }
        return false
    }

    fun sameSource(a: TimelineClip, am: MediaItem?, b: TimelineClip, bm: MediaItem?): Boolean {
        if (!a.mediaId.isNullOrEmpty() && a.mediaId == b.mediaId) return true
        val ap = resolvePath(a, am)
        val bp = resolvePath(b, bm)
        return ap != null && bp != null && ap.equals(bp, ignoreCase = true)
    }

    fun resolvePath(clip: TimelineClip, media: MediaItem?): String? {
        val p = media?.path
        if (!p.isNullOrBlank() && File(p).exists()) return p
        return null
    }

    private fun isAudible(clip: TimelineClip, atTime: Double?): Boolean {
        if (clip.disabled || clip.muted || clip.volume <= 0.001) return false
        if (atTime != null && (atTime < clip.start || atTime >= clip.end)) return false
        return true
    }

    private fun overlaps(a: TimelineClip, b: TimelineClip): Boolean =
        TimeUtil.rangesOverlap(a.start, a.end, b.start, b.end)

    private fun dedupe(project: EditorProject, list: List<Audible>): List<Audible> {
        if (list.size <= 1) return list
        val keep = mutableListOf<Audible>()
        val ordered = list.sortedWith(
            compareBy<Audible> { if (it.clip.kind == ClipKind.Audio) 0 else 1 }
                .thenBy { it.clip.start },
        )
        for (item in ordered) {
            val duplicate = keep.any { k ->
                sameSource(
                    k.clip,
                    k.media ?: project.findMedia(k.clip.mediaId),
                    item.clip,
                    item.media,
                ) && overlaps(k.clip, item.clip)
            }
            if (!duplicate) keep.add(item)
        }
        return keep
    }
}

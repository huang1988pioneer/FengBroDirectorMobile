package com.fengbro.director.core.export

import com.fengbro.director.core.model.ClipKind
import com.fengbro.director.core.model.EditorProject
import com.fengbro.director.core.model.ExportTarget
import com.fengbro.director.core.model.MediaKind
import com.fengbro.director.core.model.TimelineClip
import com.fengbro.director.core.model.TitleStyle
import com.fengbro.director.core.model.TrackKind
import com.fengbro.director.core.timeline.EditorSession
import com.fengbro.director.core.timeline.TimelineAudio
import com.fengbro.director.core.time.TimeUtil
import kotlin.math.max

data class ExportRequest(
    val outputPath: String = "",
    val width: Int = 1920,
    val height: Int = 1080,
    val frameRate: Double = 30.0,
    val target: ExportTarget = ExportTarget.YouTube1080,
)

data class VisualSegment(
    val clip: TimelineClip,
    val path: String?,
    val isImage: Boolean,
    val start: Double,
    val duration: Double,
    val inPoint: Double,
    val speed: Double,
    val flipH: Boolean,
    val flipV: Boolean,
    val rotation: Double,
    val brightness: Double,
    val scale: Double,
    val volume: Double,
    val muted: Boolean,
)

data class AudioSegment(
    val clip: TimelineClip,
    val path: String?,
    val start: Double,
    val duration: Double,
    val inPoint: Double,
    val speed: Double,
    val volume: Double,
)

data class TitleSegment(
    val clip: TimelineClip,
    val text: String,
    val start: Double,
    val end: Double,
    val caption: Boolean,
    val fontSize: Double,
    val color: String,
    val words: List<com.fengbro.director.core.model.LyricWord>?,
)

data class ExportPlan(
    val durationSec: Double,
    val width: Int,
    val height: Int,
    val frameRate: Double,
    val visuals: List<VisualSegment>,
    val audios: List<AudioSegment>,
    val titles: List<TitleSegment>,
    val watermark: Boolean,
    val background: String,
) {
    companion object {
        fun from(project: EditorProject, req: ExportRequest): ExportPlan {
            val duration = max(project.duration, 0.5)
            return ExportPlan(
                durationSec = duration,
                width = req.width,
                height = req.height,
                frameRate = req.frameRate,
                visuals = collectVisuals(project),
                audios = collectAudios(project),
                titles = collectTitles(project, req.height),
                watermark = project.includeWatermark,
                background = project.background,
            )
        }

        fun preset(target: ExportTarget): Pair<Int, Int> = when (target) {
            ExportTarget.YouTube1080, ExportTarget.Custom -> 1920 to 1080
            ExportTarget.YouTube4K -> 3840 to 2160
            ExportTarget.TikTokVertical, ExportTarget.Reels -> 1080 to 1920
            ExportTarget.InstagramSquare -> 1080 to 1080
        }

        private fun collectVisuals(project: EditorProject): List<VisualSegment> {
            val tracks = project.tracks
                .filter { it.kind == TrackKind.Video && !it.hidden }
                .sortedByDescending { it.index }
            val list = mutableListOf<VisualSegment>()
            for (track in tracks) {
                for (clip in track.clips.filter { !it.disabled }.sortedBy { it.start }) {
                    if (clip.kind == ClipKind.Audio) continue
                    val media = project.findMedia(clip.mediaId)
                    val path = media?.path
                    val isImage = media?.kind == MediaKind.Image || clip.kind == ClipKind.Image
                    list.add(
                        VisualSegment(
                            clip = clip,
                            path = path,
                            isImage = isImage,
                            start = clip.start,
                            duration = clip.duration,
                            inPoint = clip.inPoint,
                            speed = clip.speed,
                            flipH = clip.flipH,
                            flipV = clip.flipV,
                            rotation = clip.rotation,
                            brightness = clip.brightness,
                            scale = clip.scale,
                            volume = clip.volume,
                            muted = clip.muted,
                        ),
                    )
                }
            }
            return list
        }

        private fun collectAudios(project: EditorProject): List<AudioSegment> =
            TimelineAudio.collect(project).map { (clip, media) ->
                AudioSegment(
                    clip = clip,
                    path = TimelineAudio.resolvePath(clip, media) ?: media?.path,
                    start = clip.start,
                    duration = clip.duration,
                    inPoint = clip.inPoint,
                    speed = clip.speed,
                    volume = if (clip.muted) 0.0 else clip.volume,
                )
            }

        private fun collectTitles(project: EditorProject, height: Int): List<TitleSegment> {
            val clips = project.tracks.asSequence()
                .flatMap { it.clips.asSequence() }
                .filter { it.kind == ClipKind.Title || it.kind == ClipKind.Subtitle }
                .filter { !it.disabled }
                .toList()
            return EditorSession.distinctVisibleTitles(clips).map { clip ->
                val caption = EditorSession.isBottomCaption(clip)
                TitleSegment(
                    clip = clip,
                    text = clip.text.ifBlank { clip.name },
                    start = clip.start,
                    end = clip.end,
                    caption = caption,
                    fontSize = if (caption) EditorSession.captionFontSize(clip.fontSize, height) else clip.fontSize,
                    color = clip.textColor,
                    words = clip.lyricWords,
                )
            }
        }
    }
}

fun holdSeconds(segment: VisualSegment, mediaDuration: Double): Double =
    TimeUtil.sourceHoldSeconds(segment.duration, segment.inPoint, segment.speed, mediaDuration)

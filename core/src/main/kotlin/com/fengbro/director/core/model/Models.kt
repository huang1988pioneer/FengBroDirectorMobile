package com.fengbro.director.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.UUID

enum class TrackKind { Video, Subtitle, Audio }

enum class ClipKind {
    Video, Image, Audio, Title, Overlay, Effect, Transition, Subtitle, NestedProject,
}

enum class MediaKind { Video, Image, Audio, Project, Subtitle }

enum class TitleStyle {
    CenterTitle, LowerThird, Opening, Ending, CaptionBar, Quote, Chapter, Location,
}

enum class ExportTarget {
    Custom, YouTube1080, YouTube4K, TikTokVertical, InstagramSquare, Reels,
}

fun newId(): String = UUID.randomUUID().toString().replace("-", "")

@Serializable
data class LyricWord(
    var text: String = "",
    var start: Double = 0.0,
    var end: Double = 0.0,
)

@Serializable
data class SubtitleCue(
    var start: Double = 0.0,
    var end: Double = 0.0,
    var text: String = "",
    var words: List<LyricWord>? = null,
)

data class SubtitleParseResult(
    val title: String? = null,
    val artist: String? = null,
    val cues: List<SubtitleCue> = emptyList(),
) {
    companion object {
        val Empty = SubtitleParseResult()
    }
}

@Serializable
data class MediaItem(
    var id: String = newId(),
    var path: String = "",
    var name: String = "",
    var kind: MediaKind = MediaKind.Video,
    var duration: Double = 0.0,
    var width: Int = 0,
    var height: Int = 0,
    var sizeBytes: Long = 0,
    var hasVideo: Boolean = false,
    var hasAudio: Boolean = false,
    var folder: String = "媒體",
    var thumbPath: String? = null,
    var note: String? = null,
    var uri: String? = null,
    var cues: MutableList<SubtitleCue>? = null,
)

@Serializable
data class TimelineClip(
    var id: String = newId(),
    var kind: ClipKind = ClipKind.Video,
    var mediaId: String? = null,
    var name: String = "一段",
    var start: Double = 0.0,
    var duration: Double = 3.0,
    var inPoint: Double = 0.0,
    var speed: Double = 1.0,
    var reverse: Boolean = false,
    var freezeFrame: Boolean = false,
    var volume: Double = 1.0,
    var muted: Boolean = false,
    var opacity: Double = 1.0,
    var posX: Double = 0.0,
    var posY: Double = 0.0,
    var scale: Double = 1.0,
    var rotation: Double = 0.0,
    var flipH: Boolean = false,
    var flipV: Boolean = false,
    var fadeIn: Double = 0.0,
    var fadeOut: Double = 0.0,
    var brightness: Double = 0.0,
    var contrast: Double = 0.0,
    var saturation: Double = 0.0,
    var text: String = "標題文字",
    var fontFamily: String = "sans-serif",
    var fontSize: Double = 72.0,
    var textColor: String = "#FFFFFFFF",
    var titleStyle: TitleStyle = TitleStyle.CenterTitle,
    var locked: Boolean = false,
    var disabled: Boolean = false,
    var lyricWords: MutableList<LyricWord>? = null,
) {
    val end: Double get() = start + duration
    val sourceOut: Double get() = inPoint + maxOf(0.04, duration * maxOf(0.05, speed))
}

@Serializable
data class TimelineTrack(
    var id: String = newId(),
    var kind: TrackKind = TrackKind.Video,
    var index: Int = 1,
    var locked: Boolean = false,
    var hidden: Boolean = false,
    var muted: Boolean = false,
    var solo: Boolean = false,
    var clips: MutableList<TimelineClip> = mutableListOf(),
) {
    val label: String
        get() = when (kind) {
            TrackKind.Video -> if (index <= 1) "畫面" else "畫面 $index"
            TrackKind.Subtitle -> if (index <= 1) "字幕" else "字幕 $index"
            TrackKind.Audio -> if (index <= 1) "聲音" else "聲音 $index"
        }
}

@Serializable
data class TimelineMarker(
    var id: String = newId(),
    var time: Double = 0.0,
    var label: String = "釘",
    var color: String = "#F5C14A",
)

@Serializable
data class BrandKit(
    var name: String = "招牌",
    var primary: String = "#C45C2A",
    var secondary: String = "#8A9A6A",
    var accent: String = "#D4A574",
    var fontFamily: String = "sans-serif",
    var logoPath: String? = null,
)

@Serializable
data class EditorProject(
    var id: String = newId(),
    var name: String = "未命名專案",
    var filePath: String? = null,
    var width: Int = 1920,
    var height: Int = 1080,
    var frameRate: Double = 30.0,
    var sampleRate: Int = 48_000,
    var background: String = "#000000",
    var includeWatermark: Boolean = false,
    var createdAtEpochMs: Long = System.currentTimeMillis(),
    var modifiedAtEpochMs: Long = System.currentTimeMillis(),
    var brand: BrandKit = BrandKit(),
    var media: MutableList<MediaItem> = mutableListOf(),
    var tracks: MutableList<TimelineTrack> = mutableListOf(),
    var markers: MutableList<TimelineMarker> = mutableListOf(),
    @Transient var isDirty: Boolean = false,
) {
    val duration: Double
        get() = tracks.asSequence().flatMap { it.clips.asSequence() }.maxOfOrNull { it.end } ?: 0.0

    fun findMedia(id: String?): MediaItem? =
        if (id == null) null else media.firstOrDefault { it.id == id }

    companion object {
        const val WATERMARK_LINE_1 = "鋒兄"
        const val WATERMARK_LINE_2 = "Papaya Feng"
        const val WATERMARK_LINE_3 = "パパイヤ フェン"
        const val WATERMARK_ONE_LINE = "鋒兄 Papaya Feng パパイヤ フェン"

        fun createDefault(name: String? = null): EditorProject {
            val p = EditorProject(name = name ?: "未命名專案")
            ensureDefaultTracks(p)
            return p
        }

        fun ensureDefaultTracks(p: EditorProject) {
            if (p.tracks.none { it.kind == TrackKind.Video }) {
                p.tracks.add(TimelineTrack(kind = TrackKind.Video, index = 1))
            }
            if (p.tracks.none { it.kind == TrackKind.Subtitle }) {
                p.tracks.add(TimelineTrack(kind = TrackKind.Subtitle, index = 1))
            }
            if (p.tracks.none { it.kind == TrackKind.Audio }) {
                p.tracks.add(TimelineTrack(kind = TrackKind.Audio, index = 1))
            }
        }
    }
}

private fun <T> List<T>.firstOrDefault(predicate: (T) -> Boolean): T? = firstOrNull(predicate)

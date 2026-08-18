package com.fengbro.director.core.timeline

import com.fengbro.director.core.model.ClipKind
import com.fengbro.director.core.model.EditorProject
import com.fengbro.director.core.model.MediaItem
import com.fengbro.director.core.model.MediaKind
import com.fengbro.director.core.model.TimelineClip
import com.fengbro.director.core.model.TimelineTrack
import com.fengbro.director.core.model.TitleStyle
import com.fengbro.director.core.model.TrackKind
import com.fengbro.director.core.model.newId
import com.fengbro.director.core.store.ProjectStore
import com.fengbro.director.core.store.UndoStack
import com.fengbro.director.core.subtitle.SubtitleFile
import com.fengbro.director.core.time.TimeUtil
import java.io.File
import kotlin.math.abs
import kotlin.math.max

class EditorSession(initial: EditorProject = EditorProject.createDefault()) {
    var project: EditorProject = initial
        private set

    private val undo = UndoStack()

    var playhead: Double = 0.0
    var selectedClip: TimelineClip? = null
    var selectedTrack: TimelineTrack? = null
    var snapEnabled: Boolean = true
    var pixelsPerSecond: Double = TimeUtil.DEFAULT_PIXELS_PER_SECOND
    var timelineViewportWidth: Double = 0.0
    var statusText: String = "匯入媒體，再放到時間軸。"
    var includeWatermark: Boolean
        get() = project.includeWatermark
        set(value) {
            if (project.includeWatermark == value) return
            checkpoint()
            project.includeWatermark = value
            project.isDirty = true
        }

    val canUndo: Boolean get() = undo.canUndo
    val canRedo: Boolean get() = undo.canRedo
    val hasClips: Boolean get() = project.tracks.any { it.clips.isNotEmpty() }
    val duration: Double get() = project.duration
    val workspaceSeconds: Double get() = TimeUtil.workspaceSeconds(project.duration, playhead)
    val playheadText: String get() = TimeUtil.formatClock(playhead)
    val durationText: String get() = TimeUtil.formatClock(project.duration)
    val transportClock: String get() = "$playheadText  /  $durationText"

    val durationSliderMax: Double
        get() = TimeUtil.durationSliderCeiling(
            selectedClip?.duration ?: 0.0,
            selectedClip?.let { project.findMedia(it.mediaId)?.duration } ?: 0.0,
            project.media.map { it.duration },
            project.tracks.flatMap { t -> t.clips.map { it.duration } },
        )

    fun firstVideoTrack(): TimelineTrack =
        project.tracks.first { it.kind == TrackKind.Video }

    fun firstSubtitleTrack(): TimelineTrack =
        project.tracks.first { it.kind == TrackKind.Subtitle }

    fun firstAudioTrack(): TimelineTrack =
        project.tracks.first { it.kind == TrackKind.Audio }

    fun matchingAudio(video: TimelineTrack): TimelineTrack {
        val idx = video.index
        return project.tracks.firstOrNull { it.kind == TrackKind.Audio && it.index == idx }
            ?: firstAudioTrack()
    }

    fun addMedia(item: MediaItem) {
        if (project.media.none { it.id == item.id }) {
            project.media.add(item)
            project.isDirty = true
        }
    }

    fun removeLibraryItem(item: MediaItem) {
        checkpoint()
        project.media.removeAll { it.id == item.id }
        for (track in project.tracks) {
            track.clips.removeAll { it.mediaId == item.id }
        }
        if (selectedClip?.mediaId == item.id) selectedClip = null
        project.isDirty = true
        statusText = "已從媒體庫移除 ${item.name}。"
    }

    fun placeMedia(media: MediaItem, track: TimelineTrack?, time: Double) {
        placeMediaCore(media, track, time)
    }

    private fun placeMediaCore(media: MediaItem, track: TimelineTrack?, timeIn: Double) {
        checkpoint()
        var time = timeIn
        if (media.kind == MediaKind.Subtitle) {
            placeSubtitleFile(media, track, time)
            return
        }
        if (media.kind == MediaKind.Audio) {
            val a = if (track?.kind == TrackKind.Audio) track else firstAudioTrack()
            val audioDur = TimeUtil.mediaClipDuration(media.kind, media.duration)
            time = defaultClipStart(a, time, audioDur, ClipKind.Audio)
            val clip = makeMediaClip(media, ClipKind.Audio, time, audioDur)
            a.clips.add(clip)
            selectedClip = clip
            selectedTrack = a
            afterPlacedMedia(media, audioDur)
            return
        }

        val v = if (track?.kind == TrackKind.Video) track else firstVideoTrack()
        val kind = if (media.kind == MediaKind.Image) ClipKind.Image else ClipKind.Video
        val duration = TimeUtil.mediaClipDuration(media.kind, media.duration)
        time = defaultClipStart(v, time, duration, kind)
        val vclip = makeMediaClip(media, kind, time, duration)
        v.clips.add(vclip)

        var aclip: TimelineClip? = null
        if (media.hasAudio && media.kind == MediaKind.Video) {
            val a = matchingAudio(v)
            val audioTime = defaultClipStart(a, time, duration, ClipKind.Audio)
            aclip = makeMediaClip(media, ClipKind.Audio, audioTime, duration)
            a.clips.add(aclip)
        }

        selectedClip = vclip
        selectedTrack = v
        restoreClipDuration(vclip, duration)
        restoreClipDuration(aclip, duration)
        afterPlacedMedia(media, duration)
    }

    private fun restoreClipDuration(clip: TimelineClip?, duration: Double) {
        if (clip == null || duration <= 0) return
        if (abs(clip.duration - duration) < 0.02) return
        clip.duration = duration
    }

    private fun afterPlacedMedia(media: MediaItem, duration: Double) {
        statusText = "已放入 ${media.name}（${TimeUtil.formatClock(duration)}）"
        ensureClipFits(duration)
        project.isDirty = true
    }

    fun ensureClipFits(duration: Double) {
        val width = timelineViewportWidth
        if (width <= 80) return
        val view = max(80.0, width - TimeUtil.TIMELINE_GUTTER)
        if (duration * pixelsPerSecond <= view * 0.95) return
        pixelsPerSecond = TimeUtil.fitPixelsPerSecond(max(duration, project.duration), width)
    }

    fun fitTimeline() {
        val width = if (timelineViewportWidth > 80) timelineViewportWidth else 1400.0
        val span = max(project.duration, 8.0)
        pixelsPerSecond = TimeUtil.fitPixelsPerSecond(span, width)
    }

    private fun defaultClipStart(
        dest: TimelineTrack,
        time: Double,
        duration: Double = 0.0,
        kind: ClipKind? = null,
    ): Double {
        if (kind != null && TimeUtil.occupiesLane(kind) && duration > 0) {
            return resolveLaneStart(dest, time, duration)
        }
        val placed = TimeUtil.placeClipTime(time, dest.clips.map { it.end })
        return snap(placed)
    }

    private fun resolveLaneStart(
        dest: TimelineTrack,
        time: Double,
        duration: Double,
        ignore: TimelineClip? = null,
    ): Double = TimeUtil.placeOnLane(snap(max(0.0, time)), duration, laneBlocks(dest, ignore))

    private fun laneBlocks(track: TimelineTrack, ignore: TimelineClip? = null): List<Pair<Double, Double>> =
        track.clips.filter { it !== ignore && TimeUtil.occupiesLane(it.kind) }
            .map { it.start to it.end }

    fun placeImportedSubtitles(items: Iterable<MediaItem>, time: Double) {
        var placed = false
        for (item in items.filter { it.kind == MediaKind.Subtitle }) {
            placeSubtitleFile(item, null, time)
            placed = true
        }
        if (placed) project.isDirty = true
    }

    private fun placeSubtitleFile(media: MediaItem, track: TimelineTrack?, time: Double) {
        val cues = when {
            File(media.path).exists() && SubtitleFile.isSubtitlePath(media.path) ->
                SubtitleFile.parse(media.path)
            else -> media.cues.orEmpty()
        }
        if (cues.isEmpty()) {
            statusText = if (SubtitleFile.isLrcPath(media.path)) "這份歌詞檔沒有句子。" else "這份字幕檔沒有句子。"
            return
        }
        media.cues = cues.toMutableList()
        media.duration = cues.maxOf { it.end }
        val dest = if (track?.kind == TrackKind.Subtitle) track else firstSubtitleTrack()
        for (lane in project.tracks) {
            lane.clips.removeAll { old ->
                val drop = old.mediaId == media.id
                if (drop && selectedClip === old) selectedClip = null
                drop
            }
        }
        var first: TimelineClip? = null
        var placed = 0
        val lyricFile = SubtitleFile.isLrcPath(media.path)
        for (cue in cues) {
            val text = cue.text.trim()
            if (text.isEmpty()) continue
            val words = cue.words?.map { it.copy() }?.toMutableList()
            val clip = TimelineClip(
                mediaId = media.id,
                kind = ClipKind.Subtitle,
                name = firstLine(text),
                text = text,
                start = time + cue.start,
                duration = max(0.2, cue.end - cue.start),
                fontSize = if (lyricFile) 56.0 else 72.0,
                titleStyle = TitleStyle.CaptionBar,
                lyricWords = words,
            )
            dest.clips.add(clip)
            if (first == null) first = clip
            placed++
        }
        if (first == null) {
            statusText = if (lyricFile) "這份歌詞檔沒有句子。" else "這份字幕檔沒有句子。"
            return
        }
        selectedClip = first
        selectedTrack = dest
        val kind = if (lyricFile) "歌詞" else "字幕"
        statusText = if (placed == 1) "已放上 1 句$kind。" else "已放上 $placed 句$kind。"
        ensureClipFits(media.duration)
        project.isDirty = true
    }

    fun addSubtitleAtPlayhead(text: String = "字幕") {
        checkpoint()
        val dest = firstSubtitleTrack()
        val clip = TimelineClip(
            kind = ClipKind.Subtitle,
            name = firstLine(text),
            text = text,
            start = playhead,
            duration = 3.0,
            fontSize = 72.0,
            titleStyle = TitleStyle.CaptionBar,
        )
        dest.clips.add(clip)
        selectedClip = clip
        selectedTrack = dest
        statusText = "已加上字幕。"
        project.isDirty = true
    }

    fun moveClip(clip: TimelineClip, from: TimelineTrack, to: TimelineTrack, newStartIn: Double) {
        checkpoint()
        var newStart = snap(max(0.0, newStartIn))
        if (TimeUtil.occupiesLane(clip.kind)) {
            newStart = resolveLaneStart(to, newStart, clip.duration, clip)
        }
        if (from !== to) {
            from.clips.remove(clip)
            to.clips.add(clip)
            selectedTrack = to
        }
        clip.start = newStart
        selectedClip = clip
        project.isDirty = true
    }

    fun trimClip(
        clip: TimelineClip,
        newStartIn: Double,
        newDurationIn: Double,
        left: Boolean,
        recordUndo: Boolean = true,
    ) {
        if (recordUndo) checkpoint()
        var newStart = snap(max(0.0, newStartIn))
        var newDuration = max(0.05, newDurationIn)
        val track = project.tracks.firstOrNull { it.clips.contains(clip) }
        if (track != null && TimeUtil.occupiesLane(clip.kind)) {
            val clamped = TimeUtil.clampLaneTrim(
                newStart, newDuration, left, clip.start, clip.end, laneBlocks(track, clip),
            )
            newStart = clamped.first
            newDuration = clamped.second
        }
        val linked = findLinkedClips(clip)
        applyTrim(clip, newStart, newDuration, left)
        applyHoldIfNeeded(clip)
        for (other in linked) {
            applyTrim(other, newStart, newDuration, left)
            applyHoldIfNeeded(other)
        }
        project.isDirty = true
    }

    private fun applyTrim(clip: TimelineClip, newStart: Double, newDuration: Double, left: Boolean) {
        if (left) {
            val delta = newStart - clip.start
            clip.inPoint = max(0.0, clip.inPoint + delta * clip.speed)
            clip.start = newStart
            clip.duration = newDuration
        } else {
            clip.duration = newDuration
        }
    }

    private fun applyHoldIfNeeded(clip: TimelineClip) {
        val media = project.findMedia(clip.mediaId) ?: return
        if (media.kind == MediaKind.Image || media.kind == MediaKind.Subtitle || media.kind == MediaKind.Project) return
        val hold = TimeUtil.sourceHoldSeconds(clip.duration, clip.inPoint, clip.speed, media.duration)
        clip.freezeFrame = hold > 0.04
    }

    private fun findLinkedClips(clip: TimelineClip): List<TimelineClip> {
        val id = clip.mediaId ?: return emptyList()
        return project.tracks.flatMap { it.clips }.filter { other ->
            other !== clip &&
                other.mediaId == id &&
                abs(other.start - clip.start) <= 0.2 &&
                abs(other.duration - clip.duration) <= 0.2
        }
    }

    fun splitAtPlayhead() {
        val clip = selectedClip ?: return
        val track = selectedTrack ?: return
        if (playhead <= clip.start + 0.05 || playhead >= clip.end - 0.05) return
        checkpoint()
        val leftDur = playhead - clip.start
        val right = cloneClip(clip)
        right.id = newId()
        right.start = playhead
        right.duration = clip.end - playhead
        right.inPoint = clip.inPoint + leftDur * clip.speed
        clip.duration = leftDur
        track.clips.add(right)
        statusText = "已從播放頭切開。"
        project.isDirty = true
    }

    fun deleteSelected() {
        val clip = selectedClip ?: return
        checkpoint()
        removeClips(listOf(clip) + findLinkedClips(clip))
        selectedClip = null
        statusText = "已刪除片段。"
        project.isDirty = true
    }

    fun rippleDelete() {
        val clip = selectedClip ?: return
        val track = selectedTrack ?: return
        checkpoint()
        val start = clip.start
        val dur = clip.duration
        track.clips.remove(clip)
        for (c in track.clips.filter { it.start >= start }) {
            c.start = max(0.0, c.start - dur)
        }
        selectedClip = null
        statusText = "已刪除並往前靠攏。"
        project.isDirty = true
    }

    fun duplicateSelected() {
        val clip = selectedClip ?: return
        val track = selectedTrack ?: return
        checkpoint()
        val copy = cloneClip(clip)
        copy.id = newId()
        copy.start = if (TimeUtil.occupiesLane(copy.kind)) {
            resolveLaneStart(track, clip.end, copy.duration)
        } else {
            clip.end
        }
        track.clips.add(copy)
        selectedClip = copy
        statusText = "已複製一段。"
        project.isDirty = true
    }

    private fun removeClips(clips: Collection<TimelineClip>) {
        val doomed = clips.toSet()
        for (track in project.tracks) {
            track.clips.removeAll { it in doomed }
        }
    }

    fun selectClip(clip: TimelineClip?, track: TimelineTrack?) {
        selectedClip = clip
        selectedTrack = track
    }

    fun clipAtPlayhead(): Pair<TimelineClip?, MediaItem?> {
        for (track in project.tracks.filter { it.kind == TrackKind.Video && !it.hidden }.sortedBy { it.index }) {
            val clip = track.clips.lastOrNull { c ->
                !c.disabled && playhead >= c.start && playhead < c.end &&
                    (c.kind == ClipKind.Video || c.kind == ClipKind.Image || c.kind == ClipKind.NestedProject)
            }
            if (clip != null) return clip to project.findMedia(clip.mediaId)
        }
        return null to null
    }

    fun overlayAtPlayhead(): TimelineClip? {
        val clips = project.tracks.asSequence()
            .flatMap { it.clips.asSequence() }
            .filter { c ->
                !c.disabled &&
                    (c.kind == ClipKind.Title || c.kind == ClipKind.Subtitle) &&
                    playhead >= c.start && playhead < c.end
            }
            .sortedBy { it.start }
            .toList()
        return distinctVisibleTitles(clips).lastOrNull { playhead >= it.start && playhead < it.end }
            ?: clips.lastOrNull()
    }

    fun karaokeSpans(clip: TimelineClip, time: Double): List<Pair<String, Boolean>> {
        val words = clip.lyricWords
        if (words.isNullOrEmpty()) return emptyList()
        val local = time - clip.start
        return words.map { it.text to (local >= it.start) }
    }

    fun seek(time: Double) {
        playhead = max(0.0, time)
    }

    fun snap(time: Double): Double = if (snapEnabled) TimeUtil.snap(time, 0.05) else time

    fun applyAspect(width: Int, height: Int) {
        if (project.width == width && project.height == height) return
        checkpoint()
        project.width = width
        project.height = height
        project.isDirty = true
        statusText = "畫面改為 ${width}×${height}。"
    }

    fun undo() {
        val restored = undo.undo(project)
        if (restored == null) {
            statusText = "沒有可以復原的動作。"
            return
        }
        replaceProject(restored)
        statusText = "已復原上一步。"
    }

    fun redo() {
        val restored = undo.redo(project)
        if (restored == null) {
            statusText = "沒有可以重做的動作。"
            return
        }
        replaceProject(restored)
        statusText = "已重做。"
    }

    fun newProject(name: String = "未命名專案") {
        undo.clear()
        project = EditorProject.createDefault(name)
        selectedClip = null
        selectedTrack = null
        playhead = 0.0
        statusText = "新專案已開好。"
    }

    fun loadProject(path: String) {
        undo.clear()
        replaceProject(ProjectStore.load(path))
        project.isDirty = false
        statusText = "已開啟 ${project.name}。"
    }

    fun saveProject(path: String) {
        ProjectStore.save(project, path)
        statusText = "已儲存。"
    }

    fun replaceProject(next: EditorProject) {
        project = next
        EditorProject.ensureDefaultTracks(project)
        selectedClip = null
        selectedTrack = null
        playhead = playhead.coerceAtLeast(0.0)
    }

    fun checkpoint() {
        undo.push(project)
    }

    private fun makeMediaClip(media: MediaItem, kind: ClipKind, time: Double, duration: Double) =
        TimelineClip(
            mediaId = media.id,
            name = media.name,
            kind = kind,
            start = time,
            duration = duration,
            inPoint = 0.0,
            volume = 1.0,
        )

    private fun cloneClip(src: TimelineClip): TimelineClip =
        src.copy(
            lyricWords = src.lyricWords?.map { it.copy() }?.toMutableList(),
        )

    private fun firstLine(text: String): String =
        text.lineSequence().firstOrNull { it.isNotBlank() }?.take(24) ?: "字幕"

    companion object {
        fun isBottomCaption(clip: TimelineClip): Boolean =
            clip.kind == ClipKind.Subtitle || clip.titleStyle == TitleStyle.CaptionBar

        fun captionFontSize(requested: Double, height: Int): Double =
            max(requested, max(52.0, height / 16.0))

        fun distinctVisibleTitles(clips: List<TimelineClip>): List<TimelineClip> {
            val kept = mutableListOf<TimelineClip>()
            for (clip in clips.sortedBy { it.start }) {
                val text = normalizeTitle(clip)
                if (text.isNotEmpty() && kept.any { normalizeTitle(it) == text && overlaps(it, clip) }) {
                    continue
                }
                kept.add(clip)
            }
            return kept
        }

        private fun normalizeTitle(clip: TimelineClip): String =
            (if (clip.text.isBlank()) clip.name else clip.text).replace('\n', ' ').trim()

        private fun overlaps(a: TimelineClip, b: TimelineClip): Boolean =
            a.start < b.end && b.start < a.end
    }
}

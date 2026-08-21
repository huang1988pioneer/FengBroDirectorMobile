package com.fengbro.director.ui

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import com.fengbro.director.core.export.ExportPlan
import com.fengbro.director.core.export.ExportRequest
import com.fengbro.director.core.model.ExportTarget
import com.fengbro.director.core.model.MediaKind
import com.fengbro.director.core.model.TimelineClip
import com.fengbro.director.core.model.TimelineTrack
import com.fengbro.director.core.model.TrackKind
import com.fengbro.director.core.store.ProjectStore
import com.fengbro.director.core.store.RecentProject
import com.fengbro.director.core.store.RecentStore
import com.fengbro.director.core.time.TimeUtil
import com.fengbro.director.core.timeline.EditorSession
import com.fengbro.director.media.MediaImporter
import com.fengbro.director.media.CompositionPreview
import com.fengbro.director.media.MediaCompositionFactory
import com.fengbro.director.media.TransformerExporter
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

enum class EditorSheet { Library, Inspector, Export, None }

enum class WorkspaceMode { Startup, Editor }

enum class LibraryFilter { All, Video, Audio, Subtitle }

data class EditorUiState(
    val projectName: String = "未命名專案",
    val playhead: Double = 0.0,
    val duration: Double = 0.0,
    val workspaceSeconds: Double = TimeUtil.MIN_WORKSPACE_SECONDS,
    val playing: Boolean = false,
    val status: String = "匯入媒體，再放到時間軸。",
    val tracks: List<TimelineTrack> = emptyList(),
    val media: List<com.fengbro.director.core.model.MediaItem> = emptyList(),
    val selectedClipId: String? = null,
    val selectedTrackId: String? = null,
    val selectedClip: TimelineClip? = null,
    val watermark: Boolean = false,
    val width: Int = 1920,
    val height: Int = 1080,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val hasClips: Boolean = false,
    val pixelsPerSecond: Double = TimeUtil.DEFAULT_PIXELS_PER_SECOND,
    val sheet: EditorSheet = EditorSheet.None,
    val exportProgress: Float? = null,
    val exportMessage: String? = null,
    val lastExportAvailable: Boolean = false,
    val recents: List<RecentProject> = emptyList(),
    val clock: String = "00:00.00  /  00:00.00",
    val generation: Int = 0,
    val mode: WorkspaceMode = WorkspaceMode.Startup,
    val libraryFilter: LibraryFilter = LibraryFilter.All,
    val visibleMedia: List<com.fengbro.director.core.model.MediaItem> = emptyList(),
)

@OptIn(markerClass = [UnstableApi::class, ExperimentalApi::class])
class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private val session = EditorSession()
    private val importer = MediaImporter(app)
    private val compositions = MediaCompositionFactory(app)
    private val preview = CompositionPreview(app, compositions)
    private val exporter = TransformerExporter(app, compositions)
    private val recentFile = File(app.filesDir, "recents.json")
    private val autosaveFile = File(app.filesDir, "autosave.fbdproj")

    val player: Player = preview.player

    private val _ui = MutableStateFlow(EditorUiState())
    val ui: StateFlow<EditorUiState> = _ui

    private var playJob: Job? = null
    private var compositionDirty = true
    private var generation = 0
    private var mode = WorkspaceMode.Startup
    private var libraryFilter = LibraryFilter.All

    init {
        if (autosaveFile.exists()) {
            runCatching {
                session.replaceProject(ProjectStore.load(autosaveFile.absolutePath))
                if (session.hasClips || session.project.media.isNotEmpty()) {
                    mode = WorkspaceMode.Editor
                    session.statusText = "已還原上次未關的專案。"
                }
            }
        }
        publish()
        refreshRecents()
        if (session.hasClips) syncPreview(rebuild = true)
        refreshMissingThumbnails()
    }

    fun importUris(uris: List<Uri>) {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) { importer.importUris(uris) }
            if (items.isEmpty()) {
                session.statusText = "沒有匯入任何檔案。"
                publish()
                return@launch
            }
            items.forEach { session.addMedia(it) }
            session.statusText = "已匯入 ${items.size} 項，點一下放到時間軸。"
            if (mode == WorkspaceMode.Startup) mode = WorkspaceMode.Editor
            publish()
            autosave()
        }
    }

    fun placeMedia(id: String, time: Double = session.playhead) {
        val item = session.project.media.firstOrNull { it.id == id } ?: return
        session.placeMedia(item, null, time)
        publish()
        compositionChanged()
        autosave()
    }

    fun addSubtitle() {
        session.addSubtitleAtPlayhead()
        publish()
        compositionChanged()
        autosave()
    }

    fun selectClip(clipId: String?, trackId: String?) {
        val track = session.project.tracks.firstOrNull { it.id == trackId }
        val clip = track?.clips?.firstOrNull { it.id == clipId }
        session.selectClip(clip, track)
        publish()
        syncPreview(rebuild = false)
    }

    fun seek(time: Double, fromUser: Boolean = true) {
        session.seek(time)
        if (fromUser && session.playhead > session.duration && session.duration > 0) {
            pause()
        }
        publish()
        if (fromUser) syncPreview(rebuild = false)
    }

    fun togglePlay() {
        if (_ui.value.playing) pause() else play()
    }

    fun play() {
        if (!session.hasClips) {
            session.statusText = "先放一段到時間軸。"
            publish()
            return
        }
        if (session.playhead >= session.duration - 0.05) session.seek(0.0)
        _ui.update { it.copy(playing = true) }
        syncPreview(rebuild = compositionDirty)
        preview.play()
        playJob?.cancel()
        playJob = viewModelScope.launch {
            while (isActive && _ui.value.playing) {
                delay(33)
                val next = preview.currentPositionSeconds
                if (next >= max(session.duration, 0.05)) {
                    session.seek(session.duration)
                    pause()
                    break
                }
                session.seek(next)
                publish()
            }
        }
    }

    fun pause() {
        playJob?.cancel()
        playJob = null
        preview.pause()
        _ui.update { it.copy(playing = false) }
        publish()
    }

    fun split() {
        session.splitAtPlayhead()
        publish()
        compositionChanged()
        autosave()
    }

    fun deleteSelected() {
        session.deleteSelected()
        publish()
        compositionChanged()
        autosave()
    }

    fun duplicate() {
        session.duplicateSelected()
        publish()
        compositionChanged()
        autosave()
    }

    fun undo() {
        session.undo()
        publish()
        compositionChanged()
        autosave()
    }

    fun redo() {
        session.redo()
        publish()
        compositionChanged()
        autosave()
    }

    fun nudgeSelected(delta: Double) {
        val clip = session.selectedClip ?: return
        val track = session.selectedTrack ?: return
        session.moveClip(clip, track, track, (clip.start + delta).coerceAtLeast(0.0))
        publish()
        compositionChanged()
        autosave()
    }

    fun trimClip(clipId: String, newStart: Double, newDuration: Double, left: Boolean) {
        val clip = session.project.tracks.asSequence().flatMap { it.clips.asSequence() }
            .firstOrNull { it.id == clipId } ?: return
        session.trimClip(clip, newStart, newDuration, left)
        publish()
        compositionChanged()
        autosave()
    }

    fun setWatermark(on: Boolean) {
        session.includeWatermark = on
        publish()
        compositionChanged()
        autosave()
    }

    fun setAspect(target: ExportTarget) {
        val (w, h) = ExportPlan.preset(target)
        session.applyAspect(w, h)
        publish()
        compositionChanged()
        autosave()
    }

    fun updateSelectedText(text: String) {
        val clip = session.selectedClip ?: return
        session.checkpoint()
        clip.text = text
        clip.name = text.lineSequence().firstOrNull { it.isNotBlank() }?.take(24) ?: clip.name
        session.project.isDirty = true
        publish()
        compositionChanged()
        autosave()
    }

    fun updateSelectedDuration(duration: Double) {
        val clip = session.selectedClip ?: return
        session.trimClip(clip, clip.start, duration, left = false)
        publish()
        compositionChanged()
        autosave()
    }

    fun setPixelsPerSecond(value: Double) {
        session.pixelsPerSecond = value.coerceIn(TimeUtil.MIN_PIXELS_PER_SECOND, TimeUtil.MAX_PIXELS_PER_SECOND)
        publish()
    }

    fun setViewportWidth(width: Double) {
        session.timelineViewportWidth = width
    }

    fun openSheet(sheet: EditorSheet) {
        _ui.update { it.copy(sheet = sheet) }
    }

    fun closeSheet() {
        _ui.update { it.copy(sheet = EditorSheet.None, exportMessage = null) }
    }

    fun newProject() {
        pause()
        session.newProject()
        compositionDirty = true
        preview.stop()
        mode = WorkspaceMode.Editor
        publish()
        autosave()
    }

    fun startNewProject(name: String, target: ExportTarget) {
        pause()
        session.newProject(name.ifBlank { "未命名專案" })
        val (w, h) = ExportPlan.preset(target)
        session.applyAspect(w, h)
        compositionDirty = true
        preview.stop()
        mode = WorkspaceMode.Editor
        session.statusText = "新專案已開好。匯入媒體，再放到時間軸。"
        publish()
        autosave()
    }

    fun backToStartup() {
        pause()
        mode = WorkspaceMode.Startup
        _ui.update { it.copy(sheet = EditorSheet.None) }
        publish()
    }

    fun setLibraryFilter(filter: LibraryFilter) {
        libraryFilter = filter
        publish()
    }

    fun enterEditor() {
        mode = WorkspaceMode.Editor
        publish()
    }

    fun saveNamed(name: String) {
        session.project.name = name.ifBlank { session.project.name }
        val file = File(getApplication<Application>().filesDir, "projects").apply { mkdirs() }
        val dest = File(file, "${sanitize(session.project.name)}.fbdproj")
        session.saveProject(dest.absolutePath)
        RecentStore.touch(recentFile, dest.absolutePath, session.project.name)
        refreshRecents()
        publish()
    }

    fun openRecent(path: String) {
        pause()
        runCatching {
            session.loadProject(path)
            RecentStore.touch(recentFile, path, session.project.name)
            refreshRecents()
            compositionDirty = true
            preview.stop()
            mode = WorkspaceMode.Editor
            publish()
            syncPreview(rebuild = true)
        }.onFailure {
            session.statusText = "這本打不開。"
            publish()
        }
    }

    fun export(target: ExportTarget) {
        val (w, h) = ExportPlan.preset(target)
        session.applyAspect(w, h)
        compositionDirty = true
        publish()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val name = "鋒兄導演-$stamp.mp4"
        val req = ExportRequest(width = w, height = h, frameRate = session.project.frameRate, target = target)
        val plan = ExportPlan.from(session.project, req)
        _ui.update {
            it.copy(
                exportProgress = 0f,
                exportMessage = "正在匯出…",
                lastExportAvailable = false,
                sheet = EditorSheet.Export,
            )
        }
        pause()
        viewModelScope.launch {
            try {
                val result = exporter.export(plan, name) { p ->
                    _ui.update { it.copy(exportProgress = p) }
                }
                val where = result.galleryUri?.let { "已存到相簿「電影 / FengBroDirector」" }
                    ?: "已存到 ${result.file.absolutePath}"
                session.statusText = "匯出完成。"
                _ui.update {
                    it.copy(
                        exportProgress = 1f,
                        exportMessage = where,
                        lastExportAvailable = true,
                        status = session.statusText,
                    )
                }
            } catch (t: Throwable) {
                session.statusText = "匯出失敗：${t.message ?: t.javaClass.simpleName}"
                _ui.update {
                    it.copy(
                        exportProgress = null,
                        exportMessage = session.statusText,
                        lastExportAvailable = false,
                        status = session.statusText,
                    )
                }
            }
        }
    }

    fun cancelExportMessage() {
        _ui.update { it.copy(exportProgress = null, exportMessage = null) }
    }

    fun removeLibrary(id: String) {
        val item = session.project.media.firstOrNull { it.id == id } ?: return
        item.thumbPath?.takeIf { it != item.path }?.let { runCatching { File(it).delete() } }
        session.removeLibraryItem(item)
        publish()
        compositionChanged()
        autosave()
    }

    private fun compositionChanged() {
        compositionDirty = true
        syncPreview(rebuild = true)
    }

    private fun syncPreview(rebuild: Boolean) {
        if (!session.hasClips) {
            preview.stop()
            return
        }
        if (rebuild || compositionDirty) {
            val request = ExportRequest(
                width = session.project.width,
                height = session.project.height,
                frameRate = session.project.frameRate,
                target = ExportTarget.Custom,
            )
            preview.load(
                ExportPlan.from(session.project, request),
                positionSeconds = session.playhead,
                playWhenReady = _ui.value.playing,
            )
            compositionDirty = false
        } else {
            preview.seek(session.playhead)
        }
    }

    private fun publish() {
        generation += 1
        val selected = session.selectedClip
        _ui.update {
            it.copy(
                projectName = session.project.name,
                playhead = session.playhead,
                duration = session.duration,
                workspaceSeconds = session.workspaceSeconds,
                status = session.statusText,
                tracks = session.project.tracks.toList(),
                media = session.project.media.toList(),
                selectedClipId = selected?.id,
                selectedTrackId = session.selectedTrack?.id,
                selectedClip = selected,
                watermark = session.includeWatermark,
                width = session.project.width,
                height = session.project.height,
                canUndo = session.canUndo,
                canRedo = session.canRedo,
                hasClips = session.hasClips,
                pixelsPerSecond = session.pixelsPerSecond,
                clock = session.transportClock,
                generation = generation,
                mode = mode,
                libraryFilter = libraryFilter,
                visibleMedia = session.project.media.filter { item ->
                    when (libraryFilter) {
                        LibraryFilter.All -> true
                        LibraryFilter.Video -> item.kind == MediaKind.Video || item.kind == MediaKind.Image
                        LibraryFilter.Audio -> item.kind == MediaKind.Audio
                        LibraryFilter.Subtitle -> item.kind == MediaKind.Subtitle
                    }
                },
            )
        }
    }

    private fun autosave() {
        runCatching { ProjectStore.save(session.project, autosaveFile.absolutePath) }
    }

    private fun refreshMissingThumbnails() {
        val missing = session.project.media.filter {
            (it.kind == MediaKind.Video || it.kind == MediaKind.Image) &&
                (it.thumbPath.isNullOrBlank() || it.thumbPath?.let(::File)?.isFile != true)
        }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val changed = withContext(Dispatchers.IO) {
                missing.count { importer.ensureThumbnail(it) }
            }
            if (changed > 0) {
                publish()
                autosave()
            }
        }
    }

    private fun refreshRecents() {
        _ui.update { it.copy(recents = RecentStore.load(recentFile)) }
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "project" }

    override fun onCleared() {
        playJob?.cancel()
        preview.release()
        super.onCleared()
    }
}

package com.fengbro.director.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import com.fengbro.director.core.layout.EditorWindowSpec
import com.fengbro.director.core.model.ExportTarget
import com.fengbro.director.media.MediaImporter
import com.fengbro.director.ui.components.AspectRow
import com.fengbro.director.ui.components.InspectorPanel
import com.fengbro.director.ui.components.LibraryPanel
import com.fengbro.director.ui.components.TimelineView
import com.fengbro.director.ui.theme.BgApp
import com.fengbro.director.ui.theme.BgChrome
import com.fengbro.director.ui.theme.BgPanel
import com.fengbro.director.ui.theme.BgStage
import com.fengbro.director.ui.theme.Ink
import com.fengbro.director.ui.theme.Line
import com.fengbro.director.ui.theme.Muted
import com.fengbro.director.ui.theme.Primary
import com.fengbro.director.ui.theme.PrimaryInk

@Composable
fun EditorScreen(vm: EditorViewModel) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val spec = rememberEditorWindowSpec()
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) vm.importUris(uris)
    }
    val onImport = { picker.launch(MediaImporter.OPEN_MIME) }

    BackHandler(enabled = state.sheet != EditorSheet.None || state.mode == WorkspaceMode.Editor) {
        when {
            state.sheet != EditorSheet.None -> vm.closeSheet()
            state.mode == WorkspaceMode.Editor -> vm.backToStartup()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgApp)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        if (state.mode == WorkspaceMode.Startup) {
            TopBar(state, vm, editor = false)
            StartupScreen(
                state = state,
                spec = spec,
                onStart = vm::startNewProject,
                onImport = onImport,
                onOpenRecent = vm::openRecent,
            )
        } else {
            TopBar(state, vm, editor = true)
            when {
                spec.useBench -> TabletBench(state, vm, spec, onImport)
                spec.useLandscapeSplit -> PhoneLandscape(state, vm, spec, onImport)
                else -> PhonePortrait(state, vm, spec, onImport)
            }
            StatusBar(state)
        }
    }

    EditorSheets(state, vm, spec, onImport)
}

@Composable
private fun ColumnScope.TabletBench(
    state: EditorUiState,
    vm: EditorViewModel,
    spec: EditorWindowSpec,
    onImport: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
    ) {
        LibraryPanel(
            state = state,
            vm = vm,
            onImport = onImport,
            compact = false,
            modifier = Modifier
                .width(spec.libraryWidthDp.dp)
                .fillMaxHeight(),
        )
        Box(Modifier.width(1.dp).fillMaxHeight().background(Line))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            PreviewPane(vm, state, Modifier.weight(1f))
            TransportBar(state, vm)
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Line))
        InspectorPanel(
            state = state,
            vm = vm,
            modifier = Modifier
                .width(spec.inspectorWidthDp.dp)
                .fillMaxHeight(),
        )
    }
    ToolBar(state, onImport, vm, showLibrary = false, showInspector = false)
    TimelineBlock(state, vm, spec.timelineHeightDp)
}

@Composable
private fun ColumnScope.PhonePortrait(
    state: EditorUiState,
    vm: EditorViewModel,
    spec: EditorWindowSpec,
    onImport: () -> Unit,
) {
    PreviewPane(vm, state, Modifier.weight(1f))
    TransportBar(state, vm)
    ToolBar(state, onImport, vm, showLibrary = true, showInspector = true)
    TimelineBlock(state, vm, spec.timelineHeightDp)
}

@Composable
private fun ColumnScope.PhoneLandscape(
    state: EditorUiState,
    vm: EditorViewModel,
    spec: EditorWindowSpec,
    onImport: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
    ) {
        PreviewPane(vm, state, Modifier.weight(1.15f).fillMaxHeight())
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            TransportBar(state, vm)
            ToolBar(state, onImport, vm, showLibrary = true, showInspector = true)
            TimelineView(
                tracks = state.tracks,
                playhead = state.playhead,
                workspaceSeconds = state.workspaceSeconds,
                pixelsPerSecond = state.pixelsPerSecond,
                selectedClipId = state.selectedClipId,
                onViewportWidth = vm::setViewportWidth,
                onSeek = { vm.seek(it) },
                onSelect = vm::selectClip,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TimelineBlock(
    state: EditorUiState,
    vm: EditorViewModel,
    heightDp: Int,
) {
    TimelineView(
        tracks = state.tracks,
        playhead = state.playhead,
        workspaceSeconds = state.workspaceSeconds,
        pixelsPerSecond = state.pixelsPerSecond,
        selectedClipId = state.selectedClipId,
        onViewportWidth = vm::setViewportWidth,
        onSeek = { vm.seek(it) },
        onSelect = vm::selectClip,
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp),
    )
}

@Composable
private fun TopBar(state: EditorUiState, vm: EditorViewModel, editor: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgChrome)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("鋒兄導演", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (editor) {
            Spacer(Modifier.width(10.dp))
            Text(state.projectName, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = vm::backToStartup) {
                Icon(Icons.Default.Home, "開始畫面", tint = Muted)
            }
            IconButton(onClick = vm::undo, enabled = state.canUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, "復原", tint = if (state.canUndo) Ink else Muted)
            }
            IconButton(onClick = vm::redo, enabled = state.canRedo) {
                Icon(Icons.AutoMirrored.Filled.Redo, "重做", tint = if (state.canRedo) Ink else Muted)
            }
            Button(
                onClick = { vm.openSheet(EditorSheet.Export) },
                colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = PrimaryInk),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("匯出", fontWeight = FontWeight.SemiBold)
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun PreviewPane(vm: EditorViewModel, state: EditorUiState, modifier: Modifier) {
    val ratio = state.width.toFloat() / state.height.coerceAtLeast(1)
    Box(
        modifier = modifier.background(BgStage),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .padding(12.dp)
                .aspectRatio(ratio.coerceIn(0.4f, 2.4f))
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(androidx.compose.ui.graphics.Color.Black)
                .clickable { vm.togglePlay() },
            contentAlignment = Alignment.Center,
        ) {
            if (state.previewIsImage && state.previewImagePath != null) {
                val bmp = rememberPreviewBitmap(state.previewImagePath)
                if (bmp != null) {
                    Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = vm.player
                            useController = false
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        }
                    },
                    update = { it.player = vm.player },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (state.overlay.text.isNotBlank()) OverlayCaption(state.overlay)
            if (!state.hasClips) {
                Text("匯入媒體，再放到時間軸", color = Muted, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun rememberPreviewBitmap(path: String) = androidx.compose.runtime.remember(path) {
    android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap()
}

@Composable
private fun OverlayCaption(overlay: OverlayState) {
    Box(Modifier.fillMaxSize(), contentAlignment = if (overlay.caption) Alignment.BottomCenter else Alignment.Center) {
        val shape = RoundedCornerShape(8.dp)
        if (overlay.karaoke.isNotEmpty()) {
            Row(
                Modifier
                    .padding(bottom = if (overlay.caption) 28.dp else 0.dp)
                    .clip(shape)
                    .background(androidx.compose.ui.graphics.Color(0x99000000))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                overlay.karaoke.forEach { (word, on) ->
                    Text(word, color = if (on) Primary else Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            Text(
                overlay.text,
                color = Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(bottom = if (overlay.caption) 28.dp else 0.dp)
                    .clip(shape)
                    .background(androidx.compose.ui.graphics.Color(0x99000000))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun TransportBar(state: EditorUiState, vm: EditorViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(BgChrome)
            .padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.seek((state.playhead - 1).coerceAtLeast(0.0)) }) {
                Icon(Icons.Default.SkipPrevious, "倒退", tint = Ink)
            }
            IconButton(onClick = vm::togglePlay) {
                Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow, "播放", tint = Ink)
            }
            IconButton(onClick = { vm.seek(state.playhead + 1) }) {
                Icon(Icons.Default.SkipNext, "快轉", tint = Ink)
            }
            Text(state.clock, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("${state.width}×${state.height}", color = Muted, fontSize = 11.sp)
        }
        Slider(
            value = state.playhead.toFloat(),
            onValueChange = { vm.seek(it.toDouble()) },
            valueRange = 0f..state.workspaceSeconds.toFloat().coerceAtLeast(1f),
            colors = SliderDefaults.colors(thumbColor = Primary, activeTrackColor = Primary, inactiveTrackColor = Line),
        )
    }
}

@Composable
private fun ToolBar(
    state: EditorUiState,
    onImport: () -> Unit,
    vm: EditorViewModel,
    showLibrary: Boolean,
    showInspector: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgPanel)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        TinyAction("匯入", Icons.Default.FolderOpen, onClick = onImport)
        if (showLibrary) TinyAction("媒體庫", Icons.Default.Add) { vm.openSheet(EditorSheet.Library) }
        TinyAction("分割", Icons.Default.ContentCut, enabled = state.selectedClipId != null, onClick = vm::split)
        TinyAction("刪除", Icons.Default.Delete, enabled = state.selectedClipId != null, onClick = vm::deleteSelected)
        TinyAction("字幕", Icons.Default.Add, onClick = vm::addSubtitle)
        if (showInspector) TinyAction("詳細", Icons.Default.MoreHoriz) { vm.openSheet(EditorSheet.Inspector) }
        TinyAction("縮小", Icons.Default.ZoomOut) { vm.setPixelsPerSecond(state.pixelsPerSecond * 0.75) }
        TinyAction("放大", Icons.Default.ZoomIn) { vm.setPixelsPerSecond(state.pixelsPerSecond * 1.25) }
    }
}

@Composable
private fun TinyAction(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Icon(icon, label, tint = if (enabled) Ink else Muted, modifier = Modifier.size(18.dp))
        Text(label, color = if (enabled) Muted else Muted.copy(alpha = 0.5f), fontSize = 10.sp)
    }
}

@Composable
private fun StatusBar(state: EditorUiState) {
    Text(
        state.status,
        color = Muted,
        fontSize = 11.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(BgChrome)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        maxLines = 1,
    )
}

@Composable
private fun EditorSheets(
    state: EditorUiState,
    vm: EditorViewModel,
    spec: EditorWindowSpec,
    onImport: () -> Unit,
) {
    val showLibrary = state.sheet == EditorSheet.Library && spec.useLibrarySheet
    val showInspector = state.sheet == EditorSheet.Inspector && spec.useInspectorSheet
    val showExport = state.sheet == EditorSheet.Export
    if (showLibrary) {
        ModalBottomSheet(
            onDismissRequest = vm::closeSheet,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = BgPanel,
        ) {
            LibraryPanel(
                state = state,
                vm = vm,
                onImport = onImport,
                compact = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .padding(bottom = 16.dp),
            )
        }
    }
    if (showInspector) {
        ModalBottomSheet(onDismissRequest = vm::closeSheet, containerColor = BgPanel) {
            InspectorPanel(state, vm, Modifier.fillMaxWidth())
        }
    }
    if (showExport) {
        ModalBottomSheet(
            onDismissRequest = { if (state.exportProgress == null) vm.closeSheet() },
            containerColor = BgPanel,
        ) {
            ExportBody(state, vm)
        }
    }
}

@Composable
private fun ExportBody(state: EditorUiState, vm: EditorViewModel) {
    Column(Modifier.padding(16.dp)) {
        Text("匯出", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text("H.264 MP4。可選浮水印：鋒兄 / Papaya Feng / パパイヤ フェン", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        AspectRow(state, vm)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            Text("加上浮水印", color = Ink, modifier = Modifier.weight(1f))
            Switch(
                checked = state.watermark,
                onCheckedChange = vm::setWatermark,
                colors = SwitchDefaults.colors(checkedTrackColor = Primary, checkedThumbColor = PrimaryInk),
            )
        }
        val progress = state.exportProgress
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = Primary,
                trackColor = Line,
            )
            Text("${(progress * 100).toInt()}%", color = Muted, fontSize = 12.sp)
        }
        state.exportMessage?.let {
            Text(it, color = Ink, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val target = when {
                    state.height > state.width -> ExportTarget.TikTokVertical
                    state.width == state.height -> ExportTarget.InstagramSquare
                    else -> ExportTarget.YouTube1080
                }
                vm.export(target)
            },
            enabled = state.hasClips && state.exportProgress == null,
            colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = PrimaryInk),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("開始匯出", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))
    }
}

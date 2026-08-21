package com.fengbro.director.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.zIndex
import com.fengbro.director.core.model.MediaItem
import com.fengbro.director.core.model.MediaKind
import com.fengbro.director.core.time.TimeUtil
import com.fengbro.director.ui.EditorUiState
import com.fengbro.director.ui.EditorViewModel
import com.fengbro.director.ui.LibraryFilter
import com.fengbro.director.ui.theme.BgPanel
import com.fengbro.director.ui.theme.BgPanel2
import com.fengbro.director.ui.theme.Ink
import com.fengbro.director.ui.theme.Line
import com.fengbro.director.ui.theme.Muted
import com.fengbro.director.ui.theme.Primary

@Composable
fun LibraryPanel(
    state: EditorUiState,
    vm: EditorViewModel,
    onImport: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(BgPanel)
            .padding(10.dp),
    ) {
        Text("媒體庫", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterTab("媒體", LibraryFilter.All, state.libraryFilter, vm::setLibraryFilter)
            FilterTab("畫面", LibraryFilter.Video, state.libraryFilter, vm::setLibraryFilter)
            FilterTab("字幕", LibraryFilter.Subtitle, state.libraryFilter, vm::setLibraryFilter)
            FilterTab("聲音", LibraryFilter.Audio, state.libraryFilter, vm::setLibraryFilter)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onImport,
            colors = ButtonDefaults.textButtonColors(containerColor = BgPanel2, contentColor = Ink),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text("匯入媒體")
        }
        Spacer(Modifier.height(8.dp))
        if (state.visibleMedia.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onImport),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgPanel2),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+", color = Muted, fontSize = 22.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "把影片、照片、音樂或字幕匯進來",
                        color = Ink,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    Text("或按「匯入媒體」", color = Muted, fontSize = 12.sp)
                }
            }
        } else {
            Text("點一下或長按拖曳，加入播放頭位置", color = Muted, fontSize = 10.sp)
            Spacer(Modifier.height(6.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (compact) 140.dp else 120.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.visibleMedia, key = { it.id }) { item ->
                    MediaCard(
                        item = item,
                        onPlace = {
                            vm.placeMedia(item.id)
                            if (compact) vm.closeSheet()
                        },
                        onRemove = { vm.removeLibrary(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterTab(
    label: String,
    filter: LibraryFilter,
    current: LibraryFilter,
    onSelect: (LibraryFilter) -> Unit,
) {
    FilterChip(
        selected = current == filter,
        onClick = { onSelect(filter) },
        label = { Text(label, fontSize = 11.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = BgPanel2,
            selectedLabelColor = Ink,
            containerColor = BgPanel,
            labelColor = Muted,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = current == filter,
            borderColor = Line,
            selectedBorderColor = Line,
        ),
    )
}

@Composable
fun MediaCard(item: MediaItem, onPlace: () -> Unit, onRemove: () -> Unit) {
    val kind = when (item.kind) {
        MediaKind.Image -> "照片"
        MediaKind.Audio -> "音樂"
        MediaKind.Subtitle -> "字幕"
        else -> "影片"
    }
    var dragging by remember(item.id) { mutableStateOf(false) }
    var dragOffset by remember(item.id) { mutableStateOf(Offset.Zero) }
    val dragThreshold = with(LocalDensity.current) { 36.dp.toPx() }
    val haptics = LocalHapticFeedback.current
    val thumbnail = remember(item.thumbPath) {
        item.thumbPath?.let { android.graphics.BitmapFactory.decodeFile(it) }?.asImageBitmap()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("media-card-${item.id}")
            .semantics { contentDescription = "${item.name}，$kind，點一下或長按拖曳到時間軸" }
            .zIndex(if (dragging) 2f else 0f)
            .graphicsLayer {
                translationX = dragOffset.x
                translationY = dragOffset.y
                scaleX = if (dragging) 1.03f else 1f
                scaleY = if (dragging) 1.03f else 1f
                alpha = if (dragging) 0.92f else 1f
            }
            .clip(RoundedCornerShape(8.dp))
            .background(BgPanel2)
            .border(
                width = if (dragging) 2.dp else 1.dp,
                color = if (dragging) Primary else Line,
                shape = RoundedCornerShape(8.dp),
            )
            .pointerInput(item.id, dragThreshold) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragging = true
                        dragOffset = Offset.Zero
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragOffset += amount
                    },
                    onDragEnd = {
                        val shouldPlace = dragOffset.getDistance() >= dragThreshold
                        dragging = false
                        dragOffset = Offset.Zero
                        if (shouldPlace) onPlace()
                    },
                    onDragCancel = {
                        dragging = false
                        dragOffset = Offset.Zero
                    },
                )
            }
            .clickable(onClick = onPlace)
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(kindTint(item.kind)),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = "$kind：${item.name}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (item.kind == MediaKind.Video) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(androidx.compose.ui.graphics.Color(0x99000000)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
                    }
                }
            } else {
                Icon(
                    imageVector = when (item.kind) {
                        MediaKind.Image -> Icons.Default.ImageIcon
                        MediaKind.Audio -> Icons.Default.AudioFile
                        MediaKind.Subtitle -> Icons.Default.ClosedCaption
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = null,
                    tint = Ink,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(item.name, color = Ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$kind · ${TimeUtil.formatClock(item.duration)}", color = Muted, fontSize = 11.sp, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("放到時間軸", color = Primary, fontSize = 11.sp, modifier = Modifier.clickable(onClick = onPlace))
            Spacer(Modifier.width(10.dp))
            Text("移除", color = Muted, fontSize = 11.sp, modifier = Modifier.clickable(onClick = onRemove))
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.DragIndicator, contentDescription = "長按拖曳", tint = Muted, modifier = Modifier.size(18.dp))
        }
    }
}

private fun kindTint(kind: MediaKind) = when (kind) {
    MediaKind.Audio -> androidx.compose.ui.graphics.Color(0xFF2F6A4A)
    MediaKind.Subtitle -> androidx.compose.ui.graphics.Color(0xFF8A6A2C)
    MediaKind.Image -> androidx.compose.ui.graphics.Color(0xFF3A4A5A)
    else -> androidx.compose.ui.graphics.Color(0xFF2C5F6E)
}

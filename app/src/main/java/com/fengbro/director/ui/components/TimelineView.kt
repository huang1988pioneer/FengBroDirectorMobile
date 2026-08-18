package com.fengbro.director.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengbro.director.core.model.ClipKind
import com.fengbro.director.core.model.TimelineClip
import com.fengbro.director.core.model.TimelineTrack
import com.fengbro.director.core.model.TrackKind
import com.fengbro.director.core.time.TimeUtil
import com.fengbro.director.ui.theme.AudioClip
import com.fengbro.director.ui.theme.BgPanel2
import com.fengbro.director.ui.theme.BgTimeline
import com.fengbro.director.ui.theme.Ink
import com.fengbro.director.ui.theme.Line
import com.fengbro.director.ui.theme.Muted
import com.fengbro.director.ui.theme.Primary
import com.fengbro.director.ui.theme.SubtitleClip
import com.fengbro.director.ui.theme.VideoClip
import kotlin.math.roundToInt

@Composable
fun TimelineView(
    tracks: List<TimelineTrack>,
    playhead: Double,
    workspaceSeconds: Double,
    pixelsPerSecond: Double,
    selectedClipId: String?,
    onViewportWidth: (Double) -> Unit,
    onSeek: (Double) -> Unit,
    onSelect: (clipId: String?, trackId: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val contentPx = TimeUtil.timelineContentPixels(workspaceSeconds, pixelsPerSecond)
    val contentDp = with(density) { contentPx.toFloat().toDp() }

    LaunchedEffect(playhead, pixelsPerSecond, workspaceSeconds) {
        val x = TimeUtil.timelineXFromTime(playhead, 0.0, pixelsPerSecond, TimeUtil.TIMELINE_GUTTER)
        val view = scroll.viewportSize.toDouble()
        val target = (x - view * 0.45).coerceAtLeast(0.0)
        if (x < scroll.value || x > scroll.value + view - 24) {
            scroll.animateScrollTo(target.roundToInt())
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BgTimeline)
            .onSizeChanged { onViewportWidth(it.width.toDouble()) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll),
        ) {
            Column(modifier = Modifier.width(contentDp)) {
                Ruler(workspaceSeconds, pixelsPerSecond, onSeek)
                tracks.forEach { track ->
                    TrackLane(
                        track = track,
                        pixelsPerSecond = pixelsPerSecond,
                        selectedClipId = selectedClipId,
                        onSelect = onSelect,
                        onSeek = onSeek,
                    )
                }
            }
            val headX = with(density) {
                TimeUtil.timelineXFromTime(playhead, 0.0, pixelsPerSecond, TimeUtil.TIMELINE_GUTTER).toFloat().toDp()
            }
            Box(
                modifier = Modifier
                    .offset { IntOffset(with(density) { headX.roundToPx() }, 0) }
                    .width(2.dp)
                    .height((28 + tracks.size * 52).dp)
                    .background(Primary),
            )
        }
    }
}

@Composable
private fun Ruler(workspaceSeconds: Double, pixelsPerSecond: Double, onSeek: (Double) -> Unit) {
    val step = when {
        pixelsPerSecond < 8 -> 30.0
        pixelsPerSecond < 20 -> 10.0
        pixelsPerSecond < 40 -> 5.0
        else -> 1.0
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(pixelsPerSecond, workspaceSeconds) {
                detectTapGestures { offset ->
                    onSeek(TimeUtil.timeFromTimelineX(offset.x.toDouble(), 0.0, pixelsPerSecond))
                }
            },
    ) {
        val gutter = TimeUtil.TIMELINE_GUTTER.dp.toPx()
        drawRect(BgTimeline)
        var t = 0.0
        while (t <= workspaceSeconds) {
            val x = TimeUtil.timelineXFromTime(t, 0.0, pixelsPerSecond).toFloat()
            drawLine(Line, Offset(x, size.height * 0.45f), Offset(x, size.height), 1f)
            t += step
        }
        drawLine(Line, Offset(gutter, size.height), Offset(size.width, size.height), 1f)
    }
    // labels overlaid as text would need another pass; keep ticks only for density
}

@Composable
private fun TrackLane(
    track: TimelineTrack,
    pixelsPerSecond: Double,
    selectedClipId: String?,
    onSelect: (String?, String?) -> Unit,
    onSeek: (Double) -> Unit,
) {
    val height = if (track.kind == TrackKind.Video) 56.dp else 44.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(BgTimeline)
            .pointerInput(track.id, pixelsPerSecond) {
                detectTapGestures { offset ->
                    val time = TimeUtil.timeFromTimelineX(offset.x.toDouble(), 0.0, pixelsPerSecond)
                    val hit = track.clips.lastOrNull { time >= it.start && time < it.end }
                    if (hit != null) onSelect(hit.id, track.id) else {
                        onSelect(null, track.id)
                        onSeek(time)
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .width(TimeUtil.TIMELINE_GUTTER.dp)
                .fillMaxHeight()
                .background(BgPanel2)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(track.label, color = Muted, fontSize = 11.sp)
        }
        track.clips.forEach { clip ->
            ClipBlock(
                clip = clip,
                track = track,
                pixelsPerSecond = pixelsPerSecond,
                selected = clip.id == selectedClipId,
                onSelect = { onSelect(clip.id, track.id) },
            )
        }
    }
}

@Composable
private fun ClipBlock(
    clip: TimelineClip,
    track: TimelineTrack,
    pixelsPerSecond: Double,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val density = LocalDensity.current
    val x = TimeUtil.timelineXFromTime(clip.start, 0.0, pixelsPerSecond)
    val w = (clip.duration * pixelsPerSecond).coerceAtLeast(8.0)
    val color = when (clip.kind) {
        ClipKind.Subtitle, ClipKind.Title -> SubtitleClip
        ClipKind.Audio -> AudioClip
        else -> VideoClip
    }
    val xDp = with(density) { x.toFloat().toDp() }
    val wDp = with(density) { w.toFloat().toDp() }
    Box(
        modifier = Modifier
            .offset(x = xDp)
            .width(wDp)
            .fillMaxHeight()
            .padding(vertical = 6.dp, horizontal = 1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) color.copy(alpha = 1f) else color.copy(alpha = 0.88f))
            .then(if (selected) Modifier.border(1.5.dp, Primary, RoundedCornerShape(6.dp)) else Modifier)
            .pointerInput(clip.id) {
                detectTapGestures { onSelect() }
            }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (selected) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(0.dp),
            )
        }
        Text(
            clip.name,
            color = Ink,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

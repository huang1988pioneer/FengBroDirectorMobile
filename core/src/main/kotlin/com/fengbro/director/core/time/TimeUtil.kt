package com.fengbro.director.core.time

import com.fengbro.director.core.model.ClipKind
import com.fengbro.director.core.model.MediaKind
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

object TimeUtil {
    const val MIN_WORKSPACE_SECONDS = 180.0
    const val WORKSPACE_TAIL_SECONDS = 45.0
    const val LANE_EPSILON = 1e-6
    const val DURATION_SLIDER_FLOOR = 60.0
    const val TIMELINE_GUTTER = 72.0
    const val TIMELINE_TAIL_PIXELS = 80.0
    const val MIN_PIXELS_PER_SECOND = 2.0
    const val MAX_PIXELS_PER_SECOND = 160.0
    const val DEFAULT_PIXELS_PER_SECOND = 48.0
    const val TIMELINE_DRAG_SLOP = 4.0

    fun formatClock(secondsIn: Double): String {
        var seconds = secondsIn
        if (seconds.isNaN() || seconds < 0) seconds = 0.0
        val totalMs = (seconds * 1000).toLong()
        val hours = totalMs / 3_600_000
        val minutes = (totalMs % 3_600_000) / 60_000
        val secs = (totalMs % 60_000) / 1000
        val centi = (totalMs % 1000) / 10
        return if (hours >= 1) {
            "%02d:%02d:%02d".format(hours, minutes, secs)
        } else {
            "%02d:%02d.%02d".format(minutes, secs, centi)
        }
    }

    fun formatRuler(secondsIn: Double): String {
        var seconds = secondsIn
        if (seconds < 0) seconds = 0.0
        val total = seconds.toInt()
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val secs = total % 60
        return if (hours >= 1) {
            "%d:%02d:%02d".format(hours, minutes, secs)
        } else {
            "%02d:%02d".format(minutes, secs)
        }
    }

    fun snap(time: Double, grid: Double): Double {
        if (grid <= 0) return time
        return round(time / grid) * grid
    }

    fun workspaceSeconds(contentDuration: Double, playhead: Double = 0.0): Double {
        val content = if (contentDuration.isNaN() || contentDuration < 0) 0.0 else contentDuration
        val head = if (playhead.isNaN() || playhead < 0) 0.0 else playhead
        return max(MIN_WORKSPACE_SECONDS, max(content, head) + WORKSPACE_TAIL_SECONDS)
    }

    /**
     * Drop/playhead at 0 on a non-empty track appends after the last clip
     * so imported media lengthens the timeline instead of stacking.
     */
    fun placeClipTime(requested: Double, existingEnds: Iterable<Double>): Double {
        val time = max(0.0, requested)
        val end = existingEnds.maxOrNull() ?: 0.0
        if (end <= 0) return time
        if (time > 0.05) return time
        return end
    }

    fun occupiesLane(kind: ClipKind): Boolean =
        kind == ClipKind.Video || kind == ClipKind.Image || kind == ClipKind.Audio || kind == ClipKind.NestedProject

    fun rangesOverlap(aStart: Double, aEnd: Double, bStart: Double, bEnd: Double): Boolean =
        aStart < bEnd - LANE_EPSILON && bStart < aEnd - LANE_EPSILON

    fun placeOnLane(
        requestedIn: Double,
        durationIn: Double,
        occupied: Iterable<Pair<Double, Double>>,
    ): Double {
        var requested = requestedIn
        var duration = durationIn
        if (requested.isNaN() || requested < 0) requested = 0.0
        if (duration.isNaN() || duration < 0.05) duration = 0.05

        val merged = mergeRanges(occupied)
        if (merged.isEmpty()) return requested

        var best = Double.NaN
        var bestDist = Double.POSITIVE_INFINITY

        fun consider(lo: Double, hi: Double) {
            if (hi < lo - LANE_EPSILON) return
            val candidate = when {
                requested < lo -> lo
                requested > hi -> hi
                else -> requested
            }
            val dist = abs(candidate - requested)
            if (dist + LANE_EPSILON < bestDist) {
                bestDist = dist
                best = candidate
            }
        }

        consider(0.0, merged[0].first - duration)
        for (i in 0 until merged.lastIndex) {
            consider(merged[i].second, merged[i + 1].first - duration)
        }
        consider(merged.last().second, Double.MAX_VALUE)

        return if (best.isNaN()) max(0.0, merged.last().second) else best
    }

    fun clampLaneTrim(
        newStartIn: Double,
        newDurationIn: Double,
        left: Boolean,
        originalStart: Double,
        originalEnd: Double,
        others: Iterable<Pair<Double, Double>>,
    ): Pair<Double, Double> {
        val minDur = 0.05
        var newStart = newStartIn
        var newDuration = newDurationIn
        if (newStart.isNaN() || newStart < 0) newStart = 0.0
        if (newDuration.isNaN() || newDuration < minDur) newDuration = minDur

        if (left) {
            var prevEnd = 0.0
            for (o in others) {
                if (o.first < originalStart - LANE_EPSILON) prevEnd = max(prevEnd, o.second)
            }
            val end = originalEnd
            val maxStart = max(prevEnd, end - minDur)
            newStart = newStart.coerceIn(prevEnd, maxStart)
            newDuration = max(minDur, end - newStart)
            return newStart to newDuration
        }

        var nextStart = Double.POSITIVE_INFINITY
        for (o in others) {
            if (o.first > originalStart + LANE_EPSILON) nextStart = min(nextStart, o.first)
        }
        val trimmedEnd = min(newStart + newDuration, nextStart)
        return newStart to max(minDur, trimmedEnd - newStart)
    }

    private fun mergeRanges(occupied: Iterable<Pair<Double, Double>>): List<Pair<Double, Double>> {
        val merged = mutableListOf<Pair<Double, Double>>()
        for (r in occupied.filter { it.second > it.first + LANE_EPSILON }.sortedBy { it.first }) {
            if (merged.isEmpty() || r.first > merged.last().second + LANE_EPSILON) {
                merged.add(r)
            } else {
                merged[merged.lastIndex] = merged.last().first to max(merged.last().second, r.second)
            }
        }
        return merged
    }

    fun mediaClipDuration(kind: MediaKind, probed: Double): Double {
        return if (probed > 0.2) probed else 5.0
    }

    fun durationSliderCeiling(
        selectedDuration: Double,
        selectedMediaDuration: Double,
        libraryDurations: Iterable<Double>,
        clipDurations: Iterable<Double>,
    ): Double {
        var longest = selectedDuration
        if (selectedMediaDuration > longest) longest = selectedMediaDuration
        for (d in libraryDurations) if (d > longest) longest = d
        for (d in clipDurations) if (d > longest) longest = d
        if (longest.isNaN() || longest < 0) longest = 0.0
        return max(DURATION_SLIDER_FLOOR, longest + 30)
    }

    fun sourceHoldSeconds(clipDuration: Double, inPoint: Double, speed: Double, mediaDuration: Double): Double {
        if (mediaDuration <= 0) return 0.0
        val shown = max(0.0, mediaDuration - inPoint) / max(0.05, speed)
        return max(0.0, clipDuration - shown)
    }

    fun fitPixelsPerSecond(
        contentSeconds: Double,
        viewportWidth: Double,
        gutter: Double = TIMELINE_GUTTER,
        paddingSeconds: Double = 8.0,
    ): Double {
        val view = max(80.0, viewportWidth - gutter)
        val span = max(1.0, contentSeconds + max(0.0, paddingSeconds))
        return (view / span).coerceIn(MIN_PIXELS_PER_SECOND, DEFAULT_PIXELS_PER_SECOND)
    }

    fun timelineContentPixels(
        workspaceSeconds: Double,
        pixelsPerSecond: Double,
        tailPixels: Double = TIMELINE_TAIL_PIXELS,
    ): Double = max(0.0, workspaceSeconds) * max(1.0, pixelsPerSecond) + tailPixels

    fun clampTimelineScroll(
        scrollXIn: Double,
        workspaceSeconds: Double,
        pixelsPerSecond: Double,
        viewportWidth: Double,
        gutter: Double = TIMELINE_GUTTER,
        tailPixels: Double = TIMELINE_TAIL_PIXELS,
    ): Double {
        var scrollX = scrollXIn
        if (scrollX.isNaN() || scrollX.isInfinite()) scrollX = 0.0
        val content = timelineContentPixels(workspaceSeconds, pixelsPerSecond, tailPixels)
        val view = max(0.0, viewportWidth - gutter)
        return scrollX.coerceIn(0.0, max(0.0, content - view))
    }

    fun timelineDragExceededSlop(pressX: Double, nowX: Double, slop: Double = TIMELINE_DRAG_SLOP): Boolean =
        abs(nowX - pressX) >= slop

    fun timelinePanScroll(scrollAtPress: Double, pressX: Double, nowX: Double): Double =
        scrollAtPress - (nowX - pressX)

    fun timeFromTimelineX(
        x: Double,
        scrollX: Double,
        pixelsPerSecond: Double,
        gutter: Double = TIMELINE_GUTTER,
    ): Double = max(0.0, (x - gutter + scrollX) / max(1.0, pixelsPerSecond))

    fun timelineXFromTime(
        time: Double,
        scrollX: Double,
        pixelsPerSecond: Double,
        gutter: Double = TIMELINE_GUTTER,
    ): Double = gutter + max(0.0, time) * pixelsPerSecond - scrollX

    fun scrollToShowTime(
        time: Double,
        scrollX: Double,
        pixelsPerSecond: Double,
        viewportWidth: Double,
        workspaceSeconds: Double,
        gutter: Double = TIMELINE_GUTTER,
        margin: Double = 16.0,
    ): Double {
        val pps = max(1.0, pixelsPerSecond)
        val x = max(0.0, time) * pps
        val view = max(1.0, viewportWidth - gutter)
        var next = scrollX
        if (x > scrollX + view - margin) next = x - view + margin
        else if (x < scrollX) next = x - margin
        return clampTimelineScroll(next, workspaceSeconds, pps, viewportWidth, gutter)
    }
}

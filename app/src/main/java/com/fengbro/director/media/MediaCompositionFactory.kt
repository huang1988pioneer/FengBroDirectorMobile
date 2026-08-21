package com.fengbro.director.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.fengbro.director.core.export.AudioSegment
import com.fengbro.director.core.export.ExportPlan
import com.fengbro.director.core.export.VisualSegment
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/** Builds the exact Media3 composition consumed by both preview and export. */
@UnstableApi
class MediaCompositionFactory(private val context: Context) {
    fun build(plan: ExportPlan): Composition {
        val black = ensureBlackFrame(plan.width, plan.height)
        val videoItems = buildVideoItems(plan, black)
        val audioSequence = buildAudioSequence(plan)
        val sequences = buildList {
            add(EditedMediaItemSequence.withVideoFrom(videoItems))
            if (audioSequence != null) add(audioSequence)
        }
        val presentation = Presentation.createForWidthAndHeight(
            plan.width,
            plan.height,
            Presentation.LAYOUT_SCALE_TO_FIT,
        )
        return Composition.Builder(sequences)
            .setEffects(
                Effects(
                    emptyList(),
                    listOf(OverlayEffect(listOf(TimelineOverlay(plan))), presentation),
                ),
            )
            .build()
    }

    private fun buildVideoItems(plan: ExportPlan, black: File): List<EditedMediaItem> {
        val items = mutableListOf<EditedMediaItem>()
        var cursor = 0.0
        for (segment in plan.visuals.sortedBy { it.start }) {
            if (segment.start > cursor + GAP_TOLERANCE_SECONDS) {
                items += colorHold(black, segment.start - cursor, plan.frameRate)
            }
            val path = segment.path
            items += if (path.isNullOrBlank() || !File(path).exists()) {
                colorHold(black, max(MIN_ITEM_SECONDS, segment.duration), plan.frameRate)
            } else {
                visualItem(segment, plan.frameRate)
            }
            cursor = max(cursor, segment.start + segment.duration)
        }
        if (cursor < plan.durationSec - GAP_TOLERANCE_SECONDS) {
            items += colorHold(black, plan.durationSec - cursor, plan.frameRate)
        }
        if (items.isEmpty()) items += colorHold(black, plan.durationSec, plan.frameRate)
        return items
    }

    private fun buildAudioSequence(plan: ExportPlan): EditedMediaItemSequence? {
        val segments = plan.audios
            .filter { !it.path.isNullOrBlank() && File(it.path!!).exists() }
            .sortedBy { it.start }
        if (segments.isEmpty()) return null

        val builder = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
        var cursor = 0.0
        for (segment in segments) {
            if (segment.start > cursor + GAP_TOLERANCE_SECONDS) {
                builder.addGap(secondsToUs(segment.start - cursor))
            }
            builder.addItem(audioItem(segment))
            cursor = max(cursor, segment.start + segment.duration)
        }
        if (cursor < plan.durationSec - GAP_TOLERANCE_SECONDS) {
            builder.addGap(secondsToUs(plan.durationSec - cursor))
        }
        return builder.build()
    }

    private fun visualItem(segment: VisualSegment, frameRate: Double): EditedMediaItem {
        val durationUs = secondsToUs(segment.duration)
        val mediaItem = if (segment.isImage) {
            MediaItem.Builder()
                .setUri(Uri.fromFile(File(segment.path!!)))
                .setImageDurationMs(secondsToMs(segment.duration))
                .build()
        } else {
            val startMs = secondsToMs(segment.inPoint)
            val sourceDurationMs = secondsToMs(segment.duration * max(MIN_SPEED, segment.speed))
            MediaItem.Builder()
                .setUri(Uri.fromFile(File(segment.path!!)))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(startMs + sourceDurationMs)
                        .build(),
                )
                .build()
        }
        val videoEffects = mutableListOf<Effect>()
        if (segment.flipH || segment.flipV || abs(segment.rotation) > 0.1 || abs(segment.scale - 1.0) > 0.001) {
            val scaleX = if (segment.flipH) -1f else 1f
            val scaleY = if (segment.flipV) -1f else 1f
            videoEffects += ScaleAndRotateTransformation.Builder()
                .setScale(
                    scaleX * segment.scale.toFloat().coerceAtLeast(MIN_SCALE),
                    scaleY * segment.scale.toFloat().coerceAtLeast(MIN_SCALE),
                )
                .setRotationDegrees(segment.rotation.toFloat())
                .build()
        }
        return EditedMediaItem.Builder(mediaItem)
            .setDurationUs(durationUs)
            .setRemoveAudio(true)
            .setEffects(Effects(emptyList(), videoEffects))
            .apply { if (segment.isImage) setFrameRate(frameRate.toInt().coerceAtLeast(1)) }
            .build()
    }

    private fun audioItem(segment: AudioSegment): EditedMediaItem {
        val startMs = secondsToMs(segment.inPoint)
        val sourceDurationMs = secondsToMs(segment.duration * max(MIN_SPEED, segment.speed))
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(File(segment.path!!)))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(startMs + sourceDurationMs)
                    .build(),
            )
            .build()
        return EditedMediaItem.Builder(mediaItem)
            .setDurationUs(secondsToUs(segment.duration))
            .setRemoveVideo(true)
            .build()
    }

    private fun colorHold(file: File, seconds: Double, frameRate: Double): EditedMediaItem =
        EditedMediaItem.Builder(
            MediaItem.Builder()
                .setUri(Uri.fromFile(file))
                .setImageDurationMs(secondsToMs(seconds))
                .build(),
        )
            .setDurationUs(secondsToUs(seconds))
            .setFrameRate(frameRate.toInt().coerceAtLeast(1))
            .setRemoveAudio(true)
            .build()

    private fun ensureBlackFrame(width: Int, height: Int): File {
        val file = File(context.cacheDir, "black_${width}x$height.png")
        if (file.exists()) return file
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(2), height.coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.BLACK)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    private fun secondsToUs(seconds: Double): Long =
        (max(MIN_ITEM_SECONDS, seconds) * MICROS_PER_SECOND).roundToLong()

    private fun secondsToMs(seconds: Double): Long =
        (max(0.0, seconds) * MILLIS_PER_SECOND).roundToLong()

    private companion object {
        const val GAP_TOLERANCE_SECONDS = 0.04
        const val MIN_ITEM_SECONDS = 0.05
        const val MIN_SPEED = 0.05
        const val MIN_SCALE = 0.05f
        const val MICROS_PER_SECOND = 1_000_000.0
        const val MILLIS_PER_SECOND = 1_000.0
    }
}

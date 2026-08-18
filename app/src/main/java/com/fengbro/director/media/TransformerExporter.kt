package com.fengbro.director.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.fengbro.director.core.export.AudioSegment
import com.fengbro.director.core.export.ExportPlan
import com.fengbro.director.core.export.VisualSegment
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToLong

@UnstableApi
class TransformerExporter(private val context: Context) {
    data class Result(val file: File, val galleryUri: Uri?)

    suspend fun export(
        plan: ExportPlan,
        suggestedName: String,
        onProgress: (Float) -> Unit,
    ): Result {
        val outDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "exports").apply { mkdirs() }
        val outFile = File(outDir, suggestedName)
        if (outFile.exists()) outFile.delete()

        val black = ensureBlackFrame(plan.width, plan.height)
        val videoItems = buildVideoItems(plan, black)
        val audioItems = buildAudioItems(plan, black)
        val overlay = TimelineOverlay(plan)
        val presentation = Presentation.createForWidthAndHeight(
            plan.width,
            plan.height,
            Presentation.LAYOUT_SCALE_TO_FIT,
        )

        val sequences = buildList {
            add(EditedMediaItemSequence(videoItems))
            if (audioItems.isNotEmpty()) add(EditedMediaItemSequence(audioItems))
        }
        val videoEffects = listOf(
            OverlayEffect(listOf(overlay)),
            presentation,
        )
        val composition = Composition.Builder(sequences)
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        suspendCancellableCoroutine { cont ->
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        if (cont.isActive) cont.cancel(exportException)
                    }

                    override fun onFallbackApplied(
                        composition: Composition,
                        originalTransformationRequest: androidx.media3.transformer.TransformationRequest,
                        fallbackTransformationRequest: androidx.media3.transformer.TransformationRequest,
                    ) = Unit
                })
                .build()

            transformer.addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) = Unit
                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) = Unit
            })

            val progressTicker = Thread {
                val holder = android.os.Handler(android.os.Looper.getMainLooper())
                val progressHolder = ProgressHolder()
                while (cont.isActive && !Thread.interrupted()) {
                    holder.post {
                        val state = transformer.getProgress(progressHolder)
                        if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                            onProgress((progressHolder.progress / 100f).coerceIn(0f, 1f))
                        }
                    }
                    try {
                        Thread.sleep(200)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
            progressTicker.start()
            transformer.start(composition, outFile.absolutePath)
            cont.invokeOnCancellation {
                transformer.cancel()
                progressTicker.interrupt()
            }
        }

        onProgress(1f)
        val gallery = publishToGallery(outFile, suggestedName)
        return Result(outFile, gallery)
    }

    private fun buildVideoItems(plan: ExportPlan, black: File): List<EditedMediaItem> {
        val items = mutableListOf<EditedMediaItem>()
        val visuals = plan.visuals.sortedBy { it.start }
        var cursor = 0.0
        for (seg in visuals) {
            if (seg.start > cursor + 0.04) {
                items.add(colorHold(black, seg.start - cursor, plan.frameRate))
            }
            val path = seg.path
            if (path.isNullOrBlank() || !File(path).exists()) {
                items.add(colorHold(black, max(0.05, seg.duration), plan.frameRate))
            } else {
                items.add(visualItem(seg, plan.frameRate))
            }
            cursor = max(cursor, seg.start + seg.duration)
        }
        if (cursor < plan.durationSec - 0.04) {
            items.add(colorHold(black, plan.durationSec - cursor, plan.frameRate))
        }
        if (items.isEmpty()) {
            items.add(colorHold(black, plan.durationSec, plan.frameRate))
        }
        return items
    }

    private fun buildAudioItems(plan: ExportPlan, black: File): List<EditedMediaItem> {
        val audios = plan.audios.filter { !it.path.isNullOrBlank() && File(it.path!!).exists() }
            .sortedBy { it.start }
        if (audios.isEmpty()) return emptyList()
        val items = mutableListOf<EditedMediaItem>()
        var cursor = 0.0
        for (seg in audios) {
            if (seg.start > cursor + 0.04) {
                items.add(silenceHold(black, seg.start - cursor))
            }
            items.add(audioItem(seg))
            cursor = max(cursor, seg.start + seg.duration)
        }
        if (cursor < plan.durationSec - 0.04) {
            items.add(silenceHold(black, plan.durationSec - cursor))
        }
        return items
    }

    private fun visualItem(seg: VisualSegment, frameRate: Double): EditedMediaItem {
        val durationUs = (max(0.05, seg.duration) * 1_000_000).roundToLong()
        val media = if (seg.isImage) {
            MediaItem.fromUri(Uri.fromFile(File(seg.path!!)))
        } else {
            val startMs = (seg.inPoint * 1000).roundToLong().coerceAtLeast(0)
            val endMs = startMs + (max(0.05, seg.duration * max(0.05, seg.speed)) * 1000).roundToLong()
            MediaItem.Builder()
                .setUri(Uri.fromFile(File(seg.path!!)))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build(),
                )
                .build()
        }
        val effects = mutableListOf<androidx.media3.common.Effect>()
        if (seg.flipH || seg.flipV || kotlin.math.abs(seg.rotation) > 0.1) {
            val scaleX = if (seg.flipH) -1f else 1f
            val scaleY = if (seg.flipV) -1f else 1f
            effects.add(
                ScaleAndRotateTransformation.Builder()
                    .setScale(scaleX * seg.scale.toFloat().coerceAtLeast(0.05f), scaleY * seg.scale.toFloat().coerceAtLeast(0.05f))
                    .setRotationDegrees(seg.rotation.toFloat())
                    .build(),
            )
        }
        val builder = EditedMediaItem.Builder(media)
            .setDurationUs(durationUs)
            .setRemoveAudio(true)
            .setEffects(Effects(emptyList(), effects))
        if (seg.isImage) builder.setFrameRate(frameRate.toInt().coerceAtLeast(1))
        return builder.build()
    }

    private fun audioItem(seg: AudioSegment): EditedMediaItem {
        val durationUs = (max(0.05, seg.duration) * 1_000_000).roundToLong()
        val startMs = (seg.inPoint * 1000).roundToLong().coerceAtLeast(0)
        val endMs = startMs + (max(0.05, seg.duration * max(0.05, seg.speed)) * 1000).roundToLong()
        val media = MediaItem.Builder()
            .setUri(Uri.fromFile(File(seg.path!!)))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build(),
            )
            .build()
        return EditedMediaItem.Builder(media)
            .setDurationUs(durationUs)
            .setRemoveVideo(true)
            .build()
    }

    private fun colorHold(black: File, seconds: Double, frameRate: Double): EditedMediaItem {
        val durationUs = (max(0.05, seconds) * 1_000_000).roundToLong()
        return EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(black)))
            .setDurationUs(durationUs)
            .setFrameRate(frameRate.toInt().coerceAtLeast(1))
            .setRemoveAudio(true)
            .build()
    }

    private fun silenceHold(black: File, seconds: Double): EditedMediaItem {
        val durationUs = (max(0.05, seconds) * 1_000_000).roundToLong()
        return EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(black)))
            .setDurationUs(durationUs)
            .setFrameRate(30)
            .setRemoveVideo(true)
            .build()
    }

    private fun ensureBlackFrame(width: Int, height: Int): File {
        val file = File(context.cacheDir, "black_${width}x$height.png")
        if (file.exists()) return file
        val bmp = Bitmap.createBitmap(width.coerceAtLeast(2), height.coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(Color.BLACK)
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return file
    }

    private fun publishToGallery(file: File, name: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/FengBroDirector")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val uri = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (_: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }
}

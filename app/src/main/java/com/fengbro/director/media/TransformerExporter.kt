package com.fengbro.director.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.fengbro.director.core.export.ExportPlan
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

@UnstableApi
class TransformerExporter(
    private val context: Context,
    private val compositions: MediaCompositionFactory = MediaCompositionFactory(context),
) {
    data class Result(val file: File, val galleryUri: Uri?)

    suspend fun export(
        plan: ExportPlan,
        suggestedName: String,
        onProgress: (Float) -> Unit,
    ): Result {
        val outDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "exports").apply { mkdirs() }
        val outFile = File(outDir, suggestedName)
        if (outFile.exists()) outFile.delete()

        val composition = compositions.build(plan)

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

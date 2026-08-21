package com.fengbro.director.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import com.fengbro.director.core.model.MediaItem
import com.fengbro.director.core.model.MediaKind
import java.io.File
import kotlin.math.roundToLong

class MediaThumbnailer(private val context: Context) {
    fun ensure(item: MediaItem): String? {
        if (item.kind != MediaKind.Video && item.kind != MediaKind.Image) return null
        if (!File(item.path).isFile) return null

        val existing = item.thumbPath?.let(::File)?.takeIf { it.isFile && it.length() > 0L }
        if (existing != null) return existing.absolutePath

        val directory = File(context.filesDir, "thumbnails").apply { mkdirs() }
        val destination = File(directory, "${item.id}.jpg")
        return if (item.kind == MediaKind.Image) {
            createImageThumbnail(item, destination)
        } else {
            createVideoThumbnail(item, destination)
        }
    }

    private fun createImageThumbnail(item: MediaItem, destination: File): String? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(item.path, bounds)
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > THUMBNAIL_WIDTH * 2 || bounds.outHeight / sampleSize > THUMBNAIL_HEIGHT * 2) {
            sampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeFile(item.path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            ?: return null
        destination.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        bitmap.recycle()
        destination.takeIf { it.length() > 0L }?.absolutePath?.also { item.thumbPath = it }
    } catch (_: Exception) {
        destination.delete()
        null
    }

    private fun createVideoThumbnail(item: MediaItem, destination: File): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(item.path)
            val timeUs = ((item.duration.coerceAtLeast(0.0) / 3.0).coerceAtMost(1.0) * 1_000_000.0).roundToLong()
            val frame = if (Build.VERSION.SDK_INT >= 27) {
                retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    THUMBNAIL_WIDTH,
                    THUMBNAIL_HEIGHT,
                )
            } else {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (frame == null) return null
            destination.outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            frame.recycle()
            destination.takeIf { it.length() > 0L }?.absolutePath?.also { item.thumbPath = it }
        } catch (_: Exception) {
            destination.delete()
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private companion object {
        const val THUMBNAIL_WIDTH = 512
        const val THUMBNAIL_HEIGHT = 288
        const val JPEG_QUALITY = 82
    }
}

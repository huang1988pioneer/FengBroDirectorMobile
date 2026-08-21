package com.fengbro.director.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.fengbro.director.core.model.MediaItem
import com.fengbro.director.core.model.MediaKind
import com.fengbro.director.core.model.newId
import com.fengbro.director.core.subtitle.SubtitleFile
import java.io.File

class MediaImporter(private val context: Context) {
    private val thumbnailer = MediaThumbnailer(context)

    fun importUris(uris: List<Uri>): List<MediaItem> = uris.mapNotNull { importUri(it) }

    fun importUri(uri: Uri): MediaItem? {
        val name = queryName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "媒體"
        val mime = context.contentResolver.getType(uri).orEmpty()
        val ext = name.substringAfterLast('.', "").lowercase().ifBlank {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime).orEmpty()
        }
        val kind = classify(name, mime, ext)
        val id = newId()
        val dest = File(context.filesDir, "media").apply { mkdirs() }
        val local = File(dest, "$id.${ext.ifBlank { fallbackExt(kind) }}")
        context.contentResolver.openInputStream(uri)?.use { input ->
            local.outputStream().use { input.copyTo(it) }
        } ?: return null
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }

        val item = MediaItem(
            id = id,
            path = local.absolutePath,
            name = name.substringBeforeLast('.').ifBlank { name },
            kind = kind,
            uri = uri.toString(),
            sizeBytes = local.length(),
        )
        when (kind) {
            MediaKind.Subtitle -> SubtitleFile.hydrate(item)
            MediaKind.Image -> probeImage(item)
            else -> probeAv(item)
        }
        thumbnailer.ensure(item)
        return item
    }

    fun ensureThumbnail(item: MediaItem): Boolean = thumbnailer.ensure(item) != null

    private fun probeImage(item: MediaItem) {
        item.hasVideo = false
        item.hasAudio = false
        item.duration = 5.0
        runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(item.path)
            item.width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            item.height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            retriever.release()
        }
        if (item.width == 0 || item.height == 0) {
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(item.path, opts)
            item.width = opts.outWidth
            item.height = opts.outHeight
        }
    }

    private fun probeAv(item: MediaItem) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(item.path)
            val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            item.duration = if (durMs > 0) durMs / 1000.0 else 0.0
            item.width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            item.height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            item.hasVideo = item.width > 0 && item.kind != MediaKind.Audio
            val hasAudioMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            item.hasAudio = hasAudioMeta == "yes" || item.kind == MediaKind.Audio
            if (item.kind == MediaKind.Video && !item.hasVideo && item.hasAudio) {
                item.kind = MediaKind.Audio
            }
        } catch (_: Exception) {
            item.duration = 0.0
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun queryName(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return it.getString(idx)
            }
        }
        return null
    }

    private fun classify(name: String, mime: String, ext: String): MediaKind {
        if (SubtitleFile.isSubtitlePath(name) || ext in SUB_EXT) return MediaKind.Subtitle
        if (mime.startsWith("image/") || ext in IMG_EXT) return MediaKind.Image
        if (mime.startsWith("audio/") || ext in AUD_EXT) return MediaKind.Audio
        return MediaKind.Video
    }

    private fun fallbackExt(kind: MediaKind): String = when (kind) {
        MediaKind.Image -> "jpg"
        MediaKind.Audio -> "m4a"
        MediaKind.Subtitle -> "srt"
        else -> "mp4"
    }

    companion object {
        private val IMG_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")
        private val AUD_EXT = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma")
        private val SUB_EXT = setOf("srt", "vtt", "ass", "ssa", "lrc", "sub", "sbv", "smi", "sami")

        val OPEN_MIME = arrayOf("video/*", "image/*", "audio/*", "text/*", "application/x-subrip", "*/*")
    }
}

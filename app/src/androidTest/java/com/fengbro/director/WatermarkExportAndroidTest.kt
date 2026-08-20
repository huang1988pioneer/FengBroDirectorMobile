package com.fengbro.director

import android.graphics.Color
import android.media.MediaMetadataRetriever
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fengbro.director.core.export.AudioSegment
import com.fengbro.director.core.export.ExportPlan
import com.fengbro.director.core.model.ClipKind
import com.fengbro.director.core.model.TimelineClip
import com.fengbro.director.media.TransformerExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class WatermarkExportAndroidTest {
    @Test
    fun watermarkEnabledProducesVisiblePixelsInExport() = runBlocking(Dispatchers.Main) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val audioFile = File(context.cacheDir, "watermark-regression.wav")
        writeSilentWav(audioFile, durationMs = 500)
        val plan = ExportPlan(
            durationSec = 0.5,
            width = 320,
            height = 180,
            frameRate = 24.0,
            visuals = emptyList(),
            audios = listOf(
                AudioSegment(
                    clip = TimelineClip(kind = ClipKind.Audio, duration = 0.5),
                    path = audioFile.absolutePath,
                    start = 0.0,
                    duration = 0.5,
                    inPoint = 0.0,
                    speed = 1.0,
                    volume = 1.0,
                ),
            ),
            titles = emptyList(),
            watermark = true,
            background = "#000000",
        )
        val result = TransformerExporter(context).export(plan, "watermark-regression.mp4") { }

        try {
            val retriever = MediaMetadataRetriever()
            val frame = try {
                retriever.setDataSource(result.file.absolutePath)
                checkNotNull(retriever.getFrameAtTime(250_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC))
            } finally {
                retriever.release()
            }
            val brightPixels = (frame.width * 55 / 100 until frame.width).sumOf { x ->
                (frame.height * 55 / 100 until frame.height).count { y ->
                    val pixel = frame.getPixel(x, y)
                    Color.red(pixel) + Color.green(pixel) + Color.blue(pixel) > 180
                }
            }
            frame.recycle()

            assertTrue("Watermark region remained blank", brightPixels > 20)
        } finally {
            result.galleryUri?.let { context.contentResolver.delete(it, null, null) }
            result.file.delete()
            audioFile.delete()
        }
    }

    private fun writeSilentWav(file: File, durationMs: Int) {
        val sampleRate = 8_000
        val dataSize = sampleRate * durationMs / 1_000 * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + dataSize)
            put("WAVEfmt ".toByteArray())
            putInt(16)
            putShort(1.toShort())
            putShort(1.toShort())
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2.toShort())
            putShort(16.toShort())
            put("data".toByteArray())
            putInt(dataSize)
        }.array()
        file.outputStream().use { out ->
            out.write(header)
            out.write(ByteArray(dataSize))
        }
    }
}

package com.fengbro.director.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.CompositionPlayer
import com.fengbro.director.core.export.ExportPlan
import kotlin.math.roundToLong

/** Owns the experimental CompositionPlayer so callers only deal with project time. */
@OptIn(markerClass = [ExperimentalApi::class, UnstableApi::class])
class CompositionPreview(
    context: Context,
    private val compositions: MediaCompositionFactory,
) {
    private val compositionPlayer = CompositionPlayer.Builder(context).build().apply {
        repeatMode = Player.REPEAT_MODE_OFF
        volume = 1f
    }

    val player: Player get() = compositionPlayer

    fun load(plan: ExportPlan, positionSeconds: Double, playWhenReady: Boolean) {
        compositionPlayer.setComposition(compositions.build(plan), positionSeconds.toMillis())
        compositionPlayer.prepare()
        compositionPlayer.playWhenReady = playWhenReady
    }

    fun seek(positionSeconds: Double) {
        compositionPlayer.seekTo(positionSeconds.toMillis())
    }

    fun play() = compositionPlayer.play()

    fun pause() = compositionPlayer.pause()

    fun stop() = compositionPlayer.stop()

    fun release() = compositionPlayer.release()

    val currentPositionSeconds: Double
        get() = compositionPlayer.currentPosition / 1_000.0

    val isPlaying: Boolean
        get() = compositionPlayer.isPlaying

    private fun Double.toMillis(): Long = (coerceAtLeast(0.0) * 1_000.0).roundToLong()
}

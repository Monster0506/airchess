package com.airchess.ar

import kotlin.math.PI
import kotlin.math.sin

/** Timing and easing of piece animations. Pure math so it is unit-tested without Filament. */
object PieceMotion {
    const val DURATION_NANOS = 300_000_000L
    const val KNIGHT_ARC_HEIGHT = 0.03f

    private const val SMOOTHSTEP_A = 3f
    private const val SMOOTHSTEP_B = 2f

    /** Linear progress in 0..1 after [elapsedNanos]. */
    fun progress(elapsedNanos: Long): Float =
        (elapsedNanos.toFloat() / DURATION_NANOS).coerceIn(0f, 1f)

    /** Ease-in-out (smoothstep) of linear progress [t]. */
    fun easeInOut(t: Float): Float = t * t * (SMOOTHSTEP_A - SMOOTHSTEP_B * t)

    /** Vertical lift at progress [t]: zero at both ends, [height] in the middle. */
    fun arc(t: Float, height: Float): Float = sin(PI.toFloat() * t) * height
}

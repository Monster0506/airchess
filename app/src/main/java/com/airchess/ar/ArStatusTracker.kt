package com.airchess.ar

import com.airchess.model.ArStatus
import com.airchess.model.TrackingStatus

/** What one ARCore frame says about the session, reduced to plain values. */
data class ArFrameSample(
    val timestampNanos: Long,
    val cameraTracking: Boolean,
    val failure: String?,
    val planeCount: Int,
    val anchorState: String,
    val anchored: Boolean,
)

/**
 * Folds frame samples into [ArStatus] and reports it only when something changed. FPS is the
 * camera frame rate over a window of at least [fpsWindowNanos], so it updates at most twice a
 * second. Pure Kotlin; ARCore types never reach it.
 */
class ArStatusTracker(
    private val emit: (ArStatus) -> Unit,
    private val fpsWindowNanos: Long = FPS_WINDOW_NANOS,
) {
    private var current: ArStatus? = null
    private var everTracked = false
    private var lastTimestamp = Long.MIN_VALUE
    private var windowStart = Long.MIN_VALUE
    private var windowFrames = 0
    private var fps = 0

    fun onFrame(sample: ArFrameSample) {
        if (sample.timestampNanos != lastTimestamp) {
            lastTimestamp = sample.timestampNanos
            countFrame(sample.timestampNanos)
        }

        if (sample.cameraTracking) {
            everTracked = true
        }

        val status = ArStatus(
            tracking = trackingOf(sample),
            trackingFailure = sample.failure,
            planeCount = sample.planeCount,
            anchorState = sample.anchorState,
            fps = fps,
        )
        if (status != current) {
            current = status
            emit(status)
        }
    }

    private fun countFrame(timestamp: Long) {
        if (windowStart == Long.MIN_VALUE) {
            windowStart = timestamp
            windowFrames = 0
            return
        }

        windowFrames++
        val elapsed = timestamp - windowStart
        if (elapsed >= fpsWindowNanos) {
            fps = Math.round(windowFrames * NANOS_PER_SECOND / elapsed.toDouble()).toInt()
            windowStart = timestamp
            windowFrames = 0
        }
    }

    private fun trackingOf(sample: ArFrameSample): TrackingStatus = when {
        !sample.cameraTracking -> if (everTracked) TrackingStatus.LOST else TrackingStatus.INITIALIZING
        sample.anchored -> TrackingStatus.PLACED
        sample.planeCount > 0 -> TrackingStatus.PLANE_FOUND
        else -> TrackingStatus.SEARCHING
    }

    private companion object {
        const val FPS_WINDOW_NANOS = 500_000_000L
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}

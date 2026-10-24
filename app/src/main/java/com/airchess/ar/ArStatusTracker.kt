package com.airchess.ar

import com.airchess.model.ArStatus
import com.airchess.model.TrackingStatus

/**
 * Folds per-frame session facts into [ArStatus] and reports it only when something changed.
 * FPS is the camera frame rate over a window of at least [fpsWindowNanos], so it updates at most
 * twice a second. Takes plain values and allocates only when it emits, because it runs every frame.
 */
class ArStatusTracker(
    private val emit: (ArStatus) -> Unit,
    private val fpsWindowNanos: Long = FPS_WINDOW_NANOS,
) {
    private var published = false
    private var everTracked = false
    private var lastTimestamp = Long.MIN_VALUE
    private var windowStart = Long.MIN_VALUE
    private var windowFrames = 0

    private var tracking = TrackingStatus.INITIALIZING
    private var failure: String? = null
    private var planeCount = 0
    private var anchorState = ""
    private var fps = 0
    private var publishedFps = 0

    fun onFrame(
        timestampNanos: Long,
        cameraTracking: Boolean,
        failure: String?,
        planeCount: Int,
        anchorState: String,
        anchored: Boolean,
    ) {
        if (timestampNanos != lastTimestamp) {
            lastTimestamp = timestampNanos
            val newFps = countFrame(timestampNanos)
            if (newFps >= 0) {
                fps = newFps
            }
        }

        if (cameraTracking) {
            everTracked = true
        }

        val newTracking = trackingOf(cameraTracking, anchored, planeCount)
        val changed = !published ||
            newTracking != tracking ||
            failure != this.failure ||
            planeCount != this.planeCount ||
            anchorState != this.anchorState ||
            fps != publishedFps
        if (!changed) {
            return
        }

        published = true
        tracking = newTracking
        this.failure = failure
        this.planeCount = planeCount
        this.anchorState = anchorState
        publishedFps = fps
        emit(ArStatus(newTracking, failure, planeCount, anchorState, fps))
    }

    /** Returns the new FPS when a window closed, otherwise -1. */
    private fun countFrame(timestamp: Long): Int {
        if (windowStart == Long.MIN_VALUE) {
            windowStart = timestamp
            return -1
        }

        windowFrames++
        val elapsed = timestamp - windowStart
        if (elapsed < fpsWindowNanos) {
            return -1
        }

        val result = Math.round(windowFrames * NANOS_PER_SECOND / elapsed).toInt()
        windowStart = timestamp
        windowFrames = 0
        return result
    }

    private fun trackingOf(cameraTracking: Boolean, anchored: Boolean, planes: Int): TrackingStatus = when {
        !cameraTracking -> if (everTracked) TrackingStatus.LOST else TrackingStatus.INITIALIZING
        anchored -> TrackingStatus.PLACED
        planes > 0 -> TrackingStatus.PLANE_FOUND
        else -> TrackingStatus.SEARCHING
    }

    private companion object {
        const val FPS_WINDOW_NANOS = 500_000_000L
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}

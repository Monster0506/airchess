package com.airchess.ar

import com.airchess.model.ArStatus
import com.airchess.model.TrackingStatus
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class ArStatusTrackerTest {

    private val emitted = mutableListOf<ArStatus>()
    private val tracker = ArStatusTracker(emit = { emitted += it })

    private fun frame(
        timestamp: Long,
        tracking: Boolean = true,
        planes: Int = 0,
        anchored: Boolean = false,
        failure: String? = null,
    ) = tracker.onFrame(timestamp, tracking, failure, planes, if (anchored) "TRACKING" else "none", anchored)

    @Test
    fun unchangedStatusIsReportedOnce() {
        repeat(5) { frame(it * FRAME_NANOS) }

        assertEquals(1, emitted.size)
        assertEquals(TrackingStatus.SEARCHING, emitted.single().tracking)
    }

    @Test
    fun statusFollowsPlaneAndAnchorProgress() {
        frame(0, tracking = false)
        frame(FRAME_NANOS)
        frame(2 * FRAME_NANOS, planes = 1)
        frame(3 * FRAME_NANOS, planes = 1, anchored = true)

        assertEquals(
            listOf(
                TrackingStatus.INITIALIZING,
                TrackingStatus.SEARCHING,
                TrackingStatus.PLANE_FOUND,
                TrackingStatus.PLACED,
            ),
            emitted.map { it.tracking },
        )
    }

    @Test
    fun losingTrackingAfterTrackingIsLost() {
        frame(0)
        frame(FRAME_NANOS, tracking = false, failure = "Too dark")

        assertEquals(TrackingStatus.LOST, emitted.last().tracking)
        assertEquals("Too dark", emitted.last().trackingFailure)
    }

    @Test
    fun fpsIsMeasuredFromFrameTimestampsAndThrottled() {
        repeat(31) { frame(it * FRAME_NANOS) }

        assertEquals(30, emitted.last().fps)
        assertTrue(emitted.size <= 3, "fps must not be emitted per frame")
    }

    @Test
    fun duplicateTimestampsAreNotCountedAsFrames() {
        repeat(31) { index ->
            frame(index * FRAME_NANOS)
            frame(index * FRAME_NANOS)
        }

        assertEquals(30, emitted.last().fps)
    }

    private companion object {
        const val FRAME_NANOS = 33_333_333L
    }
}

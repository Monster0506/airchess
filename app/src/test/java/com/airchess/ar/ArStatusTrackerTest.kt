package com.airchess.ar

import com.airchess.model.ArStatus
import com.airchess.model.TrackingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArStatusTrackerTest {

    private val emitted = mutableListOf<ArStatus>()
    private val tracker = ArStatusTracker(emit = { emitted += it })

    private fun sample(
        timestamp: Long,
        tracking: Boolean = true,
        planes: Int = 0,
        anchored: Boolean = false,
        failure: String? = null,
    ) = ArFrameSample(timestamp, tracking, failure, planes, if (anchored) "TRACKING" else "none", anchored)

    @Test
    fun unchangedStatusIsReportedOnce() {
        repeat(5) { tracker.onFrame(sample(it * FRAME_NANOS)) }

        assertEquals(1, emitted.size)
        assertEquals(TrackingStatus.SEARCHING, emitted.single().tracking)
    }

    @Test
    fun statusFollowsPlaneAndAnchorProgress() {
        tracker.onFrame(sample(0, tracking = false))
        tracker.onFrame(sample(FRAME_NANOS))
        tracker.onFrame(sample(2 * FRAME_NANOS, planes = 1))
        tracker.onFrame(sample(3 * FRAME_NANOS, planes = 1, anchored = true))

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
        tracker.onFrame(sample(0))
        tracker.onFrame(sample(FRAME_NANOS, tracking = false, failure = "Too dark"))

        assertEquals(TrackingStatus.LOST, emitted.last().tracking)
        assertEquals("Too dark", emitted.last().trackingFailure)
    }

    @Test
    fun fpsIsMeasuredFromFrameTimestampsAndThrottled() {
        repeat(31) { tracker.onFrame(sample(it * FRAME_NANOS)) }

        assertEquals(30, emitted.last().fps)
        assertTrue("fps must not be emitted per frame", emitted.size <= 3)
    }

    @Test
    fun duplicateTimestampsAreNotCountedAsFrames() {
        repeat(31) { index ->
            tracker.onFrame(sample(index * FRAME_NANOS))
            tracker.onFrame(sample(index * FRAME_NANOS))
        }

        assertEquals(30, emitted.last().fps)
    }

    private companion object {
        const val FRAME_NANOS = 33_333_333L
    }
}

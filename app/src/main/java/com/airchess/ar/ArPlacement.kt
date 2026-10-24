package com.airchess.ar

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import io.github.sceneview.SceneScope
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val TAG = "ArPlacement"
private const val MIN_HIT_DISTANCE = 0.25f
private const val HALF = 0.5f

/**
 * Placement state of the AR board: where the reticle points, and the anchor once placed.
 * Frame callbacks write plain fields; only [anchor] and [hasHit] are Compose state, and
 * [hasHit] is written on change only.
 */
internal class ArPlacement {

    var anchor by mutableStateOf<Anchor?>(null)
        private set

    var hasHit by mutableStateOf(false)
        private set

    /** Latest ARCore frame, valid until the next update; used to hit-test at tap time. */
    var frame: Frame? = null

    private var viewWidth = 0f
    private var viewHeight = 0f

    val isPlacing: Boolean
        get() = anchor == null

    fun setViewSize(width: Int, height: Int) {
        viewWidth = width.toFloat()
        viewHeight = height.toFloat()
    }

    /** Hit test at the screen center; drives the reticle. */
    fun reticleHit(frame: Frame): HitResult? {
        val hit = hitAt(frame, viewWidth * HALF, viewHeight * HALF)
        val found = hit != null
        if (found != hasHit) {
            hasHit = found
        }
        return hit
    }

    /** Anchors the board at the tapped surface point, falling back to the screen center. */
    fun place(tapX: Float, tapY: Float): Boolean {
        val current = frame ?: return false
        val hit = hitAt(current, tapX, tapY) ?: hitAt(current, viewWidth * HALF, viewHeight * HALF) ?: return false

        val created = try {
            hit.trackable.createAnchor(facingCamera(hit.hitPose, current.camera.pose))
        } catch (error: RuntimeException) {
            Log.w(TAG, "Could not create anchor", error)
            null
        } ?: return false

        anchor = created
        return true
    }

    /** Returns to placement; the AnchorNode leaving composition detaches the anchor. */
    fun reset() {
        anchor = null
    }

    private fun hitAt(frame: Frame, x: Float, y: Float): HitResult? {
        for (hit in frame.hitTest(x, y)) {
            val plane = hit.trackable as? Plane ?: continue
            if (plane.trackingState == TrackingState.TRACKING &&
                plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                plane.isPoseInPolygon(hit.hitPose) &&
                hit.distance >= MIN_HIT_DISTANCE
            ) {
                return hit
            }
        }
        return null
    }

    /** Turns the hit pose about its up axis so the board's +Z (white side) points at the camera. */
    private fun facingCamera(hitPose: Pose, cameraPose: Pose): Pose {
        val local = hitPose.inverse().transformPoint(cameraPose.translation)
        val yaw = atan2(local[0], local[2])
        return hitPose.compose(Pose.makeRotation(0f, sin(yaw * HALF), 0f, cos(yaw * HALF)))
    }
}

private const val RETICLE_BAR = 0.004f
private const val RETICLE_THICKNESS = 0.001f
private const val RETICLE_LIFT = 0.002f

/** Board-sized square outline shown at the surface the screen center points at. */
@Composable
internal fun SceneScope.ReticleVisual(geometry: BoardGeometry, materials: BoardMaterials) {
    val size = geometry.boardSize
    val half = size / 2f - RETICLE_BAR / 2f
    val material = materials.reticle

    CubeNode(Size(size, RETICLE_THICKNESS, RETICLE_BAR), materialInstance = material, position = Position(0f, RETICLE_LIFT, half))
    CubeNode(Size(size, RETICLE_THICKNESS, RETICLE_BAR), materialInstance = material, position = Position(0f, RETICLE_LIFT, -half))
    CubeNode(Size(RETICLE_BAR, RETICLE_THICKNESS, size), materialInstance = material, position = Position(half, RETICLE_LIFT, 0f))
    CubeNode(Size(RETICLE_BAR, RETICLE_THICKNESS, size), materialInstance = material, position = Position(-half, RETICLE_LIFT, 0f))
}

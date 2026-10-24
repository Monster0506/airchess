package com.airchess.ar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.airchess.R
import com.airchess.chess.ChessSquare
import com.airchess.model.ArStatus
import com.airchess.model.BoardViewState
import com.airchess.model.TrackingStatus
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.rememberARView
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberOnGestureListener

private const val PLANE_COUNT_INTERVAL_FRAMES = 15
private const val CHIP_ALPHA = 0.65f
private val COACHING_OFFSET = 240.dp

/** Per-session bookkeeping that runs on the frame callback without allocating. */
private class FrameProbe {
    var failure: TrackingFailureReason? = null

    private var frames = 0
    private var planes = 0

    fun onFrame(session: Session, frame: Frame, placement: ArPlacement, tracker: ArStatusTracker) {
        placement.frame = frame

        if (frames++ % PLANE_COUNT_INTERVAL_FRAMES == 0) {
            planes = countPlanes(session)
        }

        val anchor = placement.anchor
        tracker.onFrame(
            timestampNanos = frame.timestamp,
            cameraTracking = frame.camera.trackingState == TrackingState.TRACKING,
            failure = failureText(failure),
            planeCount = planes,
            anchorState = anchor?.trackingState?.name ?: "none",
            anchored = anchor != null,
        )
    }

    private fun countPlanes(session: Session): Int = session.getAllTrackables(Plane::class.java).count {
        it.trackingState == TrackingState.TRACKING &&
            it.subsumedBy == null &&
            it.type == Plane.Type.HORIZONTAL_UPWARD_FACING
    }
}

private fun failureText(reason: TrackingFailureReason?): String? = when (reason) {
    null, TrackingFailureReason.NONE -> null
    TrackingFailureReason.BAD_STATE -> "Tracking error"
    TrackingFailureReason.INSUFFICIENT_LIGHT -> "Too dark"
    TrackingFailureReason.EXCESSIVE_MOTION -> "Moving too fast"
    TrackingFailureReason.INSUFFICIENT_FEATURES -> "Surface has too little detail"
    TrackingFailureReason.CAMERA_UNAVAILABLE -> "Camera unavailable"
}

/**
 * The board on a real surface. Placement: the plane renderer and a board-sized reticle follow the
 * screen center until a tap anchors the board; [repositionToken] changes send it back to placement.
 * Legality is never decided here: taps are reported as squares and the board is drawn from [board].
 */
@Composable
fun ArChessScreen(
    board: BoardViewState,
    repositionToken: Int,
    onSquareTapped: (ChessSquare) -> Unit,
    onStatusChanged: (ArStatus) -> Unit,
    modifier: Modifier = Modifier,
    onSessionFailed: (String) -> Unit = {},
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val view = rememberARView(engine)
    val cameraNode = rememberARCameraNode(engine)

    val boardRef = remember { BoardRef() }
    val placement = remember { ArPlacement() }
    val probe = remember { FrameProbe() }

    val currentOnStatus by rememberUpdatedState(onStatusChanged)
    var tracking by remember { mutableStateOf(TrackingStatus.INITIALIZING) }
    val tracker = remember {
        ArStatusTracker(emit = { status ->
            tracking = status.tracking
            currentOnStatus(status)
        })
    }

    val handledToken = remember { intArrayOf(repositionToken) }
    LaunchedEffect(repositionToken) {
        if (handledToken[0] != repositionToken) {
            handledToken[0] = repositionToken
            placement.reset()
        }
    }

    Box(modifier) {
        ARSceneView(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { placement.setViewSize(it.width, it.height) },
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            view = view,
            cameraNode = cameraNode,
            sessionCameraConfig = null,
            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL,
            planeRenderer = placement.isPlacing,
            permissionHandler = null,
            cameraPermissionOverlay = null,
            arCoreAvailabilityOverlay = null,
            onSessionFailed = { error -> onSessionFailed(error.message ?: error.javaClass.simpleName) },
            onTrackingFailureChanged = { probe.failure = it },
            onSessionUpdated = { session, frame -> probe.onFrame(session, frame, placement, tracker) },
            onGestureListener = rememberOnGestureListener(
                onSingleTapConfirmed = { event, _ ->
                    if (placement.isPlacing) {
                        placement.place(event.x, event.y)
                    } else {
                        boardRef.pick(view, event.x, event.y)?.let(onSquareTapped)
                    }
                },
            ),
        ) {
            val materials = rememberBoardMaterials(materialLoader)
            val anchor = placement.anchor

            if (anchor == null) {
                HitResultNode(hitTest = { frame -> placement.reticleHit(frame) }) {
                    ReticleVisual(boardRef.geometry, materials)
                }
            } else {
                AnchorNode(anchor = anchor) {
                    BoardContent(board, boardRef, materials)
                }
            }
        }

        val coaching = when {
            tracking == TrackingStatus.LOST -> stringResource(R.string.ar_tracking_lost)
            !placement.isPlacing -> null
            placement.hasHit -> stringResource(R.string.ar_coach_tap_to_place)
            else -> stringResource(R.string.ar_coach_find_table)
        }
        if (coaching != null) {
            CoachingChip(coaching, Modifier.align(Alignment.Center).padding(top = COACHING_OFFSET))
        }
    }
}

@Composable
private fun CoachingChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = CHIP_ALPHA),
        contentColor = Color.White,
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
    }
}

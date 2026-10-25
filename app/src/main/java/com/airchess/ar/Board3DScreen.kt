package com.airchess.ar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.airchess.chess.ChessSquare
import com.airchess.media.SceneCapture
import com.airchess.model.BoardViewState
import io.github.sceneview.SceneView
import io.github.sceneview.createDefaultCameraManipulator
import io.github.sceneview.math.Position
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberView
import io.github.sceneview.rememberRenderInvalidator

/** Orbit camera start: white's side, 45 degrees above the board, looking at its center. */
private val HOME_EYE = Position(0f, 0.9f, 0.9f)
private val HOME_TARGET = Position(0f, 0f, 0f)

/**
 * Non-AR board for devices without ARCore and for emulators. Same [BoardContent] as the AR screen.
 * The camera always starts on white's side; the black perspective rotates the board content, which
 * puts black's pieces nearest the viewer exactly as in AR. Drag to orbit, pinch to zoom.
 */
@Composable
fun Board3DScreen(
    board: BoardViewState,
    onSquareTapped: (ChessSquare) -> Unit,
    capture: SceneCapture,
    modifier: Modifier = Modifier,
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val view = rememberView(engine)
    val cameraNode = rememberCameraNode(engine)
    val manipulator = rememberCameraManipulator(
        creator = { createDefaultCameraManipulator(eyePosition = HOME_EYE, targetPosition = HOME_TARGET) },
    )
    val boardRef = remember { BoardRef() }

    val invalidator = rememberRenderInvalidator()
    SceneView(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        materialLoader = materialLoader,
        view = view,
        cameraNode = cameraNode,
        cameraManipulator = manipulator,
        renderInvalidator = invalidator,
        surfaceMirrorer = capture.rememberMirror(invalidator),
        autoCenterContent = false,
        onGestureListener = rememberOnGestureListener(
            onSingleTapConfirmed = { event, _ ->
                boardRef.pick(view, event.x, event.y)?.let(onSquareTapped)
            },
        ),
    ) {
        BoardContent(board, boardRef)
    }
}

package com.airchess.ar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.airchess.chess.ChessColor
import com.airchess.chess.ChessSquare
import com.airchess.chess.PieceType
import com.airchess.model.BoardViewState
import com.google.android.filament.MaterialInstance
import com.google.android.filament.View
import dev.romainguy.kotlin.math.inverse
import io.github.sceneview.SceneScope
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.math.worldToLocalDirection
import io.github.sceneview.math.worldToLocalPosition
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.utils.screenToRay
import io.github.sceneview.node.Node as NodeImpl

/** Yaw of the content root that puts [perspective]'s pieces nearest the viewer. */
internal fun perspectiveYaw(perspective: ChessColor): Float =
    if (perspective == ChessColor.WHITE) 0f else BLACK_PERSPECTIVE_YAW

private const val BLACK_PERSPECTIVE_YAW = 180f

/**
 * Tap picking uses a ray from the camera instead of SceneView's collision system: the collision
 * hit test only reports nodes with collision shapes and gives nothing for empty squares, while
 * the board needs a square for every tap. The ray is moved into board-local space through the
 * content root's world transform, so the black perspective rotation is handled for free.
 */
class BoardRef(val geometry: BoardGeometry = BoardGeometry()) {
    internal var root: NodeImpl? = null
    internal var pieces: Map<ChessSquare, PieceType> = emptyMap()

    /** Square under screen point ([xPx], [yPx]) of [view], or null. */
    fun pick(view: View, xPx: Float, yPx: Float): ChessSquare? {
        val ray = view.screenToRay(xPx, yPx) ?: return null
        val rootNode = root ?: return null
        val toLocal = inverse(rootNode.worldTransform)

        return BoardPicker.pick(
            geometry,
            worldToLocalPosition(ray.origin, toLocal).toVec3(),
            worldToLocalDirection(ray.direction, toLocal).toVec3(),
            pieces,
        )
    }
}

/**
 * Board, pieces and indicators under one root node, shared by the AR and the 3D screens.
 * Everything is placed in board-local space from [BoardRef.geometry]; the root is rotated for
 * the black perspective.
 */
@Composable
fun SceneScope.BoardContent(
    board: BoardViewState,
    ref: BoardRef,
    materials: BoardMaterials = rememberBoardMaterials(materialLoader),
) {
    val pieces = remember(board.position) { board.position.pieces().associate { (square, piece) -> square to piece.type } }
    SideEffect { ref.pieces = pieces }
    DisposableEffect(ref) { onDispose { ref.root = null } }

    Node(
        rotation = Rotation(0f, perspectiveYaw(board.perspective), 0f),
        apply = { ref.root = this },
    ) {
        BoardBase(ref.geometry, materials)
        IndicatorLayer(board, ref.geometry, materials)
        PieceLayer(board.position, ref.geometry, materials)
    }
}

/** The authored board model, or the procedural board when the asset is missing. */
@Composable
private fun SceneScope.BoardBase(geometry: BoardGeometry, materials: BoardMaterials) {
    val state by produceState<AssetState<ModelInstance>>(AssetState.Loading, modelLoader) {
        value = modelLoader.loadBoardModel()
    }

    val loaded = state
    DisposableEffect(loaded) {
        onDispose { (loaded as? AssetState.Ready)?.let { modelLoader.destroyModel(it.value.asset) } }
    }

    when (val current = state) {
        AssetState.Loading -> Unit
        AssetState.Missing -> ProceduralBoard(geometry, materials)
        is AssetState.Ready -> ModelNode(modelInstance = current.value, autoAnimate = false)
    }
}

private const val TILE_THICKNESS = 0.006f
private const val SLAB_THICKNESS = 0.010f
private const val FRAME_WIDTH = 0.02f
private const val FRAME_HEIGHT = 0.012f

/** 64 thin tiles, a base slab and a four-bar frame; the tile tops sit at y = 0. */
@Composable
private fun SceneScope.ProceduralBoard(geometry: BoardGeometry, materials: BoardMaterials) {
    val square = geometry.squareSize
    ChessSquare.ALL.forEach { chessSquare ->
        key(chessSquare) {
            val center = geometry.squareToLocalPosition(chessSquare)
            Tile(
                Size(square, TILE_THICKNESS, square),
                Position(center.x, -TILE_THICKNESS / 2f, center.z),
                if (chessSquare.isLight) materials.lightSquare else materials.darkSquare,
            )
        }
    }

    val outer = geometry.boardSize + 2f * FRAME_WIDTH
    val half = geometry.boardSize / 2f + FRAME_WIDTH / 2f
    val frameY = -FRAME_HEIGHT / 2f
    Tile(Size(outer, SLAB_THICKNESS, outer), Position(0f, -TILE_THICKNESS - SLAB_THICKNESS / 2f, 0f), materials.frame)
    Tile(Size(outer, FRAME_HEIGHT, FRAME_WIDTH), Position(0f, frameY, half), materials.frame)
    Tile(Size(outer, FRAME_HEIGHT, FRAME_WIDTH), Position(0f, frameY, -half), materials.frame)
    Tile(Size(FRAME_WIDTH, FRAME_HEIGHT, geometry.boardSize), Position(half, frameY, 0f), materials.frame)
    Tile(Size(FRAME_WIDTH, FRAME_HEIGHT, geometry.boardSize), Position(-half, frameY, 0f), materials.frame)
}

@Composable
private fun SceneScope.Tile(size: Size, position: Position, material: MaterialInstance) {
    CubeNode(size = size, materialInstance = material, position = position)
}

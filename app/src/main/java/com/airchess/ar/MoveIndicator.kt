package com.airchess.ar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import com.airchess.chess.ChessMove
import com.airchess.chess.ChessSquare
import com.airchess.model.BoardViewState
import com.google.android.filament.MaterialInstance
import io.github.sceneview.SceneScope
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size

/**
 * Heights of the flat overlays above the board surface, spaced 1 mm+ apart to avoid z-fighting.
 * Each overlay is a thin slab; the listed value is the slab center.
 */
private object Layer {
    const val THICKNESS = 0.001f
    const val TILE = 0.0005f
    const val DOT = 0.0025f
    const val RING = 0.0035f
    const val ARROW = 0.0050f
}

private const val TILE_FILL = 0.98f
private const val DOT_RADIUS = 0.3f
private const val DOT_SIDES = 32
private const val RING_HALF = 0.46f
private const val RING_BAR = 0.07f

/** Highlight kind per square, highest priority first when several apply to one square. */
internal fun tileKinds(board: BoardViewState): Map<ChessSquare, TileKind> {
    val result = LinkedHashMap<ChessSquare, TileKind>()

    fun put(square: ChessSquare?, kind: TileKind) {
        if (square != null && square !in result) {
            result[square] = kind
        }
    }

    put(board.selectedSquare, TileKind.SELECTED)
    put(board.checkedKingSquare, TileKind.CHECK)
    board.hint?.let {
        put(it.from, TileKind.HINT)
        put(it.to, TileKind.HINT)
    }
    board.lastMove?.let {
        put(it.from, TileKind.LAST_MOVE)
        put(it.to, TileKind.LAST_MOVE)
    }
    board.threatenedSquares.forEach { put(it, TileKind.THREATENED) }

    return result
}

/** Selection, destinations, last move, check, threats and the hint arrow. */
@Composable
internal fun SceneScope.IndicatorLayer(board: BoardViewState, geometry: BoardGeometry, materials: BoardMaterials) {
    val tiles = remember(
        board.selectedSquare, board.checkedKingSquare, board.hint, board.lastMove, board.threatenedSquares,
    ) { tileKinds(board) }
    tiles.forEach { (square, kind) ->
        key(square) { TileOverlay(square, materials.tile(kind), geometry) }
    }

    val destinations = remember(board.legalMoves) { board.legalMoves.distinctBy { it.to } }
    destinations.forEach { move ->
        key(move.to) {
            if (move.isCapture) {
                CaptureRing(move.to, materials.captureRing, geometry)
            } else {
                MoveDot(move.to, materials.moveDot, geometry)
            }
        }
    }

    board.hint?.let { hint ->
        key(hint.from, hint.to) { HintArrow(hint, materials.hintArrow, geometry) }
    }
}

@Composable
private fun SceneScope.TileOverlay(square: ChessSquare, material: MaterialInstance, geometry: BoardGeometry) {
    val center = geometry.squareToLocalPosition(square)
    val side = geometry.squareSize * TILE_FILL
    CubeNode(
        size = Size(side, Layer.THICKNESS, side),
        materialInstance = material,
        position = Position(center.x, Layer.TILE, center.z),
    )
}

@Composable
private fun SceneScope.MoveDot(square: ChessSquare, material: MaterialInstance, geometry: BoardGeometry) {
    val center = geometry.squareToLocalPosition(square)
    CylinderNode(
        radius = geometry.squareSize * DOT_RADIUS,
        height = Layer.THICKNESS,
        sideCount = DOT_SIDES,
        materialInstance = material,
        position = Position(center.x, Layer.DOT, center.z),
    )
}

/** Four thin bars forming a square outline inside the tile. */
@Composable
private fun SceneScope.CaptureRing(square: ChessSquare, material: MaterialInstance, geometry: BoardGeometry) {
    val center = geometry.squareToLocalPosition(square)
    val half = geometry.squareSize * RING_HALF
    val bar = geometry.squareSize * RING_BAR
    val span = half * 2f

    Node(position = Position(center.x, Layer.RING, center.z)) {
        CubeNode(Size(span, Layer.THICKNESS, bar), materialInstance = material, position = Position(0f, 0f, half - bar / 2f))
        CubeNode(Size(span, Layer.THICKNESS, bar), materialInstance = material, position = Position(0f, 0f, -half + bar / 2f))
        CubeNode(Size(bar, Layer.THICKNESS, span - 2f * bar), materialInstance = material, position = Position(half - bar / 2f, 0f, 0f))
        CubeNode(Size(bar, Layer.THICKNESS, span - 2f * bar), materialInstance = material, position = Position(-half + bar / 2f, 0f, 0f))
    }
}

/** Flat arrow: a shaft bar and two angled head bars, laid out by [ArrowLayout]. */
@Composable
private fun SceneScope.HintArrow(hint: ChessMove, material: MaterialInstance, geometry: BoardGeometry) {
    val segments = remember(hint.from, hint.to, geometry) {
        ArrowLayout.segments(
            geometry.squareToLocalPosition(hint.from),
            geometry.squareToLocalPosition(hint.to),
            geometry.squareSize,
        )
    }

    segments.forEachIndexed { index, segment ->
        key(index) {
            CubeNode(
                size = Size(segment.length, Layer.THICKNESS, geometry.squareSize * ArrowLayout.BAR_WIDTH),
                materialInstance = material,
                position = Position(segment.centerX, Layer.ARROW, segment.centerZ),
                rotation = Rotation(0f, segment.yawDegrees, 0f),
            )
        }
    }
}

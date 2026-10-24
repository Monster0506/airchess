package com.airchess.ar

import androidx.compose.runtime.Composable
import com.airchess.chess.ChessColor
import com.airchess.chess.ChessPiece
import com.airchess.chess.PieceType
import com.google.android.filament.MaterialInstance
import io.github.sceneview.SceneScope
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size

private const val SIDES = 24
private const val BASE_RADIUS = 0.016f
private const val BASE_HEIGHT = 0.005f
private const val KNIGHT_SHIFT = 0.005f

/** Stand-in pieces built from primitives, used when a `models/<color>_<type>.glb` asset is missing. */
@Composable
fun SceneScope.ProceduralPiece(piece: ChessPiece, materials: BoardMaterials) {
    val material = materials.piece(piece.color)

    Disc(BASE_RADIUS, BASE_HEIGHT, BASE_HEIGHT / 2f, material)

    when (piece.type) {
        PieceType.PAWN -> {
            Disc(0.007f, 0.014f, 0.012f, material)
            Ball(0.0085f, 0.0245f, material)
        }
        PieceType.ROOK -> {
            Disc(0.011f, 0.025f, 0.0175f, material)
            Block(Size(0.024f, 0.007f, 0.024f), 0.0335f, 0f, material)
        }
        PieceType.KNIGHT -> {
            val forward = if (piece.color == ChessColor.WHITE) -KNIGHT_SHIFT else KNIGHT_SHIFT
            Disc(0.009f, 0.018f, 0.014f, material)
            Block(Size(0.012f, 0.016f, 0.022f), 0.031f, forward, material)
        }
        PieceType.BISHOP -> {
            Disc(0.008f, 0.024f, 0.017f, material)
            Ball(0.009f, 0.0335f, material)
        }
        PieceType.QUEEN -> {
            Disc(0.010f, 0.028f, 0.019f, material)
            Ball(0.0105f, 0.0345f, material)
        }
        PieceType.KING -> {
            Disc(0.010f, 0.026f, 0.018f, material)
            Disc(0.0115f, 0.004f, 0.033f, material)
            Block(Size(0.005f, 0.012f, 0.005f), 0.037f, 0f, material)
            Block(Size(0.014f, 0.004f, 0.005f), 0.039f, 0f, material)
        }
    }
}

@Composable
private fun SceneScope.Disc(radius: Float, height: Float, centerY: Float, material: MaterialInstance) {
    CylinderNode(radius = radius, height = height, sideCount = SIDES, materialInstance = material, position = Position(0f, centerY, 0f))
}

@Composable
private fun SceneScope.Ball(radius: Float, centerY: Float, material: MaterialInstance) {
    SphereNode(radius = radius, materialInstance = material, position = Position(0f, centerY, 0f))
}

@Composable
private fun SceneScope.Block(size: Size, centerY: Float, offsetZ: Float, material: MaterialInstance) {
    CubeNode(size = size, materialInstance = material, position = Position(0f, centerY, offsetZ))
}

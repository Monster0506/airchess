package com.airchess.ar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.airchess.chess.ChessColor
import io.github.sceneview.loaders.MaterialLoader

/** What a highlighted square is highlighted for; declaration order is display priority. */
enum class TileKind {
    SELECTED,
    CHECK,
    HINT,
    LAST_MOVE,
    THREATENED,
}

/**
 * Every material the board scene uses, created once per [MaterialLoader]. The loader owns and
 * destroys the instances, so nothing here needs explicit cleanup.
 */
class BoardMaterials(private val loader: MaterialLoader) {

    val lightSquare = lit(LIGHT_SQUARE, roughness = 0.65f)
    val darkSquare = lit(DARK_SQUARE, roughness = 0.65f)
    val frame = lit(FRAME, roughness = 0.5f)
    private val ivory = lit(IVORY, roughness = 0.35f)
    private val ebony = lit(EBONY, roughness = 0.35f)

    val moveDot = unlit(Color(0xFF2E7D32).copy(alpha = 0.55f))
    val captureRing = unlit(Color(0xFFD32F2F).copy(alpha = 0.85f))
    val hintArrow = unlit(Color(0xFFFFEB3B).copy(alpha = 0.92f))
    val reticle = unlit(Color(0xFFFFFFFF).copy(alpha = 0.9f))

    private val tiles = mapOf(
        TileKind.SELECTED to unlit(Color(0xFF29B6F6).copy(alpha = 0.60f)),
        TileKind.CHECK to unlit(Color(0xFFE53935).copy(alpha = 0.65f)),
        TileKind.HINT to unlit(Color(0xFFFFEB3B).copy(alpha = 0.45f)),
        TileKind.LAST_MOVE to unlit(Color(0xFFFFB300).copy(alpha = 0.35f)),
        TileKind.THREATENED to unlit(Color(0xFFE53935).copy(alpha = 0.30f)),
    )

    fun tile(kind: TileKind) = tiles.getValue(kind)

    fun piece(color: ChessColor) = if (color == ChessColor.WHITE) ivory else ebony

    private fun lit(color: Color, roughness: Float) =
        loader.createColorInstance(color, metallic = 0f, roughness = roughness, reflectance = 0.25f)

    private fun unlit(color: Color) = loader.createUnlitColorInstance(color)

    private companion object {
        val LIGHT_SQUARE = Color(0xFFE8D5B0)
        val DARK_SQUARE = Color(0xFF8B5A3C)
        val FRAME = Color(0xFF4A2E1F)
        val IVORY = Color(0xFFF2EBDD)
        val EBONY = Color(0xFF2B2420)
    }
}

@Composable
fun rememberBoardMaterials(loader: MaterialLoader): BoardMaterials = remember(loader) { BoardMaterials(loader) }

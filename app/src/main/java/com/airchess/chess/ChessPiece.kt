package com.airchess.chess

enum class ChessColor {
    WHITE,
    BLACK;

    val opposite: ChessColor
        get() = if (this == WHITE) BLACK else WHITE
}

enum class PieceType(val symbol: Char) {
    PAWN('p'),
    KNIGHT('n'),
    BISHOP('b'),
    ROOK('r'),
    QUEEN('q'),
    KING('k');

    companion object {
        val PROMOTIONS = listOf(QUEEN, ROOK, BISHOP, KNIGHT)

        fun fromSymbol(symbol: Char): PieceType? = entries.firstOrNull { it.symbol == symbol.lowercaseChar() }
    }
}

data class ChessPiece(val color: ChessColor, val type: PieceType) {

    /** FEN letter: uppercase for white, lowercase for black. */
    val fenChar: Char
        get() = if (color == ChessColor.WHITE) type.symbol.uppercaseChar() else type.symbol

    companion object {
        fun fromFenChar(c: Char): ChessPiece? {
            val type = PieceType.fromSymbol(c) ?: return null
            val color = if (c.isUpperCase()) ChessColor.WHITE else ChessColor.BLACK
            return ChessPiece(color, type)
        }
    }
}

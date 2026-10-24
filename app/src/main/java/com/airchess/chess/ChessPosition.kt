package com.airchess.chess

data class CastlingRights(
    val whiteKingside: Boolean,
    val whiteQueenside: Boolean,
    val blackKingside: Boolean,
    val blackQueenside: Boolean,
) {
    companion object {
        val ALL = CastlingRights(true, true, true, true)
        val NONE = CastlingRights(false, false, false, false)
    }
}

/**
 * Immutable snapshot of a chess position.
 * [board] holds 64 entries indexed by [ChessSquare.index] (a1 = 0, h8 = 63).
 */
data class ChessPosition(
    val board: List<ChessPiece?>,
    val sideToMove: ChessColor,
    val castling: CastlingRights,
    val enPassant: ChessSquare?,
    val halfmoveClock: Int,
    val fullmoveNumber: Int,
) {
    init {
        require(board.size == ChessSquare.COUNT) { "Board must have 64 squares" }
    }

    operator fun get(square: ChessSquare): ChessPiece? = board[square.index]

    fun pieces(): List<Pair<ChessSquare, ChessPiece>> =
        ChessSquare.ALL.mapNotNull { sq -> board[sq.index]?.let { sq to it } }

    fun kingSquare(color: ChessColor): ChessSquare? =
        ChessSquare.ALL.firstOrNull { board[it.index] == ChessPiece(color, PieceType.KING) }

    companion object {
        private val BACK_RANK = listOf(
            PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN,
            PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK,
        )

        val INITIAL: ChessPosition = ChessPosition(
            board = List(ChessSquare.COUNT) { index ->
                val square = ChessSquare.of(index)
                when (square.rank) {
                    0 -> ChessPiece(ChessColor.WHITE, BACK_RANK[square.file])
                    1 -> ChessPiece(ChessColor.WHITE, PieceType.PAWN)
                    6 -> ChessPiece(ChessColor.BLACK, PieceType.PAWN)
                    7 -> ChessPiece(ChessColor.BLACK, BACK_RANK[square.file])
                    else -> null
                }
            },
            sideToMove = ChessColor.WHITE,
            castling = CastlingRights.ALL,
            enPassant = null,
            halfmoveClock = 0,
            fullmoveNumber = 1,
        )
    }
}

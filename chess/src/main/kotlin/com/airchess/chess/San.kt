package com.airchess.chess

object San {

    /** SAN of [move] played from [position], including disambiguation and +/# suffix. */
    fun format(position: ChessPosition, move: ChessMove): String {
        val after = ChessRules.applyMove(position, move)
        val afterMoves = ChessRules.legalMoves(after)
        val suffix = when {
            !ChessRules.isInCheck(after, after.sideToMove) -> ""
            afterMoves.isEmpty() -> "#"
            else -> "+"
        }
        return body(position, move) + suffix
    }

    private fun body(position: ChessPosition, move: ChessMove): String {
        if (move.kind == MoveKind.CASTLE_KINGSIDE) {
            return "O-O"
        }
        if (move.kind == MoveKind.CASTLE_QUEENSIDE) {
            return "O-O-O"
        }

        val capture = if (move.isCapture) "x" else ""
        val promotion = move.promotion?.let { "=${it.symbol.uppercaseChar()}" } ?: ""
        if (move.piece.type == PieceType.PAWN) {
            val origin = if (move.isCapture) "${move.from.algebraic[0]}" else ""
            return origin + capture + move.to.algebraic + promotion
        }
        return move.piece.type.symbol.uppercaseChar().toString() +
            disambiguation(position, move) + capture + move.to.algebraic
    }

    private fun disambiguation(position: ChessPosition, move: ChessMove): String {
        if (move.piece.type == PieceType.KING) {
            return ""
        }
        val rivals = ChessRules.legalMoves(position).filter {
            it.from != move.from && it.to == move.to && it.piece == move.piece
        }
        if (rivals.isEmpty()) {
            return ""
        }
        val fileUnique = rivals.none { it.from.file == move.from.file }
        if (fileUnique) {
            return move.from.algebraic.substring(0, 1)
        }
        val rankUnique = rivals.none { it.from.rank == move.from.rank }
        if (rankUnique) {
            return move.from.algebraic.substring(1)
        }
        return move.from.algebraic
    }
}

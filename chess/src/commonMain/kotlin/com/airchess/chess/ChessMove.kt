package com.airchess.chess

enum class MoveKind {
    NORMAL,
    DOUBLE_PAWN_PUSH,
    EN_PASSANT,
    CASTLE_KINGSIDE,
    CASTLE_QUEENSIDE,
}

data class ChessMove(
    val from: ChessSquare,
    val to: ChessSquare,
    val piece: ChessPiece,
    val captured: ChessPiece? = null,
    val promotion: PieceType? = null,
    val kind: MoveKind = MoveKind.NORMAL,
) {

    val isCapture: Boolean
        get() = captured != null

    val isCastling: Boolean
        get() = kind == MoveKind.CASTLE_KINGSIDE || kind == MoveKind.CASTLE_QUEENSIDE

    /** Square of the captured piece; differs from [to] only for en passant. */
    val captureSquare: ChessSquare?
        get() = when {
            captured == null -> null
            kind == MoveKind.EN_PASSANT -> ChessSquare.at(to.file, from.rank)
            else -> to
        }

    /** UCI long algebraic notation, e.g. "e2e4", "e7e8q". */
    val uci: String
        get() = from.algebraic + to.algebraic + (promotion?.symbol?.toString() ?: "")
}

/** A move as played, with its SAN computed against the position before it. */
data class MoveRecord(
    val move: ChessMove,
    val san: String,
)

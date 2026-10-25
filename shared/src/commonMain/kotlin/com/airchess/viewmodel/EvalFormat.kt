package com.airchess.viewmodel

import com.airchess.chess.ChessColor
import com.airchess.chess.GameStatus
import com.airchess.engine.EngineScore
import com.airchess.protocol.GameResult
import com.airchess.protocol.ResultReason
import com.airchess.protocol.Side

private const val CENTIPAWNS_PER_PAWN = 100

/** Scores are flipped to White's perspective: "+0.35", "-1.20", "M3" or "-M2". */
fun formatEval(score: EngineScore, sideToMove: ChessColor): String {
    val sign = if (sideToMove == ChessColor.WHITE) 1 else -1
    return when (score) {
        is EngineScore.Centipawns -> formatPawns(score.value * sign)
        is EngineScore.Mate -> {
            val moves = score.moves * sign
            if (moves < 0) "-M${-moves}" else "M$moves"
        }
    }
}

private fun formatPawns(centipawns: Int): String {
    if (centipawns == 0) {
        return "0.00"
    }
    val sign = if (centipawns > 0) "+" else "-"
    val abs = kotlin.math.abs(centipawns)
    return "$sign${abs / CENTIPAWNS_PER_PAWN}.${(abs % CENTIPAWNS_PER_PAWN).toString().padStart(2, '0')}"
}

private fun ChessColor.label(): String = if (this == ChessColor.WHITE) "White" else "Black"

private fun Side.label(): String = if (this == Side.WHITE) "White" else "Black"

fun localStatusText(status: GameStatus, turn: ChessColor): String =
    when (status) {
        GameStatus.IN_PROGRESS -> "${turn.label()} to move"
        GameStatus.CHECK -> "${turn.label()} to move - Check"
        GameStatus.CHECKMATE -> "Checkmate - ${turn.opposite.label()} wins"
        GameStatus.STALEMATE -> "Stalemate"
        GameStatus.DRAW_FIFTY_MOVES -> "Draw - fifty-move rule"
        GameStatus.DRAW_INSUFFICIENT_MATERIAL -> "Draw - insufficient material"
        GameStatus.DRAW_THREEFOLD_REPETITION -> "Draw - threefold repetition"
    }

fun onlineStatusText(
    status: GameStatus,
    myTurn: Boolean,
    waitingForOpponent: Boolean,
    resultText: String?,
): String {
    if (resultText != null) {
        return resultText
    }
    if (waitingForOpponent) {
        return "Waiting for opponent"
    }
    val base = if (myTurn) "Your move" else "Opponent's move"
    return if (status == GameStatus.CHECK) "$base - Check" else base
}

fun resultText(result: GameResult, mySide: Side?): String {
    val winner = result.winner
    val headline = when {
        winner == null -> "Draw"
        winner == mySide -> "You won"
        mySide == null -> "${winner.label()} wins"
        else -> "You lost"
    }
    return "$headline - ${reasonText(result.reason)}"
}

private fun reasonText(reason: ResultReason): String =
    when (reason) {
        ResultReason.CHECKMATE -> "checkmate"
        ResultReason.STALEMATE -> "stalemate"
        ResultReason.RESIGNATION -> "resignation"
        ResultReason.TIMEOUT -> "time ran out"
        ResultReason.DRAW_AGREED -> "draw agreed"
        ResultReason.FIFTY_MOVES -> "fifty-move rule"
        ResultReason.INSUFFICIENT_MATERIAL -> "insufficient material"
        ResultReason.THREEFOLD_REPETITION -> "threefold repetition"
        ResultReason.ABANDONED -> "opponent left"
    }

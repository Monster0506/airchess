package com.airchess.server

import com.airchess.chess.ChessColor
import com.airchess.chess.ChessGame
import com.airchess.chess.GameStatus
import com.airchess.chess.PieceType
import com.airchess.protocol.GameResult
import com.airchess.protocol.ResultReason
import com.airchess.protocol.Side

internal class ChessReferee(private val game: ChessGame = ChessGame()) : MoveReferee {

    override fun play(uci: String): MoveOutcome {
        val mover = game.currentTurn.toSide()
        val move = game.findMoveByUci(uci) ?: return MoveOutcome.Illegal
        if (!game.makeMove(move)) {
            return MoveOutcome.Illegal
        }
        return MoveOutcome.Legal(resultOf(game.gameStatus, mover))
    }

    override fun canMate(side: Side): Boolean {
        val color = if (side == Side.WHITE) ChessColor.WHITE else ChessColor.BLACK
        val types = game.currentPosition.pieces().map { it.second }.filter { it.color == color }.map { it.type }
        val minors = types.count { it == PieceType.KNIGHT || it == PieceType.BISHOP }
        val heavy = types.any { it == PieceType.PAWN || it == PieceType.ROOK || it == PieceType.QUEEN }
        return heavy || minors >= MIN_MATING_MINORS
    }

    private fun resultOf(status: GameStatus, mover: Side): GameResult? = when (status) {
        GameStatus.IN_PROGRESS, GameStatus.CHECK -> null
        GameStatus.CHECKMATE -> GameResult(mover, ResultReason.CHECKMATE)
        GameStatus.STALEMATE -> GameResult(null, ResultReason.STALEMATE)
        GameStatus.DRAW_FIFTY_MOVES -> GameResult(null, ResultReason.FIFTY_MOVES)
        GameStatus.DRAW_INSUFFICIENT_MATERIAL -> GameResult(null, ResultReason.INSUFFICIENT_MATERIAL)
        GameStatus.DRAW_THREEFOLD_REPETITION -> GameResult(null, ResultReason.THREEFOLD_REPETITION)
    }

    private fun ChessColor.toSide(): Side = if (this == ChessColor.WHITE) Side.WHITE else Side.BLACK

    private companion object {
        const val MIN_MATING_MINORS = 2
    }
}

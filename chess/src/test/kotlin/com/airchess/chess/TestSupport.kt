package com.airchess.chess

import org.junit.Assert.assertTrue

fun sq(name: String): ChessSquare = ChessSquare.fromAlgebraic(name)

/** Plays UCI moves, failing the test when one is rejected. */
fun ChessGame.play(vararg ucis: String) {
    for (uci in ucis) {
        val move = findMoveByUci(uci)
        assertTrue("Move $uci not legal in ${exportFen()}", move != null && makeMove(move))
    }
}

fun ChessGame.canMove(uci: String): Boolean = findMoveByUci(uci) != null

fun perft(position: ChessPosition, depth: Int): Long {
    val moves = ChessRules.legalMoves(position)
    if (depth == 1) {
        return moves.size.toLong()
    }
    return moves.sumOf { perft(ChessRules.applyMove(position, it), depth - 1) }
}

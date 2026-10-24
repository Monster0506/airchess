package com.airchess.chess

import org.junit.Assert.assertEquals
import org.junit.Test

class PerftTest {

    private val kiwipete = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"

    @Test
    fun startPosition() {
        val position = Fen.parse(Fen.START)
        assertEquals(20L, perft(position, 1))
        assertEquals(400L, perft(position, 2))
        assertEquals(8902L, perft(position, 3))
        assertEquals(197281L, perft(position, 4))
    }

    @Test
    fun kiwipete() {
        val position = Fen.parse(kiwipete)
        assertEquals(48L, perft(position, 1))
        assertEquals(2039L, perft(position, 2))
        assertEquals(97862L, perft(position, 3))
    }

    @Test
    fun enPassantAndPromotionHeavyPositions() {
        val endgame = Fen.parse("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1")
        assertEquals(14L, perft(endgame, 1))
        assertEquals(191L, perft(endgame, 2))
        assertEquals(2812L, perft(endgame, 3))
        assertEquals(43238L, perft(endgame, 4))

        val promotions = Fen.parse("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1")
        assertEquals(6L, perft(promotions, 1))
        assertEquals(264L, perft(promotions, 2))
        assertEquals(9467L, perft(promotions, 3))
    }
}

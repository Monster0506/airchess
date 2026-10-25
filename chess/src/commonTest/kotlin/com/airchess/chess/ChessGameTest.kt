package com.airchess.chess

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class ChessGameTest {

    private val whitePawn = ChessPiece(ChessColor.WHITE, PieceType.PAWN)
    private val blackPawn = ChessPiece(ChessColor.BLACK, PieceType.PAWN)

    @Test
    fun initialState() {
        val game = ChessGame()
        assertEquals(ChessColor.WHITE, game.currentTurn)
        assertEquals(GameStatus.IN_PROGRESS, game.gameStatus)
        assertEquals(20, game.allLegalMoves().size)
        assertEquals(Fen.START, game.exportFen())
        assertTrue(game.moveHistory.isEmpty())
    }

    @Test
    fun e2e4LegalAndE2e5Illegal() {
        val game = ChessGame()
        assertTrue(game.canMove("e2e4"))
        assertFalse(game.canMove("e2e5"))
        assertNull(game.move(sq("e2"), sq("e5")))
        val legal = game.findMoveByUci("e2e4")!!
        assertEquals(MoveKind.DOUBLE_PAWN_PUSH, legal.kind)
        assertFalse(game.makeMove(ChessMove(sq("e2"), sq("e5"), whitePawn)))
        assertEquals(Fen.START, game.exportFen())
    }

    @Test
    fun turnsAlternate() {
        val game = ChessGame()
        game.play("e2e4")
        assertEquals(ChessColor.BLACK, game.currentTurn)
        assertFalse(game.canMove("d2d4"))
        assertNull(game.move(sq("d2"), sq("d4")))
        game.play("e7e5")
        assertEquals(ChessColor.WHITE, game.currentTurn)
    }

    @Test
    fun slidersCannotJumpButKnightsCan() {
        val game = ChessGame()
        assertFalse(game.canMove("a1a3"))
        assertFalse(game.canMove("c1e3"))
        assertFalse(game.canMove("d1d3"))
        assertTrue(game.canMove("g1f3"))
        assertTrue(game.canMove("b1c3"))
        assertEquals(2, game.getLegalMoves(sq("g1")).size)
    }

    @Test
    fun captureUpdatesBoardAndRecordsPiece() {
        val game = ChessGame()
        game.play("e2e4", "d7d5", "e4d5")
        val record = game.moveHistory.last()
        assertEquals(blackPawn, record.move.captured)
        assertTrue(record.move.isCapture)
        assertEquals(whitePawn, game.currentPosition[sq("d5")])
        assertNull(game.currentPosition[sq("e4")])
        assertEquals(0, game.currentPosition.halfmoveClock)
    }

    @Test
    fun castleKingside() {
        val game = ChessGame("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        game.play("e1g1")
        val position = game.currentPosition
        assertEquals(ChessPiece(ChessColor.WHITE, PieceType.KING), position[sq("g1")])
        assertEquals(ChessPiece(ChessColor.WHITE, PieceType.ROOK), position[sq("f1")])
        assertNull(position[sq("e1")])
        assertNull(position[sq("h1")])
        assertEquals(ChessPiece(ChessColor.WHITE, PieceType.ROOK), position[sq("a1")])
        assertEquals(CastlingRights(false, false, true, true), position.castling)
        assertEquals(MoveKind.CASTLE_KINGSIDE, game.moveHistory.last().move.kind)
        assertEquals("O-O", game.moveHistory.last().san)
    }

    @Test
    fun castleQueenside() {
        val game = ChessGame("r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1")
        game.play("e8c8")
        val position = game.currentPosition
        assertEquals(ChessPiece(ChessColor.BLACK, PieceType.KING), position[sq("c8")])
        assertEquals(ChessPiece(ChessColor.BLACK, PieceType.ROOK), position[sq("d8")])
        assertNull(position[sq("a8")])
        assertNull(position[sq("e8")])
        assertEquals(CastlingRights(true, true, false, false), position.castling)
        assertEquals("O-O-O", game.moveHistory.last().san)
    }

    @Test
    fun castlingRightsLostWhenRookMovesOrIsCaptured() {
        val game = ChessGame("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        game.play("h1h8")
        assertEquals(CastlingRights(false, true, false, true), game.currentPosition.castling)

        val captured = ChessGame("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        captured.play("a1a8")
        assertEquals(CastlingRights(true, false, true, false), captured.currentPosition.castling)
    }

    @Test
    fun castlingRejectedThroughCheck() {
        val game = ChessGame("5r2/6k1/8/8/8/8/8/R3K2R w KQ - 0 1")
        assertFalse(game.canMove("e1g1"))
        assertTrue(game.canMove("e1c1"))
    }

    @Test
    fun castlingRejectedOutOfCheck() {
        val game = ChessGame("4r3/6k1/8/8/8/8/8/R3K2R w KQ - 0 1")
        assertEquals(GameStatus.CHECK, game.gameStatus)
        assertFalse(game.canMove("e1g1"))
        assertFalse(game.canMove("e1c1"))
    }

    @Test
    fun castlingRejectedIntoCheckButRookSquareMayBeAttacked() {
        val game = ChessGame("6r1/5k2/8/8/8/8/8/R3K2R w KQ - 0 1")
        assertFalse(game.canMove("e1g1"))
        assertTrue(game.canMove("e1c1"))

        val queenside = ChessGame("1r6/6k1/8/8/8/8/8/R3K2R w KQ - 0 1")
        assertTrue(queenside.canMove("e1c1"))
    }

    @Test
    fun castlingRejectedWhenBlockedOrWithoutRights() {
        assertFalse(ChessGame().canMove("e1g1"))
        assertFalse(ChessGame("r3k2r/8/8/8/8/8/8/R3K2R w - - 0 1").canMove("e1g1"))
        assertFalse(ChessGame("r3k2r/8/8/8/8/8/8/RN2K2R w KQkq - 0 1").canMove("e1c1"))
    }

    @Test
    fun enPassantWorksAndRemovesPawn() {
        val game = ChessGame()
        game.play("e2e4", "a7a6", "e4e5", "d7d5")
        assertEquals(sq("d6"), game.currentPosition.enPassant)
        val move = game.move(sq("e5"), sq("d6"))
        assertNotNull(move)
        assertEquals(MoveKind.EN_PASSANT, move!!.kind)
        assertEquals(sq("d5"), move.captureSquare)
        assertNull(game.currentPosition[sq("d5")])
        assertEquals(whitePawn, game.currentPosition[sq("d6")])
        assertEquals("exd6", game.moveHistory.last().san)
    }

    @Test
    fun enPassantExpiresAfterOneMove() {
        val game = ChessGame()
        game.play("e2e4", "a7a6", "e4e5", "d7d5", "a2a3", "h7h6")
        assertNull(game.currentPosition.enPassant)
        assertNull(game.move(sq("e5"), sq("d6")))
    }

    @Test
    fun promotionRequiresChoice() {
        val game = ChessGame("8/P6k/8/8/8/8/8/K7 w - - 0 1")
        assertNull(game.move(sq("a7"), sq("a8")))
        val moves = game.getLegalMoves(sq("a7"))
        assertEquals(4, moves.size)
        assertEquals(PieceType.PROMOTIONS.toSet(), moves.mapNotNull { it.promotion }.toSet())
    }

    @Test
    fun promotionToEachPiece() {
        for (type in PieceType.PROMOTIONS) {
            val game = ChessGame("8/P6k/8/8/8/8/8/K7 w - - 0 1")
            val move = game.move(sq("a7"), sq("a8"), type)
            assertNotNull(move)
            assertEquals(ChessPiece(ChessColor.WHITE, type), game.currentPosition[sq("a8")])
            assertNull(game.currentPosition[sq("a7")])
            assertEquals("a8=${type.symbol.uppercaseChar()}", game.moveHistory.last().san.removeSuffix("+"))
        }
    }

    @Test
    fun promotionByCaptureAndUnderPromotionUci() {
        val game = ChessGame("1n6/P6k/8/8/8/8/8/K7 w - - 0 1")
        game.play("a7b8n")
        assertEquals(ChessPiece(ChessColor.WHITE, PieceType.KNIGHT), game.currentPosition[sq("b8")])
        assertEquals(PieceType.KNIGHT, game.moveHistory.last().move.promotion)
        assertTrue(game.moveHistory.last().move.isCapture)
        assertFalse(ChessGame("1n6/P6k/8/8/8/8/8/K7 w - - 0 1").canMove("a7b8k"))
        assertFalse(ChessGame("1n6/P6k/8/8/8/8/8/K7 w - - 0 1").canMove("a7b8p"))
    }

    @Test
    fun blackPromotion() {
        val game = ChessGame("7k/8/8/8/8/8/p7/2K5 b - - 0 1")
        assertEquals(4, game.getLegalMoves(sq("a2")).size)
        game.play("a2a1q")
        assertEquals(ChessPiece(ChessColor.BLACK, PieceType.QUEEN), game.currentPosition[sq("a1")])
    }

    @Test
    fun cannotLeaveKingInCheck() {
        val game = ChessGame("4r2k/8/8/8/8/8/4B3/4K3 w - - 0 1")
        assertFalse(game.canMove("e2d3"))
        assertTrue(game.canMove("e1d1"))
        assertFalse(game.canMove("e1e2"))
    }

    @Test
    fun checkDetection() {
        val game = ChessGame()
        game.play("e2e4", "f7f6", "d1h5")
        assertEquals(GameStatus.CHECK, game.gameStatus)
        assertEquals("Qh5+", game.moveHistory.last().san)
    }

    @Test
    fun foolsMate() {
        val game = ChessGame()
        game.play("f2f3", "e7e5", "g2g4", "d8h4")
        assertEquals(GameStatus.CHECKMATE, game.gameStatus)
        assertEquals("Qh4#", game.moveHistory.last().san)
        assertTrue(game.allLegalMoves().isEmpty())
        assertFalse(game.canMove("a2a3"))
    }

    @Test
    fun stalemate() {
        val game = ChessGame("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertEquals(GameStatus.STALEMATE, game.gameStatus)
        assertTrue(game.gameStatus.isGameOver)
        assertTrue(game.allLegalMoves().isEmpty())

        val reached = ChessGame("7k/8/4Q1K1/8/8/8/8/8 w - - 0 1")
        reached.play("e6f7")
        assertEquals(GameStatus.STALEMATE, reached.gameStatus)
    }

    @Test
    fun fiftyMoveRule() {
        val game = ChessGame("3k4/8/8/8/8/8/4R3/4K3 w - - 98 80")
        game.play("e2a2")
        assertEquals(GameStatus.IN_PROGRESS, game.gameStatus)
        game.play("d8c8")
        assertEquals(GameStatus.DRAW_FIFTY_MOVES, game.gameStatus)
    }

    @Test
    fun insufficientMaterial() {
        assertEquals(GameStatus.DRAW_INSUFFICIENT_MATERIAL, ChessGame("4k3/8/8/8/8/8/8/4K3 w - - 0 1").gameStatus)
        assertEquals(GameStatus.DRAW_INSUFFICIENT_MATERIAL, ChessGame("4k3/8/8/8/8/8/8/3BK3 w - - 0 1").gameStatus)
        assertEquals(GameStatus.DRAW_INSUFFICIENT_MATERIAL, ChessGame("4k3/8/8/8/8/8/8/3NK3 w - - 0 1").gameStatus)
        assertEquals(GameStatus.DRAW_INSUFFICIENT_MATERIAL, ChessGame("4kb2/8/8/8/8/8/8/2B1K3 w - - 0 1").gameStatus)
        assertEquals(GameStatus.IN_PROGRESS, ChessGame("2b1k3/8/8/8/8/8/8/2B1K3 w - - 0 1").gameStatus)
        assertEquals(GameStatus.IN_PROGRESS, ChessGame("4k3/8/8/8/8/8/8/3RK3 w - - 0 1").gameStatus)
        assertEquals(GameStatus.IN_PROGRESS, ChessGame("4k3/p7/8/8/8/8/8/4K3 w - - 0 1").gameStatus)
        assertEquals(GameStatus.IN_PROGRESS, ChessGame("4k3/8/8/8/8/8/8/2NNK3 w - - 0 1").gameStatus)
    }

    @Test
    fun threefoldRepetition() {
        val game = ChessGame()
        val cycle = arrayOf("g1f3", "g8f6", "f3g1", "f6g8")
        game.play(*cycle)
        assertEquals(GameStatus.IN_PROGRESS, game.gameStatus)
        game.play(*cycle.sliceArray(0..2))
        assertEquals(GameStatus.IN_PROGRESS, game.gameStatus)
        game.play(cycle[3])
        assertEquals(GameStatus.DRAW_THREEFOLD_REPETITION, game.gameStatus)
        assertTrue(game.allLegalMoves().isEmpty())

        assertTrue(game.undoMove())
        assertEquals(GameStatus.IN_PROGRESS, game.gameStatus)
    }

    @Test
    fun selectSquareBehaviour() {
        val game = ChessGame()
        assertTrue(game.selectSquare(sq("e2")))
        assertEquals(sq("e2"), game.selectedSquare)
        assertEquals(setOf(sq("e3"), sq("e4")), game.legalMoves.map { it.to }.toSet())

        assertFalse(game.selectSquare(sq("e4")))
        assertEquals(sq("e2"), game.selectedSquare)
        assertEquals(Fen.START, game.exportFen())

        assertTrue(game.selectSquare(sq("g1")))
        assertEquals(sq("g1"), game.selectedSquare)
        assertFalse(game.selectSquare(sq("e5")))
        assertNull(game.selectedSquare)
        assertTrue(game.legalMoves.isEmpty())

        assertFalse(game.selectSquare(sq("e7")))
        assertNull(game.selectedSquare)
    }

    @Test
    fun makeMoveClearsSelection() {
        val game = ChessGame()
        game.selectSquare(sq("e2"))
        game.play("e2e4")
        assertNull(game.selectedSquare)
        assertTrue(game.legalMoves.isEmpty())
    }

    @Test
    fun clearSelectionAndReset() {
        val game = ChessGame()
        game.selectSquare(sq("e2"))
        game.clearSelection()
        assertNull(game.selectedSquare)
        game.play("e2e4")
        game.reset()
        assertEquals(Fen.START, game.exportFen())
        assertTrue(game.moveHistory.isEmpty())
        assertFalse(game.undoMove())
    }

    @Test
    fun attackedSquares() {
        val game = ChessGame()
        val white = game.attackedSquares(ChessColor.WHITE)
        assertTrue(sq("e3") in white)
        assertTrue(sq("f3") in white)
        assertTrue(sq("e2") in white)
        assertFalse(sq("e4") in white)
        val black = game.attackedSquares(ChessColor.BLACK)
        assertTrue(sq("e6") in black)
        assertFalse(sq("e5") in black)
    }

    @Test
    fun findMoveByUciRejectsGarbage() {
        val game = ChessGame()
        assertNull(game.findMoveByUci(""))
        assertNull(game.findMoveByUci("zz"))
        assertNull(game.findMoveByUci("e2e9"))
        assertEquals("e2e4", game.findMoveByUci("e2e4")!!.uci)
    }

    @Test
    fun undoRestoresFullState() {
        val game = ChessGame("r3k2r/1P6/8/3pP3/8/8/8/R3K2R w KQkq d6 0 1")
        val states = mutableListOf(snapshot(game))
        val moves = listOf("e5d6", "e8g8", "b7a8q", "g8g7", "e1c1", "f8f1")
        for (uci in moves) {
            game.play(uci)
            states.add(snapshot(game))
        }
        assertEquals(MoveKind.EN_PASSANT, game.moveHistory[0].move.kind)
        assertEquals(MoveKind.CASTLE_KINGSIDE, game.moveHistory[1].move.kind)
        assertEquals(PieceType.QUEEN, game.moveHistory[2].move.promotion)

        for (index in moves.indices.reversed()) {
            assertTrue(game.undoMove())
            assertEquals(states[index], snapshot(game))
        }
        assertFalse(game.undoMove())

        game.play(*moves.toTypedArray())
        assertEquals(states.last(), snapshot(game))
    }

    @Test
    fun loadFenReplacesGame() {
        val game = ChessGame()
        game.play("e2e4")
        game.loadFen("4k3/8/8/8/8/8/8/4K3 b - - 0 1")
        assertTrue(game.moveHistory.isEmpty())
        assertEquals(ChessColor.BLACK, game.currentTurn)
        assertFalse(game.undoMove())
    }

    private fun snapshot(game: ChessGame) = Triple(game.exportFen(), game.moveHistory, game.gameStatus)
}

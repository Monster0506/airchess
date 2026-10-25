package com.airchess.ar

import com.airchess.chess.ChessColor
import com.airchess.chess.ChessPiece
import com.airchess.chess.ChessPosition
import com.airchess.chess.ChessSquare
import com.airchess.chess.PieceType
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class PieceReconcilerTest {

    private val whitePawn = ChessPiece(ChessColor.WHITE, PieceType.PAWN)
    private val blackPawn = ChessPiece(ChessColor.BLACK, PieceType.PAWN)
    private val whiteKnight = ChessPiece(ChessColor.WHITE, PieceType.KNIGHT)
    private val whiteKing = ChessPiece(ChessColor.WHITE, PieceType.KING)
    private val whiteRook = ChessPiece(ChessColor.WHITE, PieceType.ROOK)
    private val whiteQueen = ChessPiece(ChessColor.WHITE, PieceType.QUEEN)
    private val blackRook = ChessPiece(ChessColor.BLACK, PieceType.ROOK)

    private fun sq(name: String) = ChessSquare.fromAlgebraic(name)

    private fun position(vararg pieces: Pair<String, ChessPiece>): ChessPosition {
        val board = MutableList<ChessPiece?>(ChessSquare.COUNT) { null }
        pieces.forEach { (name, piece) -> board[sq(name).index] = piece }
        return ChessPosition.INITIAL.copy(board = board)
    }

    private fun ChessPosition.edit(vararg changes: Pair<String, ChessPiece?>): ChessPosition {
        val board = board.toMutableList()
        changes.forEach { (name, piece) -> board[sq(name).index] = piece }
        return copy(board = board)
    }

    private fun PieceReconciler.idAt(name: String): Int =
        pieces.entries.single { it.value.square == sq(name) }.key

    @Test
    fun firstPositionSpawnsEverything() {
        val reconciler = PieceReconciler()

        val transitions = reconciler.update(ChessPosition.INITIAL)

        assertEquals(32, transitions.size)
        assertTrue(transitions.all { it.from == null && it.to != null })
        assertEquals(32, transitions.map { it.id }.toSet().size)
    }

    @Test
    fun identicalPositionProducesNoTransitions() {
        val reconciler = PieceReconciler()
        reconciler.update(ChessPosition.INITIAL)

        assertTrue(reconciler.update(ChessPosition.INITIAL).isEmpty())
    }

    @Test
    fun simpleMoveKeepsId() {
        val reconciler = PieceReconciler()
        reconciler.update(ChessPosition.INITIAL)
        val id = reconciler.idAt("e2")

        val next = ChessPosition.INITIAL.edit("e2" to null, "e4" to whitePawn)
        val transitions = reconciler.update(next)

        assertEquals(listOf(PieceTransition(id, sq("e2"), sq("e4"), whitePawn)), transitions)
        assertEquals(id, reconciler.idAt("e4"))
    }

    @Test
    fun captureRemovesVictimAndMovesAttacker() {
        val start = position("d4" to whitePawn, "e5" to blackPawn, "a1" to whiteKing)
        val reconciler = PieceReconciler()
        reconciler.update(start)
        val attacker = reconciler.idAt("d4")
        val victim = reconciler.idAt("e5")

        val transitions = reconciler.update(start.edit("d4" to null, "e5" to whitePawn))

        assertEquals(2, transitions.size)
        assertTrue(PieceTransition(attacker, sq("d4"), sq("e5"), whitePawn) in transitions)
        assertTrue(PieceTransition(victim, sq("e5"), null, blackPawn) in transitions)
        assertEquals(attacker, reconciler.idAt("e5"))
        assertTrue(victim !in reconciler.pieces)
    }

    @Test
    fun castlingMovesKingAndRook() {
        val start = position("e1" to whiteKing, "h1" to whiteRook, "a1" to whiteRook)
        val reconciler = PieceReconciler()
        reconciler.update(start)
        val king = reconciler.idAt("e1")
        val rook = reconciler.idAt("h1")
        val otherRook = reconciler.idAt("a1")

        val transitions = reconciler.update(start.edit("e1" to null, "h1" to null, "g1" to whiteKing, "f1" to whiteRook))

        assertEquals(2, transitions.size)
        assertTrue(PieceTransition(king, sq("e1"), sq("g1"), whiteKing) in transitions)
        assertTrue(PieceTransition(rook, sq("h1"), sq("f1"), whiteRook) in transitions)
        assertEquals(otherRook, reconciler.idAt("a1"))
    }

    @Test
    fun enPassantRemovesTheCapturedPawnNotTheDestination() {
        val start = position("e5" to whitePawn, "d5" to blackPawn, "a1" to whiteKing)
        val reconciler = PieceReconciler()
        reconciler.update(start)
        val attacker = reconciler.idAt("e5")
        val victim = reconciler.idAt("d5")

        val transitions = reconciler.update(start.edit("e5" to null, "d5" to null, "d6" to whitePawn))

        assertEquals(2, transitions.size)
        assertTrue(PieceTransition(attacker, sq("e5"), sq("d6"), whitePawn) in transitions)
        assertTrue(PieceTransition(victim, sq("d5"), null, blackPawn) in transitions)
    }

    @Test
    fun promotionTravelsPawnThenChangesType() {
        val start = position("e7" to whitePawn, "a1" to whiteKing)
        val reconciler = PieceReconciler()
        reconciler.update(start)
        val pawn = reconciler.idAt("e7")

        val transitions = reconciler.update(start.edit("e7" to null, "e8" to whiteQueen))

        assertEquals(
            listOf(PieceTransition(pawn, sq("e7"), sq("e8"), whiteQueen, promotedFrom = whitePawn)),
            transitions,
        )
        assertEquals(pawn, reconciler.idAt("e8"))
        assertEquals(whiteQueen, reconciler.pieces.getValue(pawn).piece)
    }

    @Test
    fun capturePromotionRemovesVictimAndPromotesPawn() {
        val start = position("e7" to whitePawn, "d8" to blackRook, "a1" to whiteKing)
        val reconciler = PieceReconciler()
        reconciler.update(start)
        val pawn = reconciler.idAt("e7")
        val victim = reconciler.idAt("d8")

        val transitions = reconciler.update(start.edit("e7" to null, "d8" to whiteQueen))

        assertEquals(2, transitions.size)
        assertTrue(PieceTransition(pawn, sq("e7"), sq("d8"), whiteQueen, promotedFrom = whitePawn) in transitions)
        assertTrue(PieceTransition(victim, sq("d8"), null, blackRook) in transitions)
    }

    @Test
    fun blackPromotionUsesFirstRank() {
        val start = position("b2" to blackPawn, "a8" to whiteKing)
        val reconciler = PieceReconciler()
        reconciler.update(start)
        val pawn = reconciler.idAt("b2")
        val blackQueen = ChessPiece(ChessColor.BLACK, PieceType.QUEEN)

        val transitions = reconciler.update(start.edit("b2" to null, "b1" to blackQueen))

        assertEquals(listOf(PieceTransition(pawn, sq("b2"), sq("b1"), blackQueen, blackPawn)), transitions)
    }

    @Test
    fun undoOfCaptureRespawnsVictimWithNewId() {
        val before = position("d4" to whitePawn, "e5" to blackPawn, "a1" to whiteKing)
        val after = before.edit("d4" to null, "e5" to whitePawn)
        val reconciler = PieceReconciler()
        reconciler.update(before)
        reconciler.update(after)
        val attacker = reconciler.idAt("e5")

        val transitions = reconciler.update(before)

        assertEquals(2, transitions.size)
        assertTrue(PieceTransition(attacker, sq("e5"), sq("d4"), whitePawn) in transitions)
        val spawn = transitions.single { it.from == null }
        assertEquals(sq("e5"), spawn.to)
        assertEquals(blackPawn, spawn.piece)
        assertTrue(spawn.id !in listOf(attacker))
        assertEquals(3, reconciler.pieces.size)
    }

    @Test
    fun undoOfPromotionRemovesPieceAndSpawnsPawn() {
        val before = position("e7" to whitePawn, "a1" to whiteKing)
        val after = before.edit("e7" to null, "e8" to whiteQueen)
        val reconciler = PieceReconciler()
        reconciler.update(before)
        reconciler.update(after)
        val queenId = reconciler.idAt("e8")

        val transitions = reconciler.update(before)

        assertEquals(2, transitions.size)
        assertTrue(PieceTransition(queenId, sq("e8"), null, whiteQueen) in transitions)
        assertEquals(sq("e7"), transitions.single { it.from == null }.to)
    }

    @Test
    fun resetFromMidGameJumpsInsteadOfSliding() {
        val reconciler = PieceReconciler()
        reconciler.update(ChessPosition.INITIAL)
        val midGame = ChessPosition.INITIAL
            .edit("e2" to null, "e4" to whitePawn)
            .edit("d7" to null, "d5" to blackPawn)
            .edit("g1" to null, "f3" to whiteKnight)
            .edit("e4" to null, "d5" to whitePawn)
            .edit("a2" to null, "a3" to whitePawn)
        reconciler.update(midGame)
        val untouched = reconciler.idAt("b2")

        val transitions = reconciler.update(ChessPosition.INITIAL)

        assertTrue(transitions.none { it.from != null && it.to != null })
        assertTrue(transitions.any { it.from == null && it.to == sq("e2") })
        assertTrue(transitions.any { it.from == null && it.to == sq("g1") })
        assertEquals(untouched, reconciler.idAt("b2"))
        assertEquals(32, reconciler.pieces.size)
        assertNull(reconciler.pieces.values.firstOrNull { it.square == sq("d5") })
    }

    @Test
    fun ambiguousSameKindMovesPickNearestDestination() {
        val start = position("a1" to whiteRook, "h1" to whiteRook, "e1" to whiteKing)
        val reconciler = PieceReconciler()
        reconciler.update(start)
        val left = reconciler.idAt("a1")
        val right = reconciler.idAt("h1")

        reconciler.update(start.edit("a1" to null, "h1" to null, "a4" to whiteRook, "h4" to whiteRook))

        assertEquals(left, reconciler.idAt("a4"))
        assertEquals(right, reconciler.idAt("h4"))
    }
}

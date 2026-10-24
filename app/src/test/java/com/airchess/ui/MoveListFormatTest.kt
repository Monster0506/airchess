package com.airchess.ui

import com.airchess.chess.ChessColor
import com.airchess.chess.ChessMove
import com.airchess.chess.ChessPiece
import com.airchess.chess.ChessSquare
import com.airchess.chess.MoveRecord
import com.airchess.chess.PieceType
import org.junit.Assert.assertEquals
import org.junit.Test

class MoveListFormatTest {

    private fun record(san: String, color: ChessColor = ChessColor.WHITE): MoveRecord {
        val move = ChessMove(
            from = ChessSquare.fromAlgebraic("e2"),
            to = ChessSquare.fromAlgebraic("e4"),
            piece = ChessPiece(color, PieceType.PAWN),
        )
        return MoveRecord(move, san)
    }

    @Test
    fun emptyRecordsProduceNoLines() {
        assertEquals(emptyList<String>(), formatMoveList(emptyList()))
        assertEquals(emptyList<String>(), formatMoveList(emptyList(), startsWithBlack = true))
    }

    @Test
    fun oddCountEndsWithWhiteOnlyLine() {
        val records = listOf(
            record("e4"),
            record("e5", ChessColor.BLACK),
            record("Nf3"),
            record("Nc6", ChessColor.BLACK),
            record("Bc4"),
        )
        assertEquals(
            listOf("1. e4 e5", "2. Nf3 Nc6", "3. Bc4"),
            formatMoveList(records),
        )
    }

    @Test
    fun evenCountPairsEveryLine() {
        val records = listOf(
            record("e4"),
            record("e5", ChessColor.BLACK),
            record("Nf3"),
            record("Nc6", ChessColor.BLACK),
        )
        assertEquals(listOf("1. e4 e5", "2. Nf3 Nc6"), formatMoveList(records))
    }

    @Test
    fun startsWithBlackPrefixesFirstLineWithEllipsis() {
        val records = listOf(
            record("e5", ChessColor.BLACK),
            record("Nf3"),
            record("Nc6", ChessColor.BLACK),
            record("Bc4"),
        )
        assertEquals(
            listOf("1... e5", "2. Nf3 Nc6", "3. Bc4"),
            formatMoveList(records, startsWithBlack = true),
        )
    }

    @Test
    fun startsWithBlackSingleMove() {
        assertEquals(
            listOf("1... e5"),
            formatMoveList(listOf(record("e5", ChessColor.BLACK)), startsWithBlack = true),
        )
    }
}

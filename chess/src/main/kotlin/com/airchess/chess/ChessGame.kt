package com.airchess.chess

/**
 * Mutable game session over [ChessRules]. Once the status is game over (mate, stalemate or an
 * automatic draw) no further moves are legal until [undoMove], [reset] or [loadFen].
 */
class ChessGame(fen: String = Fen.START) {

    private class Snapshot(
        val position: ChessPosition,
        val legal: List<ChessMove>,
        val status: GameStatus,
        val key: String,
    )

    private val snapshots = ArrayList<Snapshot>()
    private val history = ArrayList<MoveRecord>()

    var selectedSquare: ChessSquare? = null
        private set

    var legalMoves: List<ChessMove> = emptyList()
        private set

    val currentPosition: ChessPosition
        get() = snapshots.last().position

    val currentTurn: ChessColor
        get() = currentPosition.sideToMove

    val moveHistory: List<MoveRecord>
        get() = history.toList()

    val gameStatus: GameStatus
        get() = snapshots.last().status

    init {
        loadFen(fen)
    }

    fun loadFen(fen: String) {
        val position = Fen.parse(fen)
        snapshots.clear()
        history.clear()
        clearSelection()
        snapshots.add(snapshotOf(position))
    }

    fun reset() = loadFen(Fen.START)

    fun exportFen(): String = Fen.export(currentPosition)

    /**
     * Selects a piece of the side to move. A tap on a destination of the current selection
     * is not played here: it returns false and keeps the selection so the caller decides.
     */
    fun selectSquare(square: ChessSquare): Boolean {
        val piece = currentPosition[square]
        if (piece != null && piece.color == currentTurn && !gameStatus.isGameOver) {
            selectedSquare = square
            legalMoves = getLegalMoves(square)
            return true
        }
        if (selectedSquare != null && legalMoves.any { it.to == square }) {
            return false
        }
        clearSelection()
        return false
    }

    fun clearSelection() {
        selectedSquare = null
        legalMoves = emptyList()
    }

    fun getLegalMoves(square: ChessSquare): List<ChessMove> = allLegalMoves().filter { it.from == square }

    fun allLegalMoves(): List<ChessMove> {
        val snapshot = snapshots.last()
        return if (snapshot.status.isGameOver) emptyList() else snapshot.legal
    }

    fun findMoveByUci(uci: String): ChessMove? = allLegalMoves().firstOrNull { it.uci == uci.lowercase() }

    fun move(from: ChessSquare, to: ChessSquare, promotion: PieceType? = null): ChessMove? {
        val match = allLegalMoves().firstOrNull { it.from == from && it.to == to && it.promotion == promotion }
            ?: return null
        play(match)
        return match
    }

    fun makeMove(move: ChessMove): Boolean {
        val match = allLegalMoves().firstOrNull {
            it.from == move.from && it.to == move.to && it.promotion == move.promotion
        } ?: return false
        play(match)
        return true
    }

    fun undoMove(): Boolean {
        if (history.isEmpty()) {
            return false
        }
        history.removeAt(history.lastIndex)
        snapshots.removeAt(snapshots.lastIndex)
        clearSelection()
        return true
    }

    fun attackedSquares(by: ChessColor): Set<ChessSquare> = ChessRules.attackedSquares(currentPosition, by)

    private fun play(move: ChessMove) {
        val before = currentPosition
        val san = San.format(before, move)
        snapshots.add(snapshotOf(ChessRules.applyMove(before, move)))
        history.add(MoveRecord(move, san))
        clearSelection()
    }

    private fun snapshotOf(position: ChessPosition): Snapshot {
        val legal = ChessRules.legalMoves(position)
        val key = ChessRules.repetitionKey(position, legal)
        val repetitions = 1 + snapshots.count { it.key == key }
        return Snapshot(position, legal, ChessRules.status(position, legal, repetitions), key)
    }
}

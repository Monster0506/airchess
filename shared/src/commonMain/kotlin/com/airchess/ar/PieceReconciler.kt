package com.airchess.ar

import com.airchess.chess.ChessColor
import com.airchess.chess.ChessPiece
import com.airchess.chess.ChessPosition
import com.airchess.chess.ChessSquare
import com.airchess.chess.PieceType

/** A piece the renderer currently shows, identified by a stable render id. */
data class TrackedPiece(val square: ChessSquare, val piece: ChessPiece)

/**
 * One change between two positions, in render terms.
 *
 * - move: [from] and [to] set; [promotedFrom] set when a pawn travels and then becomes [piece]
 * - spawn: [from] null (undo of a capture, promotion piece without pawn, reset, first position)
 * - remove: [to] null (captured piece)
 */
data class PieceTransition(
    val id: Int,
    val from: ChessSquare?,
    val to: ChessSquare?,
    val piece: ChessPiece,
    val promotedFrom: ChessPiece? = null,
)

/**
 * Keeps stable render ids across positions so the renderer can animate instead of rebuild.
 * Pure Kotlin: the board position is the only input.
 */
class PieceReconciler {

    private var nextId = 0
    private var tracked: Map<Int, TrackedPiece> = emptyMap()

    val pieces: Map<Int, TrackedPiece>
        get() = tracked

    /** Moves to [position] and returns what changed; unchanged pieces produce no transition. */
    fun update(position: ChessPosition): List<PieceTransition> {
        val result = reconcile(tracked, position, nextId)
        tracked = result.pieces
        nextId = result.nextId
        return result.transitions
    }

    data class Result(
        val pieces: Map<Int, TrackedPiece>,
        val transitions: List<PieceTransition>,
        val nextId: Int,
    )

    companion object {
        /** More matched moves than this means a jump (reset, undo, FEN load): no sliding. */
        const val MAX_ANIMATED_MOVES = 2

        /** Pure reconciliation of [previous] against [position]; new ids start at [firstNewId]. */
        fun reconcile(
            previous: Map<Int, TrackedPiece>,
            position: ChessPosition,
            firstNewId: Int,
        ): Result {
            val board = occupancy(position)
            val matched = HashSet<Int>()
            val covered = HashSet<ChessSquare>()

            previous.forEach { (id, tracked) ->
                if (board[tracked.square] == tracked.piece) {
                    matched += id
                    covered += tracked.square
                }
            }

            val disappeared = previous.filterKeys { it !in matched }
            val appeared = board.filterKeys { it !in covered }

            val moves = matchMoves(disappeared, appeared)
            val animated = if (moves.size <= MAX_ANIMATED_MOVES) moves else emptyList()
            val claimedIds = animated.mapTo(HashSet()) { it.first }
            val claimedSquares = animated.mapTo(HashSet()) { it.second }

            val leftIds = disappeared.keys.filterTo(ArrayList()) { it !in claimedIds }.sorted()
            val leftSquares = appeared.keys.filterTo(ArrayList()) { it !in claimedSquares }
                .sortedBy { it.index }

            val promotions = if (moves.size > MAX_ANIMATED_MOVES) {
                emptyList()
            } else {
                matchPromotions(leftIds, leftSquares, previous, board)
            }
            val promotedIds = promotions.mapTo(HashSet()) { it.id }
            val promotedSquares = promotions.mapNotNullTo(HashSet()) { it.to }

            val transitions = ArrayList<PieceTransition>()
            val next = LinkedHashMap<Int, TrackedPiece>()
            var nextId = firstNewId

            previous.forEach { (id, tracked) ->
                if (id in matched) {
                    next[id] = tracked
                }
            }

            animated.forEach { (id, square) ->
                val piece = previous.getValue(id).piece
                transitions += PieceTransition(id, previous.getValue(id).square, square, piece)
                next[id] = TrackedPiece(square, piece)
            }

            promotions.forEach { promotion ->
                transitions += promotion
                next[promotion.id] = TrackedPiece(promotion.to!!, promotion.piece)
            }

            leftIds.filter { it !in promotedIds }.forEach { id ->
                val tracked = previous.getValue(id)
                transitions += PieceTransition(id, tracked.square, null, tracked.piece)
            }

            leftSquares.filter { it !in promotedSquares }.forEach { square ->
                val piece = board.getValue(square)
                val id = nextId++
                transitions += PieceTransition(id, null, square, piece)
                next[id] = TrackedPiece(square, piece)
            }

            return Result(next, transitions, nextId)
        }

        private fun occupancy(position: ChessPosition): Map<ChessSquare, ChessPiece> {
            val board = LinkedHashMap<ChessSquare, ChessPiece>()
            ChessSquare.ALL.forEach { square ->
                position[square]?.let { board[square] = it }
            }
            return board
        }

        /** Greedy minimum-distance pairing of vanished and new squares, per piece kind. */
        private fun matchMoves(
            disappeared: Map<Int, TrackedPiece>,
            appeared: Map<ChessSquare, ChessPiece>,
        ): List<Pair<Int, ChessSquare>> {
            val result = ArrayList<Pair<Int, ChessSquare>>()
            val kinds = disappeared.values.map { it.piece }.distinct()

            kinds.forEach { kind ->
                val ids = disappeared.filterValues { it.piece == kind }.keys.sorted()
                val squares = appeared.filterValues { it == kind }.keys.sortedBy { it.index }

                val candidates = ids.flatMap { id ->
                    squares.map { square -> Triple(id, square, distance(disappeared.getValue(id).square, square)) }
                }.sortedWith(compareBy({ it.third }, { it.first }, { it.second.index }))

                val usedIds = HashSet<Int>()
                val usedSquares = HashSet<ChessSquare>()
                candidates.forEach { (id, square, _) ->
                    if (id in usedIds || square in usedSquares) {
                        return@forEach
                    }
                    usedIds += id
                    usedSquares += square
                    result += id to square
                }
            }

            return result
        }

        /** A vanished pawn next to a new piece on its last rank travels there, then changes type. */
        private fun matchPromotions(
            ids: List<Int>,
            squares: List<ChessSquare>,
            previous: Map<Int, TrackedPiece>,
            board: Map<ChessSquare, ChessPiece>,
        ): List<PieceTransition> {
            val result = ArrayList<PieceTransition>()
            val usedSquares = HashSet<ChessSquare>()

            ids.forEach { id ->
                val tracked = previous.getValue(id)
                if (tracked.piece.type != PieceType.PAWN) {
                    return@forEach
                }

                val color = tracked.piece.color
                val target = squares
                    .filter { it !in usedSquares }
                    .filter { square ->
                        val piece = board.getValue(square)
                        piece.color == color &&
                            piece.type != PieceType.PAWN &&
                            square.rank == lastRank(color) &&
                            tracked.square.rank == lastRank(color) - forward(color) &&
                            kotlin.math.abs(square.file - tracked.square.file) <= 1
                    }
                    .minByOrNull { distance(tracked.square, it) }
                    ?: return@forEach

                usedSquares += target
                result += PieceTransition(
                    id = id,
                    from = tracked.square,
                    to = target,
                    piece = board.getValue(target),
                    promotedFrom = tracked.piece,
                )
            }

            return result
        }

        private fun lastRank(color: ChessColor): Int =
            if (color == ChessColor.WHITE) ChessSquare.BOARD_SIZE - 1 else 0

        private fun forward(color: ChessColor): Int = if (color == ChessColor.WHITE) 1 else -1

        private fun distance(a: ChessSquare, b: ChessSquare): Int {
            val dFile = a.file - b.file
            val dRank = a.rank - b.rank
            return dFile * dFile + dRank * dRank
        }
    }
}

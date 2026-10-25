package com.airchess.ios

import com.airchess.ar.ArStatusTracker
import com.airchess.ar.PieceReconciler
import com.airchess.chess.ChessColor
import com.airchess.chess.ChessSquare
import com.airchess.model.ArStatus
import com.airchess.model.BoardViewState
import com.airchess.viewmodel.ChessViewModel

/**
 * Turns board states into [BoardFrame]s. The reconciler keeps piece ids stable across
 * positions, so Swift can slide a piece instead of rebuilding it. One instance per live
 * board view: its first frame is never animated.
 */
internal class BoardFrameBuilder {
    private val reconciler = PieceReconciler()
    private var rendered = false

    fun build(board: BoardViewState): BoardFrame {
        reconciler.update(board.position)

        val pieces = reconciler.pieces
            .map { (id, tracked) ->
                PieceFrame(id, tracked.square.index, tracked.piece.type.toKind(), tracked.piece.color.toSide())
            }
            .sortedBy { it.id }

        val marks = marksOf(board).map { (square, kind) -> MarkFrame(square.index, kind) }
        val destinations = board.legalMoves
            .distinctBy { it.to }
            .map { DestinationFrame(it.to.index, it.isCapture) }

        val frame = BoardFrame(
            pieces = pieces,
            marks = marks,
            destinations = destinations,
            hintFrom = board.hint?.from?.index ?: NO_SQUARE,
            hintTo = board.hint?.to?.index ?: NO_SQUARE,
            perspective = board.perspective.toSide(),
            animate = rendered,
        )
        rendered = true
        return frame
    }

    /** Highest priority first; a square keeps the first kind that claims it. */
    private fun marksOf(board: BoardViewState): Map<ChessSquare, MarkKind> {
        val result = LinkedHashMap<ChessSquare, MarkKind>()

        fun put(square: ChessSquare?, kind: MarkKind) {
            if (square != null && square !in result) {
                result[square] = kind
            }
        }

        put(board.selectedSquare, MarkKind.SELECTED)
        put(board.checkedKingSquare, MarkKind.CHECK)
        board.hint?.let {
            put(it.from, MarkKind.HINT)
            put(it.to, MarkKind.HINT)
        }
        board.lastMove?.let {
            put(it.from, MarkKind.LAST_MOVE)
            put(it.to, MarkKind.LAST_MOVE)
        }
        board.threatenedSquares.forEach { put(it, MarkKind.THREATENED) }

        return result
    }

    private fun ChessColor.toSide(): PieceSide = if (this == ChessColor.WHITE) PieceSide.WHITE else PieceSide.BLACK
}

/** Feeds one [BoardController] from the view model's board state, skipping unchanged states. */
internal class BoardPresenter(private val controller: BoardController) {
    private val builder = BoardFrameBuilder()
    private var last: BoardViewState? = null

    fun present(board: BoardViewState) {
        if (board == last) {
            return
        }

        last = board
        controller.render(builder.build(board))
    }
}

/** Receives Swift's board events and applies them to the view model. */
internal class ViewModelBoardListener(
    private val viewModel: ChessViewModel,
    private val onFailed: (String) -> Unit,
) : BoardListener {
    private val tracker = ArStatusTracker(emit = { status: ArStatus -> viewModel.onArStatus(status) })

    override fun onSquareTapped(square: Int) {
        viewModel.onSquareTapped(ChessSquare.of(square))
    }

    override fun onArFrame(
        timestampNanos: Long,
        cameraTracking: Boolean,
        failure: String?,
        planeCount: Int,
        anchorState: String,
        anchored: Boolean,
    ) {
        tracker.onFrame(timestampNanos, cameraTracking, failure, planeCount, anchorState, anchored)
    }

    override fun onSessionFailed(message: String) {
        onFailed(message)
    }
}

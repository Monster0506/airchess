package com.airchess.model

import com.airchess.chess.ChessColor
import com.airchess.chess.ChessMove
import com.airchess.chess.ChessPosition
import com.airchess.chess.ChessSquare

/** Everything the 3D/AR board needs to render. Derived from ChessGame; never authoritative. */
data class BoardViewState(
    val position: ChessPosition = ChessPosition.INITIAL,
    val selectedSquare: ChessSquare? = null,
    val legalMoves: List<ChessMove> = emptyList(),
    val lastMove: ChessMove? = null,
    val hint: ChessMove? = null,
    val threatenedSquares: Set<ChessSquare> = emptySet(),
    val checkedKingSquare: ChessSquare? = null,
    /** Side the viewer plays; the board is shown with that side's pieces nearest. */
    val perspective: ChessColor = ChessColor.WHITE,
)

enum class TrackingStatus {
    INITIALIZING,
    SEARCHING,
    PLANE_FOUND,
    PLACED,
    LOST,
}

/** AR session diagnostics, reported by the AR layer for the overlay and debug panel. */
data class ArStatus(
    val tracking: TrackingStatus = TrackingStatus.INITIALIZING,
    val trackingFailure: String? = null,
    val planeCount: Int = 0,
    val anchorState: String = "none",
    val fps: Int = 0,
)

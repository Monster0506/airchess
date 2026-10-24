package com.airchess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airchess.ar.ArGate
import com.airchess.chess.ChessMove
import com.airchess.chess.ChessSquare
import com.airchess.model.ArStatus
import com.airchess.model.BoardViewState

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                ArDemoHarness()
            }
        }
    }
}

/**
 * TEMPORARY demo harness so the AR layer runs standalone. Moves a piece from the selected square
 * to the tapped one with no chess rules at all; the ViewModel slice replaces this wiring.
 */
@Composable
private fun ArDemoHarness() {
    var board by remember { mutableStateOf(BoardViewState()) }
    var repositionToken by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf(ArStatus()) }

    Box(Modifier.fillMaxSize()) {
        ArGate(
            board = board,
            repositionToken = repositionToken,
            onSquareTapped = { board = demoTap(board, it) },
            onStatusChanged = { status = it },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("${status.tracking} planes=${status.planeCount} anchor=${status.anchorState} fps=${status.fps}")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton({ repositionToken++ }) { Text("Reposition") }
                TextButton({ board = board.copy(perspective = board.perspective.opposite) }) { Text("Flip") }
                TextButton({ board = demoHint(board) }) { Text("Hint") }
                TextButton({ board = BoardViewState(perspective = board.perspective) }) { Text("Reset") }
            }
        }
    }
}

private val NEIGHBOURS = listOf(-1 to -1, 0 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 0 to 1, 1 to 1)

private fun demoTap(board: BoardViewState, square: ChessSquare): BoardViewState {
    val position = board.position
    val selected = board.selectedSquare
    val tappedPiece = position[square]

    if (selected == null || selected == square) {
        if (tappedPiece == null || selected == square) {
            return board.copy(selectedSquare = null, legalMoves = emptyList())
        }
        val moves = NEIGHBOURS.mapNotNull { (df, dr) -> square.offset(df, dr) }.map {
            ChessMove(square, it, tappedPiece, captured = position[it])
        }
        return board.copy(selectedSquare = square, legalMoves = moves)
    }

    val piece = position[selected] ?: return board.copy(selectedSquare = null, legalMoves = emptyList())
    val cells = position.board.toMutableList()
    cells[selected.index] = null
    cells[square.index] = piece
    val move = ChessMove(selected, square, piece, captured = tappedPiece)

    return board.copy(
        position = position.copy(board = cells, sideToMove = position.sideToMove.opposite),
        selectedSquare = null,
        legalMoves = emptyList(),
        lastMove = move,
        hint = null,
    )
}

private fun demoHint(board: BoardViewState): BoardViewState {
    if (board.hint != null) {
        return board.copy(hint = null)
    }
    val from = ChessSquare.fromAlgebraic("e2")
    val to = ChessSquare.fromAlgebraic("e4")
    val piece = board.position[from] ?: return board
    return board.copy(hint = ChessMove(from, to, piece))
}

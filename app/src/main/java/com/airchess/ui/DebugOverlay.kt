package com.airchess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airchess.chess.ChessSquare
import com.airchess.model.ArStatus
import com.airchess.model.TrackingStatus

@Composable
fun DebugOverlay(
    status: ArStatus,
    selected: ChessSquare?,
    fen: String,
    modifier: Modifier = Modifier,
) {
    val lines = listOf(
        "tracking: ${status.tracking} (${status.trackingFailure ?: "ok"})",
        "planes: ${status.planeCount}",
        "anchor: ${status.anchorState}",
        "fps: ${status.fps}",
        "selected: ${selected?.algebraic ?: "-"}",
        "fen: $fen",
    )
    Column(
        modifier = modifier
            .widthIn(max = 280.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        lines.forEach { line ->
            Text(
                text = line,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF888888)
@Composable
private fun DebugOverlayPreview() {
    DebugOverlay(
        status = ArStatus(
            tracking = TrackingStatus.PLACED,
            trackingFailure = null,
            planeCount = 2,
            anchorState = "placed",
            fps = 60,
        ),
        selected = ChessSquare.fromAlgebraic("e2"),
        fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
    )
}

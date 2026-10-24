package com.airchess.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airchess.chess.ChessColor
import com.airchess.chess.PieceType

@Composable
fun PromotionDialog(
    color: ChessColor,
    onChoose: (PieceType) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Promote pawn") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                PieceType.PROMOTIONS.forEach { type ->
                    PromotionOption(
                        glyph = pieceGlyph(color, type),
                        label = promotionLabel(type),
                        onClick = { onChoose(type) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun PromotionOption(glyph: String, label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(84.dp),
        contentPadding = PaddingValues(4.dp),
        colors = ButtonDefaults.filledTonalButtonColors(),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = glyph, fontSize = 40.sp)
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun pieceGlyph(color: ChessColor, type: PieceType): String {
    val white = color == ChessColor.WHITE
    val code = when (type) {
        PieceType.KING -> if (white) '\u2654' else '\u265A'
        PieceType.QUEEN -> if (white) '\u2655' else '\u265B'
        PieceType.ROOK -> if (white) '\u2656' else '\u265C'
        PieceType.BISHOP -> if (white) '\u2657' else '\u265D'
        PieceType.KNIGHT -> if (white) '\u2658' else '\u265E'
        PieceType.PAWN -> if (white) '\u2659' else '\u265F'
    }
    return code.toString()
}

private fun promotionLabel(type: PieceType): String = when (type) {
    PieceType.QUEEN -> "Queen"
    PieceType.ROOK -> "Rook"
    PieceType.BISHOP -> "Bishop"
    PieceType.KNIGHT -> "Knight"
    PieceType.PAWN -> "Pawn"
    PieceType.KING -> "King"
}

@Preview(showBackground = true)
@Composable
private fun PromotionDialogPreview() {
    MaterialTheme {
        PromotionDialog(color = ChessColor.WHITE, onChoose = {}, onDismiss = {})
    }
}

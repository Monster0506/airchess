package com.airchess.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airchess.chess.ChessColor
import com.airchess.chess.MoveRecord

/**
 * Formats SAN move records as numbered lines, two plies per line: `1. e4 e5`, `2. Nf3 Nc6`, `3. Bc4`.
 * When [startsWithBlack] is true, the first record is Black's move and is rendered as `1... e5`.
 */
fun formatMoveList(records: List<MoveRecord>, startsWithBlack: Boolean = false): List<String> {
    val lines = mutableListOf<String>()
    var index = 0
    var moveNumber = 1
    if (startsWithBlack && records.isNotEmpty()) {
        lines += "1... ${records[0].san}"
        index = 1
        moveNumber = 2
    }
    while (index < records.size) {
        val white = records[index].san
        val black = records.getOrNull(index + 1)?.san
        lines += if (black != null) {
            "$moveNumber. $white $black"
        } else {
            "$moveNumber. $white"
        }
        index += 2
        moveNumber++
    }
    return lines
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveHistorySheet(records: List<MoveRecord>, onDismiss: () -> Unit) {
    val lines = remember(records) { formatMoveList(records, startsWithBlackMoves(records)) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (lines.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "No moves yet", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            val listState = rememberLazyListState()
            LaunchedEffect(lines.size) {
                listState.animateScrollToItem(lines.lastIndex)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .padding(horizontal = 24.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                items(lines) { line ->
                    Text(
                        text = line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/** Compact pill showing the last two formatted move lines; tapping it opens [MoveHistorySheet]. */
@Composable
fun RecentMovesStrip(
    records: List<MoveRecord>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = remember(records) {
        val lines = formatMoveList(records, startsWithBlackMoves(records))
        if (lines.isEmpty()) "No moves yet" else lines.takeLast(2).joinToString("  ")
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 2.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun startsWithBlackMoves(records: List<MoveRecord>): Boolean =
    records.firstOrNull()?.move?.piece?.color == ChessColor.BLACK

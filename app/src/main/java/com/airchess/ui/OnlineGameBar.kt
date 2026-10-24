package com.airchess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.airchess.model.OnlineUiState
import com.airchess.net.ConnectionState

private const val SURFACE_ALPHA = 0.85f
private const val LOW_TIME_MS = 10_000L
private const val TENTHS_BELOW_MS = 10_000L
private const val MS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val MS_PER_TENTH = 100L

/** "4:05" normally, "0:09.4" once under ten seconds. */
fun formatClock(millis: Long): String {
    val clamped = millis.coerceAtLeast(0)
    if (clamped < TENTHS_BELOW_MS) {
        val tenths = (clamped % MS_PER_SECOND) / MS_PER_TENTH
        return "0:${(clamped / MS_PER_SECOND).toString().padStart(2, '0')}.$tenths"
    }
    val seconds = (clamped + MS_PER_SECOND - 1) / MS_PER_SECOND
    return "${seconds / SECONDS_PER_MINUTE}:${(seconds % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"
}

@Composable
fun OnlineTopBar(online: OnlineUiState, opponentToMove: Boolean, modifier: Modifier = Modifier) {
    val running = opponentToMove && online.resultText == null && !online.waitingForOpponent
    PlayerBar(
        name = online.opponentName ?: "Waiting for opponent",
        millis = online.opponentMillis,
        active = running,
        modifier = modifier,
        leading = { ConnectionDot(online) },
    )
}

@Composable
fun OnlineBottomBar(
    online: OnlineUiState,
    myToMove: Boolean,
    onRespondDraw: (Boolean) -> Unit,
    onOfferRematch: () -> Unit,
    onRespondRematch: (Boolean) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (online.resultText != null) {
            ResultCard(online, onOfferRematch, onRespondRematch, onLeave)
        } else if (online.incomingDrawOffer) {
            DrawOfferCard(onRespondDraw)
        } else if (online.outgoingDrawOffer) {
            InfoCard("Draw offer sent")
        }

        val running = myToMove && online.resultText == null && !online.waitingForOpponent
        PlayerBar(
            name = online.myName.ifEmpty { "You" },
            millis = online.myMillis,
            active = running,
            leading = {},
        )
    }
}

@Composable
private fun PlayerBar(
    name: String,
    millis: Long,
    active: Boolean,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val low = millis < LOW_TIME_MS
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = SURFACE_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            leading()
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (active) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                Text(
                    text = formatClock(millis),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (low) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
            }
        }
    }
}

@Composable
private fun ConnectionDot(online: OnlineUiState) {
    val color = when {
        online.connection != ConnectionState.CONNECTED -> MaterialTheme.colorScheme.error
        !online.opponentConnected -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.size(10.dp).background(color, CircleShape))
        if (online.connection != ConnectionState.CONNECTED) {
            Text(
                text = "Reconnecting",
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        } else if (!online.opponentConnected && online.opponentName != null) {
            Text(
                text = "Away",
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
    }
}

@Composable
private fun DrawOfferCard(onRespond: (Boolean) -> Unit) {
    CardRow {
        Text("Opponent offers a draw", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { onRespond(false) }) { Text("Decline") }
        Button(onClick = { onRespond(true) }) { Text("Accept") }
    }
}

@Composable
private fun InfoCard(text: String) {
    CardRow {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ResultCard(
    online: OnlineUiState,
    onOfferRematch: () -> Unit,
    onRespondRematch: (Boolean) -> Unit,
    onLeave: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = SURFACE_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(online.resultText.orEmpty(), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    online.incomingRematch -> {
                        OutlinedButton(onClick = { onRespondRematch(false) }) { Text("Decline") }
                        Button(onClick = { onRespondRematch(true) }) { Text("Accept rematch") }
                    }
                    online.outgoingRematch -> Button(onClick = {}, enabled = false) { Text("Rematch requested") }
                    else -> Button(onClick = onOfferRematch) { Text("Rematch") }
                }
                OutlinedButton(onClick = onLeave) { Text("Leave") }
            }
        }
    }
}

@Composable
private fun CardRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = SURFACE_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

package com.airchess.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airchess.model.EngineUiState
import com.airchess.model.GameUiState

private const val SURFACE_ALPHA = 0.85f
private const val DISABLED_ALPHA = 0.38f

@Immutable
class GameActions(
    val onUndo: () -> Unit,
    val onReset: () -> Unit,
    val onReposition: () -> Unit,
    val onHint: () -> Unit,
    val onHistory: () -> Unit,
    val onSettings: () -> Unit,
    val onOnline: () -> Unit,
    val onResign: () -> Unit,
    val onOfferDraw: () -> Unit,
    val onRespondDraw: (Boolean) -> Unit,
    val onOfferRematch: () -> Unit,
    val onRespondRematch: (Boolean) -> Unit,
    val onLeave: () -> Unit,
)

/** Translucent HUD that leaves the middle of the screen, where the board sits, free. */
@Composable
fun GameOverlay(state: GameUiState, actions: GameActions, modifier: Modifier = Modifier) {
    val online = state.online?.takeIf { it.code != null }

    Box(modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)) {
        Column(
            modifier = Modifier.align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (online != null) {
                OnlineTopBar(online = online, opponentToMove = online.myColor != state.currentTurn)
            }
            StatusPill(state)
            EngineHintCard(state.engine, state.engineThinking)
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (online != null) {
                OnlineBottomBar(
                    online = online,
                    myToMove = online.myColor == state.currentTurn,
                    onRespondDraw = actions.onRespondDraw,
                    onOfferRematch = actions.onOfferRematch,
                    onRespondRematch = actions.onRespondRematch,
                    onLeave = actions.onLeave,
                )
            }
            RecentMovesStrip(records = state.moveHistory, onClick = actions.onHistory)
            ActionBar(state = state, actions = actions)
        }
    }
}

@Composable
private fun StatusPill(state: GameUiState) {
    val inCheck = state.gameStatus.isCheck
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = if (inCheck) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = SURFACE_ALPHA)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = SURFACE_ALPHA)
        },
        contentColor = if (inCheck) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        shadowElevation = 2.dp,
    ) {
        Text(
            text = state.statusText,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EngineHintCard(engine: EngineUiState, computerThinking: Boolean) {
    val text = when {
        computerThinking -> "Computer is thinking"
        engine.isAnalyzing -> "Analyzing..."
        engine.engineError != null -> engine.engineError
        engine.hintText != null -> engine.hintText + (engine.evalText?.let { "  Eval: $it" } ?: "")
        else -> return
    }
    val isError = !engine.isAnalyzing && engine.engineError != null
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = SURFACE_ALPHA),
        contentColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (engine.isAnalyzing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            Text(text = text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ActionBar(state: GameUiState, actions: GameActions) {
    val online = state.online?.takeIf { it.code != null }
    val gameOver = state.gameStatus.isGameOver
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = SURFACE_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (online == null) {
                ActionButton(Icons.AutoMirrored.Filled.Undo, "Undo", actions.onUndo, enabled = state.canUndo)
                ActionButton(Icons.Filled.RestartAlt, "Reset", actions.onReset)
            } else {
                val live = online.resultText == null && !online.waitingForOpponent
                ActionButton(Icons.Filled.Flag, "Resign", actions.onResign, enabled = live)
                ActionButton(
                    icon = Icons.Filled.Handshake,
                    label = "Draw",
                    onClick = actions.onOfferDraw,
                    enabled = live && !online.outgoingDrawOffer,
                )
            }
            ActionButton(Icons.Filled.CenterFocusStrong, "Reposition", actions.onReposition)
            if (state.hintsAllowed) {
                ActionButton(Icons.Filled.Lightbulb, "Hint", actions.onHint, enabled = !gameOver && !state.engine.isAnalyzing)
            }
            ActionButton(Icons.Filled.History, "History", actions.onHistory)
            ActionButton(Icons.Filled.Settings, "Settings", actions.onSettings)
            ActionButton(Icons.Filled.Public, "Online", actions.onOnline)
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    val color = LocalContentColor.current.let { if (enabled) it else it.copy(alpha = DISABLED_ALPHA) }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        contentColor = color,
    ) {
        CompositionLocalProvider(LocalContentColor provides color) {
            Column(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(22.dp))
                Text(text = label, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

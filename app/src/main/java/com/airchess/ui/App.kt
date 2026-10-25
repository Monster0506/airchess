package com.airchess.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchess.ar.ArGate
import com.airchess.chess.Fen
import com.airchess.model.HapticKind
import com.airchess.model.UiEvent
import com.airchess.viewmodel.ChessViewModel
import kotlinx.coroutines.launch

private enum class Sheet {
    NONE,
    SETTINGS,
    HISTORY,
    ONLINE,
}

@Composable
fun AirChessTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun App(viewModel: ChessViewModel) {
    AirChessTheme {
        AppContent(viewModel)
    }
}

@Composable
private fun AppContent(viewModel: ChessViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf(Sheet.NONE) }
    var confirmResign by rememberSaveable { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled by rememberUpdatedState(state.settings.hapticsEnabled)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Haptic -> if (hapticsEnabled) {
                    haptics.performHapticFeedback(event.kind.feedbackType())
                }
                is UiEvent.Message -> scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(event.text)
                }
            }
        }
    }

    val gameStarted = state.online?.let { it.code != null && !it.waitingForOpponent } == true
    LaunchedEffect(gameStarted) {
        if (gameStarted && sheet == Sheet.ONLINE) {
            sheet = Sheet.NONE
        }
    }

    val actions = remember(viewModel) {
        GameActions(
            onUndo = viewModel::undo,
            onReset = viewModel::reset,
            onReposition = viewModel::reposition,
            onHint = viewModel::requestHint,
            onHistory = { sheet = Sheet.HISTORY },
            onSettings = { sheet = Sheet.SETTINGS },
            onOnline = { sheet = Sheet.ONLINE },
            onResign = { confirmResign = true },
            onOfferDraw = viewModel::offerDraw,
            onRespondDraw = viewModel::respondDraw,
            onOfferRematch = viewModel::offerRematch,
            onRespondRematch = viewModel::respondRematch,
            onLeave = viewModel::leaveOnlineGame,
        )
    }

    Box(Modifier.fillMaxSize()) {
        ArGate(
            board = state.board,
            repositionToken = state.repositionToken,
            onSquareTapped = viewModel::onSquareTapped,
            onStatusChanged = viewModel::onArStatus,
            modifier = Modifier.fillMaxSize(),
        )

        GameOverlay(state = state, actions = actions)

        if (state.settings.showDebug) {
            val fen = remember(state.board.position) { Fen.export(state.board.position) }
            DebugOverlay(
                status = state.arStatus,
                selected = state.board.selectedSquare,
                fen = fen,
                modifier = Modifier.align(Alignment.CenterStart).safeDrawingPadding().padding(8.dp),
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 96.dp),
        )
    }

    state.pendingPromotion?.let {
        PromotionDialog(
            color = it.color,
            onChoose = viewModel::onPromotionChosen,
            onDismiss = viewModel::onPromotionDismissed,
        )
    }

    if (confirmResign) {
        ResignDialog(
            onConfirm = {
                confirmResign = false
                viewModel.resign()
            },
            onDismiss = { confirmResign = false },
        )
    }

    when (sheet) {
        Sheet.NONE -> Unit
        Sheet.SETTINGS -> SettingsSheet(
            settings = state.settings,
            onChange = viewModel::updateSettings,
            onDismiss = { sheet = Sheet.NONE },
        )
        Sheet.HISTORY -> MoveHistorySheet(records = state.moveHistory, onDismiss = { sheet = Sheet.NONE })
        Sheet.ONLINE -> PlayOnlineSheet(
            state = state,
            onCreate = viewModel::createOnlineGame,
            onJoin = viewModel::joinOnlineGame,
            onLeave = {
                viewModel.leaveOnlineGame()
                sheet = Sheet.NONE
            },
            onDismiss = { sheet = Sheet.NONE },
        )
    }
}

@Composable
private fun ResignDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resign game?") },
        text = { Text("Your opponent will win the game.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Resign") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep playing") } },
    )
}

private fun HapticKind.feedbackType(): HapticFeedbackType =
    when (this) {
        HapticKind.SELECT -> HapticFeedbackType.SegmentTick
        HapticKind.MOVE -> HapticFeedbackType.Confirm
        HapticKind.CHECK -> HapticFeedbackType.LongPress
        HapticKind.REJECTED -> HapticFeedbackType.Reject
        HapticKind.GAME_OVER -> HapticFeedbackType.LongPress
    }

package com.airchess.ui

import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import com.airchess.model.GameMode
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

private enum class Screen {
    ONBOARDING,
    HOME,
    GAME,
    LOBBY,
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
    var cameraStage by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.stopCamera(); cameraStage = false }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf(Sheet.NONE) }
    var screen by rememberSaveable { mutableStateOf(if (state.onboardingComplete) Screen.HOME else Screen.ONBOARDING) }
    var confirmResign by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var confirmDeepLink by rememberSaveable { mutableStateOf(false) }
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

    LaunchedEffect(state.online?.code, state.online?.waitingForOpponent) {
        val online = state.online
        if (online == null) {
            if (screen == Screen.LOBBY || state.lobbyError != null) {
                screen = Screen.HOME
                sheet = Sheet.ONLINE
            }
            return@LaunchedEffect
        }
        val waiting = online.code == null || online.waitingForOpponent
        screen = if (waiting) Screen.LOBBY else Screen.GAME
        sheet = if (waiting) Sheet.ONLINE else Sheet.NONE
    }
    LaunchedEffect(state.deepLinkCode, state.onboardingComplete, screen) {
        if (state.deepLinkCode != null && state.onboardingComplete) {
            if (screen == Screen.GAME || screen == Screen.LOBBY || state.online != null) {
                confirmDeepLink = true
            } else {
                viewModel.continueDeepLink()
                screen = Screen.LOBBY
                sheet = Sheet.ONLINE
            }
        }
    }
    LaunchedEffect(state.camera.enabled) {
        if (!state.camera.enabled) cameraStage = false
    }

    androidx.activity.compose.BackHandler(cameraStage) { cameraStage = false }
    androidx.activity.compose.BackHandler(!cameraStage && (screen == Screen.GAME || screen == Screen.LOBBY)) {
        if (state.online?.resultText != null) {
            viewModel.leaveOnlineGame()
            screen = Screen.HOME
        } else if (state.online != null) {
            confirmLeave = true
        } else {
            viewModel.pauseGame()
            screen = Screen.HOME
        }
    }

    if (!state.onboardingComplete) {
        Onboarding(state.playerName) {
            viewModel.completeOnboarding(it)
            screen = Screen.HOME
        }
        return
    }

    if (screen == Screen.HOME) {
        Box(Modifier.fillMaxSize()) {
            HomeScreen(
                onOnline = { sheet = Sheet.ONLINE },
                state = state,
                onLocal = { viewModel.openGame(GameMode.LOCAL); screen = Screen.GAME },
                onComputer = { color, difficulty -> viewModel.startComputerGame(color, difficulty); screen = Screen.GAME },
                onResume = {
                    val online = state.online
                    if (online != null) {
                        screen = if (online.waitingForOpponent) Screen.LOBBY else Screen.GAME
                    } else {
                        viewModel.resumeLocal()
                        screen = Screen.GAME
                    }
                },
                onSettings = { sheet = Sheet.SETTINGS },
            )
            SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).safeDrawingPadding())
        }
    } else {
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
                onLeave = {
                    if (state.online?.resultText != null) {
                        viewModel.leaveOnlineGame()
                        screen = Screen.HOME
                    } else {
                        confirmLeave = true
                    }
                },
                onCamera = {
                    if (state.camera.enabled) {
                        viewModel.stopCamera()
                        cameraStage = false
                    } else {
                        viewModel.startCamera()
                    }
                },
                onPlayAgain = viewModel::playAgain,
                onHome = { viewModel.pauseGame(); screen = Screen.HOME },
                onLocalFallback = viewModel::playLocally,
            )
        }
        Box(Modifier.fillMaxSize()) {
            ArGate(
                board = state.board,
                repositionToken = state.repositionToken,
                onSquareTapped = viewModel::onSquareTapped,
                onStatusChanged = viewModel::onArStatus,
                capture = viewModel.sceneCapture,
                prefer3d = state.prefer3d,
                onPrefer3d = { viewModel.setPrefer3d(true) },
                modifier = Modifier.fillMaxSize(),
            )
            if (cameraStage && state.camera.enabled) {
                CameraVideoStage(state, viewModel, { cameraStage = false }, { cameraStage = false }, viewModel::stopCamera)
            } else if (state.camera.enabled) {
                CameraBoardPreview(state, viewModel, { cameraStage = true }, Modifier.align(Alignment.TopEnd))
            }
            if (!cameraStage) { GameOverlay(state = state, actions = actions) }
            if (!cameraStage && state.settings.showDebug) {
                val fen = remember(state.board.position) { Fen.export(state.board.position) }
                DebugOverlay(
                    status = state.arStatus,
                    selected = state.board.selectedSquare,
                    fen = fen,
                    modifier = Modifier.align(Alignment.CenterStart).safeDrawingPadding().padding(8.dp),
                )
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 96.dp))
        }
    }

    state.pendingPromotion?.let {
        PromotionDialog(it.color, viewModel::onPromotionChosen, viewModel::onPromotionDismissed)
    }
    if (confirmResign) {
        ResignDialog({ confirmResign = false; viewModel.resign() }, { confirmResign = false })
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave this game?") },
            text = { Text("Leaving an online game immediately forfeits the game. Are you sure you want to leave?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    viewModel.leaveOnlineGame()
                    cameraStage = false
                    screen = Screen.HOME
                }) { Text("Leave") }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Keep playing") } },
        )
    }
    if (confirmDeepLink && state.deepLinkCode != null) {
        AlertDialog(
            onDismissRequest = { confirmDeepLink = false; viewModel.dismissDeepLink() },
            title = { Text("Join this game?") },
            text = {
                Text(
                    if (state.online != null) "Leaving your online game immediately forfeits it. Continue to join code ${state.deepLinkCode}?"
                    else "Leave your current game and join code ${state.deepLinkCode}?",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeepLink = false
                    if (state.online != null) viewModel.leaveOnlineGame()
                    viewModel.continueDeepLink()
                    screen = Screen.LOBBY
                    sheet = Sheet.ONLINE
                }) { Text("Join game") }
            },
            dismissButton = { TextButton(onClick = { confirmDeepLink = false; viewModel.dismissDeepLink() }) { Text("Keep playing") } },
        )
    }

    when (sheet) {
        Sheet.NONE -> Unit
        Sheet.SETTINGS -> SettingsSheet(
            settings = state.settings,
            onChange = viewModel::updateSettings,
            onDismiss = { sheet = Sheet.NONE },
            name = state.playerName,
            serverUrl = state.serverUrl,
            prefer3d = state.prefer3d,
            onNameChange = viewModel::setName,
            onServerChange = viewModel::setServerUrl,
            onPrefer3dChange = viewModel::setPrefer3d,
        )
        Sheet.HISTORY -> MoveHistorySheet(state.moveHistory) { sheet = Sheet.NONE }
        Sheet.ONLINE -> PlayOnlineSheet(
            state = state,
            onCreate = viewModel::createOnlineGame,
            onJoin = viewModel::joinOnlineGame,
            onLeave = {
                if (state.online?.code != null) {
                    confirmLeave = true
                } else {
                    viewModel.leaveOnlineGame()
                    screen = Screen.HOME
                    sheet = Sheet.NONE
                }
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

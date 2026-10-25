package com.airchess.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.airchess.chess.Fen
import com.airchess.model.GameMode
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
fun AirChessApp(viewModel: ChessViewModel, platform: PlatformServices, boardSurface: BoardSurface) {
    AirChessTheme(platform) {
        AppContent(viewModel, platform, boardSurface)
    }
}

@Composable
private fun AppContent(viewModel: ChessViewModel, platform: PlatformServices, boardSurface: BoardSurface) {
    val hudCovered = boardSurface.coversHud()
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.stopCamera() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheet by rememberSavedEnum(Sheet.NONE)
    var screen by rememberSavedEnum(if (state.onboardingComplete) Screen.HOME else Screen.ONBOARDING)
    var confirmResign by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var confirmDeepLink by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val hapticsEnabled by rememberUpdatedState(state.settings.hapticsEnabled)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Haptic -> if (hapticsEnabled) {
                    platform.haptic(event.kind)
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

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = !hudCovered && (screen == Screen.GAME || screen == Screen.LOBBY),
    ) {
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
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Onboarding(state.playerName) {
                viewModel.completeOnboarding(it)
                screen = Screen.HOME
            }
        }
        return
    }

    if (screen == Screen.HOME) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
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
            boardSurface.Content(state, viewModel, Modifier.fillMaxSize())
            if (!hudCovered) { GameOverlay(state = state, actions = actions) }
            if (!hudCovered && state.settings.showDebug) {
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
            platform = platform,
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

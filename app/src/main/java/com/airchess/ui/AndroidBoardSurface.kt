package com.airchess.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.airchess.ar.ArGate
import com.airchess.media.androidSharing
import com.airchess.model.GameUiState
import com.airchess.viewmodel.ChessViewModel

/** ARCore or 3D board, plus the WebRTC opponent preview and its full-screen stage. */
class AndroidBoardSurface : BoardSurface {
    internal var stage by mutableStateOf(false)

    @Composable
    override fun Content(state: GameUiState, viewModel: ChessViewModel, modifier: Modifier) {
        LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { stage = false }
        LaunchedEffect(state.camera.enabled) {
            if (!state.camera.enabled) {
                stage = false
            }
        }
        BackHandler(stage) { stage = false }

        Box(modifier.fillMaxSize()) {
            ArGate(
                board = state.board,
                repositionToken = state.repositionToken,
                onSquareTapped = viewModel::onSquareTapped,
                onStatusChanged = viewModel::onArStatus,
                capture = viewModel.androidSharing.capture,
                prefer3d = state.prefer3d,
                onPrefer3d = { viewModel.setPrefer3d(true) },
                modifier = Modifier.fillMaxSize(),
            )
            if (stage && state.camera.enabled) {
                CameraVideoStage(state, viewModel, { stage = false }, { stage = false }, viewModel::stopCamera)
            } else if (state.camera.enabled) {
                CameraBoardPreview(state, viewModel, { stage = true }, Modifier.align(Alignment.TopEnd))
            }
        }
    }

    @Composable
    override fun coversHud(): Boolean = stage
}

@Composable
fun rememberAndroidBoardSurface(): AndroidBoardSurface =
    rememberSaveable(
        saver = Saver<AndroidBoardSurface, Boolean>(
            save = { it.stage },
            restore = { staged -> AndroidBoardSurface().also { it.stage = staged } },
        ),
    ) { AndroidBoardSurface() }

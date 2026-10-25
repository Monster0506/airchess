package com.airchess.ios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitViewController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.airchess.ar.ArGateScreen
import com.airchess.ar.ArSupport
import com.airchess.ar.decideArGate
import com.airchess.model.GameUiState
import com.airchess.ui.BoardSurface
import com.airchess.viewmodel.ChessViewModel

/** Native RealityKit board hosted below the shared game HUD. */
class IosBoardSurface(private val factory: BoardControllerFactory) : BoardSurface {
    @Composable
    override fun Content(state: GameUiState, viewModel: ChessViewModel, modifier: Modifier) {
        var camera by remember { mutableStateOf(factory.cameraAccess()) }
        var support by remember {
            mutableStateOf(if (factory.arSupported) ArSupport.READY else ArSupport.UNSUPPORTED)
        }
        var controller by remember { mutableStateOf<BoardController?>(null) }
        var failure by remember { mutableStateOf<String?>(null) }
        var permissionPrompt by remember { mutableStateOf(false) }
        val gate = decideArGate(support, camera, state.prefer3d, failure != null)

        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            camera = factory.cameraAccess()
            support = if (factory.arSupported) ArSupport.READY else ArSupport.UNSUPPORTED
            controller?.resume()
        }
        LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
            controller?.pause()
        }
        LaunchedEffect(gate) {
            if (gate == ArGateScreen.AR || gate == ArGateScreen.BOARD_3D) {
                val mode = if (gate == ArGateScreen.AR) BoardMode.AR else BoardMode.THREE_D
                val listener = ViewModelBoardListener(viewModel) { message -> failure = message }
                controller = factory.create(mode, BoardLayout(), listener)
                failure = null
            } else {
                controller?.pause()
                controller = null
            }
        }
        val presenter = remember(controller) { controller?.let { BoardPresenter(it) } }
        LaunchedEffect(failure) {
            if (failure != null) controller?.pause()
        }


        LaunchedEffect(presenter, state.board) {
            presenter?.present(state.board)
        }
        LaunchedEffect(controller, state.repositionToken) {
            controller?.resetPlacement()
        }

        Box(modifier.fillMaxSize()) {
            val current = controller
            if (failure == null && current != null && (gate == ArGateScreen.AR || gate == ArGateScreen.BOARD_3D)) {
                UIKitViewController(
                    factory = { current.viewController },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                GateContent(
                    screen = gate,
                    failure = failure,
                    permissionPrompt = permissionPrompt,
                    onPermission = {
                        permissionPrompt = true
                        factory.requestCamera(object : CameraPromptCallback {
                            override fun onAnswered() {
                                permissionPrompt = false
                                camera = factory.cameraAccess()
                            }
                        })
                    },
                    onSettings = factory::openSettings,
                    on3d = { viewModel.setPrefer3d(true) },
                )
            }
        }

        DisposableEffect(controller) {
            onDispose { controller?.dispose() }
        }
    }

    @Composable
    override fun coversHud(): Boolean = false
}

@Composable
private fun GateContent(
    screen: ArGateScreen,
    failure: String?,
    permissionPrompt: Boolean,
    onPermission: () -> Unit,
    onSettings: () -> Unit,
    on3d: () -> Unit,
) {
    val title = if (failure != null) "Board could not be loaded" else when (screen) {
        ArGateScreen.CHECKING -> "Checking AR support"
        ArGateScreen.UNSUPPORTED -> "AR is unavailable"
        ArGateScreen.INSTALL_ARCORE -> "AR is unavailable"
        ArGateScreen.CAMERA_RATIONALE,
        ArGateScreen.CAMERA_DENIED,
        ArGateScreen.CAMERA_BLOCKED -> "Camera access is needed"
        else -> "Preparing board"
    }
    val body = failure ?: when (screen) {
        ArGateScreen.CHECKING -> ""
        ArGateScreen.UNSUPPORTED -> "This device cannot run AR. You can still play on a 3D board."
        ArGateScreen.INSTALL_ARCORE -> "AR is not supported on this device. You can still play on a 3D board."
        ArGateScreen.CAMERA_RATIONALE -> "Allow camera access to place the chessboard on your table."
        ArGateScreen.CAMERA_DENIED -> "Camera access was denied. Allow it to place the chessboard on your table."
        ArGateScreen.CAMERA_BLOCKED -> "Enable camera access in Settings to use augmented reality."
        else -> ""
    }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card {
            Column(
                Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (body.isNotEmpty()) Text(body, style = MaterialTheme.typography.bodyMedium)
                if (screen == ArGateScreen.CHECKING || permissionPrompt) CircularProgressIndicator()
                when (screen) {
                    ArGateScreen.CAMERA_RATIONALE, ArGateScreen.CAMERA_DENIED ->
                        Button(onClick = onPermission) { Text("Allow camera") }
                    ArGateScreen.CAMERA_BLOCKED ->
                        Button(onClick = onSettings) { Text("Open Settings") }
                    else -> Unit
                }
                if (screen != ArGateScreen.CHECKING && (failure == null || screen == ArGateScreen.UNSUPPORTED)) {
                    OutlinedButton(onClick = on3d) { Text("Play in 3D") }
                }
            }
        }
    }
}

package com.airchess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.airchess.media.CameraLayout
import com.airchess.model.GameUiState
import com.airchess.viewmodel.ChessViewModel

@Composable
fun CameraBoardPreview(state: GameUiState, viewModel: ChessViewModel, onOpenStage: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.safeDrawingPadding().padding(top = 108.dp, end = 12.dp)
            .size(width = 104.dp, height = 142.dp).clip(RoundedCornerShape(10.dp))
            .background(Color.Black).clickable(onClickLabel = "Open opponent view", onClick = onOpenStage),
    ) {
        CameraVideo(viewModel, CameraLayout.PREVIEW, {}, Modifier.fillMaxSize())
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(4.dp),
            color = Color.Black.copy(alpha = 0.65f),
            contentColor = Color.White,
            shape = RoundedCornerShape(4.dp),
        ) {
            Text(if (state.camera.status == "Views connected") "Opponent view" else state.camera.status, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
fun CameraVideoStage(
    state: GameUiState,
    viewModel: ChessViewModel,
    onSwap: () -> Unit,
    onBoard: () -> Unit,
    onStop: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
        CameraVideo(viewModel, CameraLayout.OPPONENT, onSwap, Modifier.fillMaxSize())
        if (state.camera.status != "Views connected") {
            Text(state.camera.status, Modifier.align(Alignment.Center), color = Color.White)
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onBoard) { Text("Your board") }
            Button(onClick = onStop) { Text("Stop sharing") }
        }
    }
}

@Composable
private fun CameraVideo(viewModel: ChessViewModel, layout: CameraLayout, onSwap: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val swap by rememberUpdatedState(onSwap)
    val view = remember(viewModel, layout == CameraLayout.PREVIEW) {
        viewModel.createCameraView(context, layout) { swap() }
    }
    DisposableEffect(viewModel, view) {
        onDispose { view?.let(viewModel::releaseCameraView) }
    }
    if (view != null) {
        AndroidView(factory = { view }, update = { viewModel.updateCameraView(it, layout) }, modifier = modifier)
    }
}

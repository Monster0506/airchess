package com.airchess.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.airchess.model.GameUiState
import com.airchess.model.HapticKind
import com.airchess.viewmodel.ChessViewModel

/** What the shared UI needs from the host OS. */
interface PlatformServices {
    fun copyText(label: String, text: String)

    fun shareText(title: String, text: String)

    fun openUrl(url: String)

    fun haptic(kind: HapticKind)

    /** The system-derived palette, or null when the platform has none and the stock one applies. */
    @Composable
    fun systemColorScheme(dark: Boolean): ColorScheme?
}

/** The board (AR or 3D) drawn full screen under the HUD, plus any platform-only extras. */
fun interface BoardSurface {
    @Composable
    fun Content(state: GameUiState, viewModel: ChessViewModel, modifier: Modifier)

    /** True while the surface shows a full-screen stage that replaces the HUD. Reads Compose state. */
    @Composable
    fun coversHud(): Boolean = false
}

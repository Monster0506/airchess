package com.airchess.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.airchess.viewmodel.ChessViewModel
import platform.UIKit.UIViewController

fun MainViewController(viewModel: ChessViewModel, boardSurface: BoardSurface): UIViewController =
    ComposeUIViewController {
        val platform = remember { IosPlatformServices() }
        AirChessApp(viewModel, platform, boardSurface)
    }

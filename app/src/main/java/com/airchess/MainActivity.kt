package com.airchess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.airchess.data.SharedPreferencesStore
import com.airchess.data.StoredPrefs
import com.airchess.engine.StockfishEngine
import com.airchess.media.AndroidViewSharing
import com.airchess.net.KtorOnlineGameClient
import com.airchess.ui.AirChessApp
import com.airchess.ui.rememberAndroidBoardSurface
import com.airchess.ui.rememberAndroidPlatformServices
import com.airchess.viewmodel.ChessViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ChessViewModel by viewModels {
        viewModelFactory {
            initializer {
                ChessViewModel(
                    engineProvider = { StockfishEngine(applicationContext) },
                    clientFactory = { KtorOnlineGameClient() },
                    prefs = StoredPrefs(SharedPreferencesStore(applicationContext), BuildConfig.DEFAULT_SERVER_URL),
                    defaultServerUrl = BuildConfig.DEFAULT_SERVER_URL,
                    viewSharing = AndroidViewSharing(applicationContext),
                )
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        acceptJoinIntent(intent)
        setContent {
            AirChessApp(viewModel, rememberAndroidPlatformServices(), rememberAndroidBoardSurface())
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptJoinIntent(intent)
    }

    private fun acceptJoinIntent(intent: android.content.Intent?) {
        val code = com.airchess.ui.parseJoinCode(intent?.dataString) ?: return
        viewModel.handleDeepLink(code)
    }
}

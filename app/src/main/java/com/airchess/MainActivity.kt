package com.airchess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.airchess.data.SharedPrefs
import com.airchess.engine.StockfishEngine
import com.airchess.net.KtorOnlineGameClient
import com.airchess.ui.App
import com.airchess.viewmodel.ChessViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ChessViewModel by viewModels {
        viewModelFactory {
            initializer {
                ChessViewModel(
                    engineProvider = { StockfishEngine(applicationContext) },
                    clientFactory = { KtorOnlineGameClient() },
                    prefs = SharedPrefs(applicationContext, BuildConfig.DEFAULT_SERVER_URL),
                    defaultServerUrl = BuildConfig.DEFAULT_SERVER_URL,
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            App(viewModel)
        }
    }
}

package com.airchess.ios

import com.airchess.data.StoredPrefs
import com.airchess.data.UserDefaultsStore
import com.airchess.engine.ChessEngine
import com.airchess.media.ViewSharing
import com.airchess.net.KtorOnlineGameClient
import com.airchess.viewmodel.ChessViewModel

/**
 * Builds the view model for the iOS host. [engineProvider] runs on first analysis, so the
 * engine process is only started when a hint or computer move is requested.
 */
fun createChessViewModel(
    serverUrl: String,
    engineProvider: () -> ChessEngine,
    viewSharing: ViewSharing,
): ChessViewModel =
    ChessViewModel(
        engineProvider = engineProvider,
        clientFactory = { KtorOnlineGameClient() },
        prefs = StoredPrefs(UserDefaultsStore(), serverUrl),
        defaultServerUrl = serverUrl,
        viewSharing = viewSharing,
    )

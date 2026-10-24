package com.airchess.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.airchess.model.GameSettings

interface Prefs {
    var playerName: String

    var serverUrl: String

    var settings: GameSettings
}

class SharedPrefs(context: Context, private val defaultServerUrl: String) : Prefs {

    private val store: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override var playerName: String
        get() = store.getString(KEY_NAME, "").orEmpty()
        set(value) = store.edit { putString(KEY_NAME, value) }

    override var serverUrl: String
        get() = store.getString(KEY_SERVER_URL, null) ?: defaultServerUrl
        set(value) = store.edit { putString(KEY_SERVER_URL, value) }

    override var settings: GameSettings
        get() {
            val defaults = GameSettings()
            return GameSettings(
                engineDepth = store.getInt(KEY_DEPTH, defaults.engineDepth)
                    .coerceIn(GameSettings.MIN_DEPTH, GameSettings.MAX_DEPTH),
                showLegalMoves = store.getBoolean(KEY_LEGAL, defaults.showLegalMoves),
                showThreats = store.getBoolean(KEY_THREATS, defaults.showThreats),
                showDebug = store.getBoolean(KEY_DEBUG, defaults.showDebug),
                hapticsEnabled = store.getBoolean(KEY_HAPTICS, defaults.hapticsEnabled),
            )
        }
        set(value) = store.edit {
            putInt(KEY_DEPTH, value.engineDepth)
            putBoolean(KEY_LEGAL, value.showLegalMoves)
            putBoolean(KEY_THREATS, value.showThreats)
            putBoolean(KEY_DEBUG, value.showDebug)
            putBoolean(KEY_HAPTICS, value.hapticsEnabled)
        }

    private companion object {
        const val FILE_NAME = "airchess"
        const val KEY_NAME = "player_name"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_DEPTH = "engine_depth"
        const val KEY_LEGAL = "show_legal_moves"
        const val KEY_THREATS = "show_threats"
        const val KEY_DEBUG = "show_debug"
        const val KEY_HAPTICS = "haptics"
    }
}

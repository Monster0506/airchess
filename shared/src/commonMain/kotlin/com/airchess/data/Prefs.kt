package com.airchess.data

import com.airchess.model.GameSettings

interface Prefs {
    var playerName: String
    var serverUrl: String
    var settings: GameSettings
    var onboardingComplete: Boolean
    var prefer3d: Boolean
    var savedMoves: String
    var savedMode: String
    var savedComputerSide: String
    var savedComputerDifficulty: String
    var savedOnlineCode: String
    var savedOnlineToken: String
}

/** Typed key-value storage that each platform backs with its native preferences. */
interface SettingsStore {
    fun getString(key: String, default: String?): String?

    fun putString(key: String, value: String)

    fun getBoolean(key: String, default: Boolean): Boolean

    fun putBoolean(key: String, value: Boolean)

    fun getInt(key: String, default: Int): Int

    fun putInt(key: String, value: Int)
}

class StoredPrefs(private val store: SettingsStore, private val defaultServerUrl: String) : Prefs {

    override var playerName: String
        get() = store.getString(KEY_NAME, "Player").orEmpty()
        set(value) = store.putString(KEY_NAME, value)

    override var serverUrl: String
        get() = store.getString(KEY_SERVER_URL, null) ?: defaultServerUrl
        set(value) = store.putString(KEY_SERVER_URL, value)

    override var onboardingComplete: Boolean
        get() = store.getBoolean(KEY_ONBOARDING, false)
        set(value) = store.putBoolean(KEY_ONBOARDING, value)

    override var prefer3d: Boolean
        get() = store.getBoolean(KEY_PREFER_3D, false)
        set(value) = store.putBoolean(KEY_PREFER_3D, value)

    override var savedMoves: String
        get() = store.getString(KEY_MOVES, "").orEmpty()
        set(value) = store.putString(KEY_MOVES, value)

    override var savedMode: String
        get() = store.getString(KEY_MODE, "").orEmpty()
        set(value) = store.putString(KEY_MODE, value)

    override var savedComputerSide: String
        get() = store.getString(KEY_COMPUTER_SIDE, "WHITE").orEmpty()
        set(value) = store.putString(KEY_COMPUTER_SIDE, value)

    override var savedComputerDifficulty: String
        get() = store.getString(KEY_COMPUTER_DIFFICULTY, "MEDIUM").orEmpty()
        set(value) = store.putString(KEY_COMPUTER_DIFFICULTY, value)

    override var savedOnlineCode: String
        get() = store.getString(KEY_ONLINE_CODE, "").orEmpty()
        set(value) = store.putString(KEY_ONLINE_CODE, value)

    override var savedOnlineToken: String
        get() = store.getString(KEY_ONLINE_TOKEN, "").orEmpty()
        set(value) = store.putString(KEY_ONLINE_TOKEN, value)

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
        set(value) {
            store.putInt(KEY_DEPTH, value.engineDepth)
            store.putBoolean(KEY_LEGAL, value.showLegalMoves)
            store.putBoolean(KEY_THREATS, value.showThreats)
            store.putBoolean(KEY_DEBUG, value.showDebug)
            store.putBoolean(KEY_HAPTICS, value.hapticsEnabled)
        }

    private companion object {
        const val KEY_NAME = "player_name"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_ONBOARDING = "onboarding_complete"
        const val KEY_PREFER_3D = "prefer_3d"
        const val KEY_MOVES = "saved_moves"
        const val KEY_MODE = "saved_mode"
        const val KEY_COMPUTER_SIDE = "computer_side"
        const val KEY_COMPUTER_DIFFICULTY = "computer_difficulty"
        const val KEY_ONLINE_CODE = "saved_online_code"
        const val KEY_ONLINE_TOKEN = "saved_online_token"
        const val KEY_DEPTH = "engine_depth"
        const val KEY_LEGAL = "show_legal_moves"
        const val KEY_THREATS = "show_threats"
        const val KEY_DEBUG = "show_debug"
        const val KEY_HAPTICS = "haptics"
    }
}

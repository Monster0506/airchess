package com.airchess.model

import com.airchess.chess.ChessColor
import com.airchess.chess.ChessSquare
import com.airchess.chess.GameStatus
import com.airchess.chess.MoveRecord
import com.airchess.net.ConnectionState
import com.airchess.protocol.Side

data class PendingPromotion(
    val from: ChessSquare,
    val to: ChessSquare,
    val color: ChessColor,
)

data class EngineUiState(
    val isAnalyzing: Boolean = false,
    /** e.g. "Suggested move: Nf3". */
    val hintText: String? = null,
    /** From White's perspective: "+0.35", "-1.20", "M3" or "-M2". */
    val evalText: String? = null,
    val engineError: String? = null,
)

data class OnlineUiState(
    val connection: ConnectionState = ConnectionState.CONNECTING,
    /** Null until the server answered the create or join request. */
    val code: String? = null,
    val mySide: Side? = null,
    val myName: String = "",
    val opponentName: String? = null,
    val opponentConnected: Boolean = false,
    val whiteMillis: Long = 0,
    val blackMillis: Long = 0,
    val timeControlLabel: String = "",
    val waitingForOpponent: Boolean = false,
    val incomingDrawOffer: Boolean = false,
    val outgoingDrawOffer: Boolean = false,
    val incomingRematch: Boolean = false,
    val outgoingRematch: Boolean = false,
    val resultText: String? = null,
    val lastError: String? = null,
) {
    val inLobby: Boolean
        get() = code == null

    val myColor: ChessColor?
        get() = mySide?.toColor()

    val myMillis: Long
        get() = if (mySide == Side.BLACK) blackMillis else whiteMillis

    val opponentMillis: Long
        get() = if (mySide == Side.BLACK) whiteMillis else blackMillis
}

data class CameraUiState(
    val enabled: Boolean = false,
    val status: String = "Camera off",
)
enum class GameMode(val key: String) {
    LOCAL("local"),
    COMPUTER("computer"),
    ONLINE("online");

    companion object {
        fun fromStored(value: String?): GameMode = entries.firstOrNull { it.key == value } ?: LOCAL
    }
}

enum class ComputerDifficulty(val depth: Int) {
    EASY(8),
    MEDIUM(12),
    HARD(16),
    MAX(20),
}

data class GameUiState(
    val board: BoardViewState = BoardViewState(),
    val currentTurn: ChessColor = ChessColor.WHITE,
    val gameStatus: GameStatus = GameStatus.IN_PROGRESS,
    val statusText: String = "White to move",
    val moveHistory: List<MoveRecord> = emptyList(),
    val canUndo: Boolean = false,
    val engine: EngineUiState = EngineUiState(),
    val pendingPromotion: PendingPromotion? = null,
    val settings: GameSettings = GameSettings(),
    val arStatus: ArStatus = ArStatus(),
    val repositionToken: Int = 0,
    val online: OnlineUiState? = null,
    val playerName: String = "",
    val serverUrl: String = "",
    val lobbyError: String? = null,
    val onboardingComplete: Boolean = false,
    val prefer3d: Boolean = false,
    val gameMode: GameMode = GameMode.LOCAL,
    val hasLocalGame: Boolean = false,
    val engineThinking: Boolean = false,
    val deepLinkCode: String? = null,
    val camera: CameraUiState = CameraUiState(),
    val cameraError: String? = null,
    val computerSide: ChessColor = ChessColor.WHITE,
    val computerDifficulty: ComputerDifficulty = ComputerDifficulty.MEDIUM,
) {
    val isOnlineGame: Boolean
        get() = online?.code != null

    val isMyTurn: Boolean
        get() = online?.myColor?.let { it == currentTurn } ?: true

    val hintsAllowed: Boolean
        get() = online?.code == null || online.resultText != null

    val hasResumeGame: Boolean
        get() = online?.code != null || (hasLocalGame && !gameStatus.isGameOver)
}

enum class HapticKind {
    SELECT,
    MOVE,
    CHECK,
    REJECTED,
    GAME_OVER,
}

sealed interface UiEvent {
    data class Haptic(val kind: HapticKind) : UiEvent

    data class Message(val text: String) : UiEvent
}

fun Side.toColor(): ChessColor = if (this == Side.WHITE) ChessColor.WHITE else ChessColor.BLACK

fun ChessColor.toSide(): Side = if (this == ChessColor.WHITE) Side.WHITE else Side.BLACK

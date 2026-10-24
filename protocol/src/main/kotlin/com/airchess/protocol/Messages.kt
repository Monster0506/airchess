package com.airchess.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Side {
    WHITE,
    BLACK;

    val opposite: Side
        get() = if (this == WHITE) BLACK else WHITE
}

@Serializable
enum class ColorChoice {
    WHITE,
    BLACK,
    RANDOM,
}

@Serializable
data class TimeControl(
    val initialSeconds: Int,
    val incrementSeconds: Int,
) {
    val label: String
        get() = "${initialSeconds / SECONDS_PER_MINUTE}+$incrementSeconds"

    companion object {
        private const val SECONDS_PER_MINUTE = 60

        val BULLET_1_0 = TimeControl(60, 0)
        val BLITZ_3_2 = TimeControl(180, 2)
        val BLITZ_5_0 = TimeControl(300, 0)
        val RAPID_10_0 = TimeControl(600, 0)
        val RAPID_15_10 = TimeControl(900, 10)

        val PRESETS = listOf(BULLET_1_0, BLITZ_3_2, BLITZ_5_0, RAPID_10_0, RAPID_15_10)
    }
}

@Serializable
enum class ResultReason {
    CHECKMATE,
    STALEMATE,
    RESIGNATION,
    TIMEOUT,
    DRAW_AGREED,
    FIFTY_MOVES,
    INSUFFICIENT_MATERIAL,
    THREEFOLD_REPETITION,
    ABANDONED,
}

/** [winner] is null for draws. */
@Serializable
data class GameResult(
    val winner: Side?,
    val reason: ResultReason,
)

/**
 * Full room state; the server broadcasts it after every change.
 * Clients rebuild the position by replaying [moves] (UCI) from the initial position.
 * Clock millis are measured at server send time; the clock of the side to move
 * is ticking iff [clockRunning].
 */
@Serializable
data class GameSnapshot(
    val code: String,
    val gameNumber: Int,
    val whiteName: String?,
    val blackName: String?,
    val timeControl: TimeControl,
    val moves: List<String>,
    val whiteMillis: Long,
    val blackMillis: Long,
    val clockRunning: Boolean,
    val drawOfferBy: Side? = null,
    val rematchOfferBy: Side? = null,
    val result: GameResult? = null,
    val whiteConnected: Boolean = false,
    val blackConnected: Boolean = false,
) {
    val started: Boolean
        get() = whiteName != null && blackName != null

    val sideToMove: Side
        get() = if (moves.size % 2 == 0) Side.WHITE else Side.BLACK
}

@Serializable
sealed interface ClientMessage {

    @Serializable
    @SerialName("create")
    data class CreateGame(
        val name: String,
        val timeControl: TimeControl,
        val color: ColorChoice,
    ) : ClientMessage

    @Serializable
    @SerialName("join")
    data class JoinGame(val code: String, val name: String) : ClientMessage

    /** Re-attach after a dropped connection using the token from [ServerMessage.Joined]. */
    @Serializable
    @SerialName("resume")
    data class Resume(val code: String, val token: String) : ClientMessage

    /** [ply] = number of moves already played before this one; guards against duplicates. */
    @Serializable
    @SerialName("move")
    data class MakeMove(val uci: String, val ply: Int) : ClientMessage

    @Serializable
    @SerialName("resign")
    data object Resign : ClientMessage

    @Serializable
    @SerialName("offer_draw")
    data object OfferDraw : ClientMessage

    @Serializable
    @SerialName("respond_draw")
    data class RespondDraw(val accept: Boolean) : ClientMessage

    @Serializable
    @SerialName("offer_rematch")
    data object OfferRematch : ClientMessage

    @Serializable
    @SerialName("respond_rematch")
    data class RespondRematch(val accept: Boolean) : ClientMessage

    @Serializable
    @SerialName("leave")
    data object Leave : ClientMessage

    @Serializable
    @SerialName("ping")
    data object Ping : ClientMessage
}

@Serializable
enum class ErrorCode {
    GAME_NOT_FOUND,
    GAME_FULL,
    INVALID_TOKEN,
    NOT_IN_GAME,
    NOT_YOUR_TURN,
    ILLEGAL_MOVE,
    GAME_OVER,
    BAD_REQUEST,
}

@Serializable
sealed interface ServerMessage {

    /** Sent to a player after create, join, or resume. A rematch swaps [side]. */
    @Serializable
    @SerialName("joined")
    data class Joined(
        val code: String,
        val token: String,
        val side: Side,
        val snapshot: GameSnapshot,
    ) : ServerMessage

    @Serializable
    @SerialName("snapshot")
    data class Snapshot(val snapshot: GameSnapshot) : ServerMessage

    @Serializable
    @SerialName("move_rejected")
    data class MoveRejected(
        val uci: String,
        val reason: ErrorCode,
        val snapshot: GameSnapshot,
    ) : ServerMessage

    @Serializable
    @SerialName("error")
    data class Error(val code: ErrorCode, val message: String) : ServerMessage

    @Serializable
    @SerialName("pong")
    data object Pong : ServerMessage
}

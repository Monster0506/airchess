package com.airchess.server

import com.airchess.protocol.GameResult
import com.airchess.protocol.Side

/** Rules authority for one game; GameRoom never looks at the board itself. */
internal interface MoveReferee {
    fun play(uci: String): MoveOutcome

    /** False when [side] cannot ever deliver checkmate, so its opponent flagging is only a draw. */
    fun canMate(side: Side): Boolean
}

internal sealed interface MoveOutcome {
    data object Illegal : MoveOutcome

    /** [result] is set when the move ended the game. */
    data class Legal(val result: GameResult?) : MoveOutcome
}

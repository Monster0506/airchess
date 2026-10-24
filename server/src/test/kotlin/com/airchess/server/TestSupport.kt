@file:OptIn(ExperimentalCoroutinesApi::class)

package com.airchess.server

import com.airchess.protocol.ClientMessage
import com.airchess.protocol.ErrorCode
import com.airchess.protocol.GameResult
import com.airchess.protocol.GameSnapshot
import com.airchess.protocol.ServerMessage
import com.airchess.protocol.Side
import com.airchess.protocol.TimeControl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent

internal class FakeConnection : Connection {
    val sent = mutableListOf<ServerMessage>()

    override suspend fun send(message: ServerMessage) {
        sent += message
    }

    inline fun <reified T : ServerMessage> all(): List<T> = sent.filterIsInstance<T>()

    inline fun <reified T : ServerMessage> last(): T = all<T>().last()

    /** Newest snapshot carried by any message. */
    fun snapshot(): GameSnapshot = when (val m = sent.last { it !is ServerMessage.Error }) {
        is ServerMessage.Joined -> m.snapshot
        is ServerMessage.Snapshot -> m.snapshot
        is ServerMessage.MoveRejected -> m.snapshot
        else -> error("no snapshot in $m")
    }

    fun lastError(): ErrorCode = last<ServerMessage.Error>().code
}

/** Accepts any four-character move except those listed as illegal; results are scripted per move. */
internal class ScriptedReferee(
    private val illegal: Set<String> = setOf("e2e5"),
    private val results: Map<String, GameResult> = emptyMap(),
    private val mating: Boolean = true,
) : MoveReferee {
    override fun play(uci: String): MoveOutcome =
        if (uci in illegal) MoveOutcome.Illegal else MoveOutcome.Legal(results[uci])

    override fun canMate(side: Side): Boolean = mating
}

internal class Table(
    scope: TestScope,
    timeControl: TimeControl = TimeControl(60, 2),
    creatorColor: Side = Side.WHITE,
    val referees: MutableList<ScriptedReferee> = mutableListOf(),
    makeReferee: () -> ScriptedReferee = { ScriptedReferee() },
    graceMs: Long = 60_000,
) {
    private var tokens = 0
    val room = GameRoom(
        code = "ABC234",
        timeControl = timeControl,
        creatorSide = creatorColor,
        newReferee = { makeReferee().also { referees += it } },
        scope = scope.backgroundScope,
        now = { scope.currentTime },
        newToken = { "token-${++tokens}" },
        graceMs = graceMs,
    )
    val annConn = FakeConnection()
    val bobConn = FakeConnection()
    lateinit var annSeat: Seat
    lateinit var bobSeat: Seat

    /** Seats both players: Ann creates, Bob joins. */
    suspend fun seatBoth(): Table {
        annSeat = room.join("Ann", annConn)!!
        bobSeat = room.join("Bob", bobConn)!!
        return this
    }

    suspend fun ann(message: ClientMessage) = room.handle(annSeat, annConn, message)

    suspend fun bob(message: ClientMessage) = room.handle(bobSeat, bobConn, message)
}

internal suspend fun TestScope.pass(ms: Long) {
    advanceTimeBy(ms)
    runCurrent()
}

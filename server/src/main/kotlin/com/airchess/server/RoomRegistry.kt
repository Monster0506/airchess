package com.airchess.server

import com.airchess.protocol.ColorChoice
import com.airchess.protocol.Protocol
import com.airchess.protocol.Side
import com.airchess.protocol.TimeControl
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.random.asKotlinRandom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val ROOM_IDLE_TTL_MS = 10 * 60_000L
private const val SWEEP_INTERVAL_MS = 60_000L
private const val TOKEN_BYTES = 16

internal class RoomRegistry(
    private val scope: CoroutineScope,
    private val newReferee: () -> MoveReferee,
    private val now: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
    private val random: Random = SecureRandom().asKotlinRandom(),
    private val idleTtlMs: Long = ROOM_IDLE_TTL_MS,
) {
    private val rooms = ConcurrentHashMap<String, GameRoom>()

    val size: Int get() = rooms.size

    fun create(timeControl: TimeControl, color: ColorChoice): GameRoom {
        val creatorSide = when (color) {
            ColorChoice.WHITE -> Side.WHITE
            ColorChoice.BLACK -> Side.BLACK
            ColorChoice.RANDOM -> if (random.nextBoolean()) Side.WHITE else Side.BLACK
        }
        while (true) {
            val code = newCode()
            val room = GameRoom(code, timeControl, creatorSide, newReferee, scope, now, ::newToken)
            if (rooms.putIfAbsent(code, room) == null) {
                return room
            }
        }
    }

    fun find(code: String): GameRoom? = rooms[code.trim().uppercase()]

    fun startCleanup() {
        scope.launch {
            while (true) {
                delay(SWEEP_INTERVAL_MS)
                sweep()
            }
        }
    }

    /** Drops rooms whose players are all gone for longer than the idle TTL. */
    suspend fun sweep() {
        rooms.values.filter { it.isIdle(idleTtlMs) }.forEach { room ->
            rooms.remove(room.code)
            room.close()
        }
    }

    private fun newCode(): String =
        String(CharArray(Protocol.CODE_LENGTH) { Protocol.CODE_ALPHABET[random.nextInt(Protocol.CODE_ALPHABET.length)] })

    private fun newToken(): String = random.nextBytes(TOKEN_BYTES).joinToString("") { "%02x".format(it) }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

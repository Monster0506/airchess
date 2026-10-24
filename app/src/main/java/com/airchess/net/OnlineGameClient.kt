package com.airchess.net

import com.airchess.protocol.ClientMessage
import com.airchess.protocol.ServerMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class ConnectionState {
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    DISCONNECTED,
}

/**
 * One WebSocket to the game server. [connect] may be called again after the socket dropped;
 * retry and resume policy belongs to the caller. The client itself only reports
 * CONNECTING, CONNECTED and DISCONNECTED.
 */
interface OnlineGameClient {
    /** Hot flow without replay: collect before [connect] to avoid missing early messages. */
    val incoming: Flow<ServerMessage>

    val state: StateFlow<ConnectionState>

    /** Suspends until the socket is open; throws when the server cannot be reached. */
    suspend fun connect(url: String)

    /** Queues the message; false when no socket is open. */
    fun send(message: ClientMessage): Boolean

    /** Flushes queued messages, then closes the socket. The client is not reusable afterwards. */
    fun close()
}

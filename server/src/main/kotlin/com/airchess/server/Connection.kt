package com.airchess.server

import com.airchess.protocol.Protocol
import com.airchess.protocol.ServerMessage
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame

/** Outbound half of a player's socket; a fake in unit tests. */
internal interface Connection {
    suspend fun send(message: ServerMessage)
}

internal class WsConnection(private val session: DefaultWebSocketSession) : Connection {
    override suspend fun send(message: ServerMessage) {
        session.send(Frame.Text(Protocol.encode(message)))
    }
}

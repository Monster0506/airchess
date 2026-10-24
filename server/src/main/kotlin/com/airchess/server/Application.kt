package com.airchess.server

import com.airchess.protocol.Protocol
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

private const val BIND_HOST = "0.0.0.0"
private const val PING_SECONDS = 15
private const val TIMEOUT_SECONDS = 30
private const val MAX_FRAME_BYTES = 64L * 1024

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: Protocol.DEFAULT_PORT
    printLanUrls(port)
    embeddedServer(Netty, port = port, host = BIND_HOST) { module() }.start(wait = true)
}

internal fun Application.module(newReferee: () -> MoveReferee = { ChessReferee() }) {
    val registry = RoomRegistry(scope = this, newReferee = newReferee)
    registry.startCleanup()

    install(CallLogging)
    install(WebSockets) {
        pingPeriod = PING_SECONDS.seconds
        timeout = TIMEOUT_SECONDS.seconds
        maxFrameSize = MAX_FRAME_BYTES
    }

    routing {
        get("/") { call.respondText("AirChess server OK - rooms: ${registry.size}") }
        webSocket(Protocol.WEBSOCKET_PATH) {
            val session = Session(registry, WsConnection(this))
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        session.onText(frame.readText())
                    }
                }
            } finally {
                withContext(NonCancellable) { session.onClose() }
            }
        }
    }
}

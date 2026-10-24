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

private const val BIND_HOST = "0.0.0.0"

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: Protocol.DEFAULT_PORT
    printLanUrls(port)
    embeddedServer(Netty, port = port, host = BIND_HOST, module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(CallLogging)
    routing {
        get("/") { call.respondText("AirChess server OK") }
    }
}

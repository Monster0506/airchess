package com.airchess.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.websocket.WebSockets

internal actual fun platformHttpClient(): HttpClient = HttpClient(Darwin) { install(WebSockets) }

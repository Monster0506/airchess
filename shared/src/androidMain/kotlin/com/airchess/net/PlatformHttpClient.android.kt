package com.airchess.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets

internal actual fun platformHttpClient(): HttpClient = HttpClient(OkHttp) { install(WebSockets) }

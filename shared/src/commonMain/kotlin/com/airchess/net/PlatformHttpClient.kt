package com.airchess.net

import io.ktor.client.HttpClient

/** WebSocket-capable client on the platform's own engine (OkHttp on Android, Darwin on iOS). */
internal expect fun platformHttpClient(): HttpClient

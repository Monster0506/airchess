package com.airchess.server

import com.airchess.protocol.IceServer
import com.airchess.protocol.Protocol
import com.airchess.protocol.ServerMessage
import java.net.HttpURLConnection.HTTP_OK
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Duration
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val CREDENTIAL_TTL_SECONDS = 21_600
private const val REQUEST_TIMEOUT_SECONDS = 10L
private const val STUN_URL = "stun:stun.cloudflare.com:3478"

internal class CameraRelay(
    private val ident: String = System.getenv("XIRSYS_IDENT").orEmpty(),
    private val secret: String = System.getenv("XIRSYS_SECRET").orEmpty(),
    private val channel: String = System.getenv("XIRSYS_CHANNEL").orEmpty(),
) {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
        .build()

    init {
        require(listOf(ident, secret, channel).all { it.isEmpty() } || listOf(ident, secret, channel).none { it.isEmpty() }) {
            "Set XIRSYS_IDENT, XIRSYS_SECRET and XIRSYS_CHANNEL together"
        }
    }

    suspend fun config(): ServerMessage.CameraConfig {
        if (ident.isEmpty()) {
            return ServerMessage.CameraConfig(listOf(IceServer(listOf(STUN_URL))))
        }
        return try {
            withContext(Dispatchers.IO) {
                val path = URLEncoder.encode(channel, UTF_8).replace("+", "%20")
                val auth = Base64.getEncoder().encodeToString("$ident:$secret".toByteArray(UTF_8))
                val request = HttpRequest.newBuilder(
                    URI.create("https://global.xirsys.net/_turn/$path?webrtc=1&expire=$CREDENTIAL_TTL_SECONDS"),
                )
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                    .header("Authorization", "Basic $auth")
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                check(response.statusCode() == HTTP_OK) { "TURN credential request failed" }
                val envelope = Protocol.json.parseToJsonElement(response.body()).jsonObject
                check(envelope["s"]?.jsonPrimitive?.content == "ok") { "TURN provider rejected request" }
                val config = Protocol.json.decodeFromJsonElement<ServerMessage.CameraConfig>(envelope.getValue("v"))
                check(config.iceServers.any { server -> server.urls.any { it.startsWith("turn:") || it.startsWith("turns:") } }) {
                    "TURN credentials missing"
                }
                config
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Provider responses can contain credentials; never log the body or request.
            ServerMessage.CameraConfig(emptyList(), "Video relay unavailable. Try again later.")
        }
    }
}

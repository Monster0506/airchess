package com.airchess.protocol

import kotlinx.serialization.json.Json

object Protocol {
    const val WEBSOCKET_PATH = "/ws"
    const val DEFAULT_PORT = 8080
    const val CODE_LENGTH = 6

    /** Unambiguous code alphabet: no 0/O, 1/I/L. */
    const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    fun encode(message: ClientMessage): String = json.encodeToString(ClientMessage.serializer(), message)

    fun encode(message: ServerMessage): String = json.encodeToString(ServerMessage.serializer(), message)

    fun decodeClient(text: String): ClientMessage = json.decodeFromString(ClientMessage.serializer(), text)

    fun decodeServer(text: String): ServerMessage = json.decodeFromString(ServerMessage.serializer(), text)
}

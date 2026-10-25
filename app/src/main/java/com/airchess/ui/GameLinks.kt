package com.airchess.ui

import com.airchess.protocol.Protocol

fun parseJoinCode(uri: String?): String? {
    if (uri == null || !uri.startsWith("airchess://join/", ignoreCase = true)) {
        return null
    }
    val raw = uri.substringAfter("airchess://join/", "").substringBefore('?').substringBefore('#').substringBefore('/')
    val normalized = raw.uppercase()
    if (normalized.length != Protocol.CODE_LENGTH || normalized.any { it !in Protocol.CODE_ALPHABET }) {
        return null
    }
    return normalized
}

fun webInviteLink(serverUrl: String, code: String): String? {
    val url = serverUrl.trim()
    val scheme = when {
        url.startsWith("wss://", ignoreCase = true) -> "https"
        url.startsWith("ws://", ignoreCase = true) -> "http"
        else -> return null
    }
    val authority = url.substringAfter("://").substringBefore('/').takeIf(String::isNotBlank) ?: return null
    return "$scheme://$authority/play/?code=$code"
}

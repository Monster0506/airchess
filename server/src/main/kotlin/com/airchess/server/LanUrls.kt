package com.airchess.server

import com.airchess.protocol.Protocol
import java.net.Inet4Address
import java.net.NetworkInterface

internal fun lanAddresses(): List<String> =
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .mapNotNull { it.hostAddress }

internal fun printLanUrls(port: Int) {
    println("AirChess server listening on port $port. Connect phones to:")
    lanAddresses().forEach { println("  ws://$it:$port${Protocol.WEBSOCKET_PATH}") }
}

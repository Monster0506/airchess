package com.airchess.media

import com.airchess.protocol.CameraSignalKind
import com.airchess.protocol.IceServer
import com.airchess.protocol.Side

enum class CameraPhase { OFF, STARTING, WAITING, CONNECTING, CONNECTED, ERROR }

/** Live view sharing as the view model drives it; each platform brings its own WebRTC stack. */
interface ViewSharing {
    /** True once a board surface is on screen that can be streamed. */
    val isReady: Boolean

    /** Throws when the platform cannot start sharing; the same session is returned until closed. */
    fun open(): ViewSharingSession
}

interface ViewSharingSession {
    fun start(
        servers: List<IceServer>,
        side: Side,
        signal: (CameraSignalKind, String) -> Unit,
        status: (CameraPhase, String) -> Unit,
    )

    fun receive(kind: CameraSignalKind, payload: String)

    /** Tells the peer to stop and tears the stream down. */
    fun stop()

    fun close()
}

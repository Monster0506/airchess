package com.airchess.media

import com.airchess.protocol.CameraSignalKind
import com.airchess.protocol.IceServer
import com.airchess.protocol.Side
import platform.UIKit.UIView

/** Swift owns the native capture and WebRTC resources. */
interface IosViewSharingBridge {
    val isReady: Boolean
    fun open(): IosViewSharingSessionBridge
    fun makeView(onSwap: () -> Unit): UIView
    fun showOpponent(opponent: Boolean, view: UIView)
    fun releaseView(view: UIView)
}

interface IosViewSharingSessionBridge {
    fun start(
        servers: List<IceServer>,
        side: Side,
        signal: (CameraSignalKind, String) -> Unit,
        status: (CameraPhase, String) -> Unit,
    )

    fun receive(kind: CameraSignalKind, payload: String)
    fun stop()
    fun close()
}

/** Common ViewSharing adapter for the Swift WebRTC implementation. */
class IosViewSharing(private val bridge: IosViewSharingBridge) : ViewSharing {
    override val isReady: Boolean
        get() = bridge.isReady

    override fun open(): ViewSharingSession = Session(bridge.open())

    private class Session(private val native: IosViewSharingSessionBridge) : ViewSharingSession {
        override fun start(
            servers: List<IceServer>,
            side: Side,
            signal: (CameraSignalKind, String) -> Unit,
            status: (CameraPhase, String) -> Unit,
        ) = native.start(servers, side, signal, status)

        override fun receive(kind: CameraSignalKind, payload: String) = native.receive(kind, payload)
        override fun stop() = native.stop()
        override fun close() = native.close()
    }
}

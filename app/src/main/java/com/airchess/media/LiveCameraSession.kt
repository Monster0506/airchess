package com.airchess.media

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.airchess.protocol.CameraSignalKind
import com.airchess.protocol.IceServer
import com.airchess.protocol.Side
import org.json.JSONObject
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

enum class CameraLayout { PREVIEW, OPPONENT }
enum class CameraPhase { OFF, STARTING, WAITING, CONNECTING, CONNECTED, ERROR }

class LiveCameraSession(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val egl = EglBase.create()
    private val factory: PeerConnectionFactory
    private val views = ArrayList<VideoView>()
    private val pendingIce = ArrayList<IceCandidate>()
    private var capture: SceneCapture? = null
    private var source: VideoSource? = null
    private var localTrack: VideoTrack? = null
    private var remoteTrack: VideoTrack? = null
    private var peer: PeerConnection? = null
    private var onSignal: ((CameraSignalKind, String) -> Unit)? = null
    private var onStatus: ((CameraPhase, String) -> Unit)? = null
    private var iceConfig: List<IceServer> = emptyList()
    private var localSide = Side.WHITE
    private var active = false
    private var remoteReady = false
    private var remoteDescriptionSet = false
    private var generation = 0
    private var closed = false

    init {
        initializeFactory(appContext)
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
    }

    fun start(servers: List<IceServer>, side: Side, scene: SceneCapture, signal: (CameraSignalKind, String) -> Unit, status: (CameraPhase, String) -> Unit) {
        check(!closed)
        stop()
        iceConfig = servers
        localSide = side
        onSignal = signal
        onStatus = status
        active = true
        publish(CameraPhase.STARTING, "Starting view sharing")
        val epoch = generation
        try {
            capture = scene
            source = factory.createVideoSource(true)
            source!!.adaptOutputFormat(SceneCapture.WIDTH, SceneCapture.HEIGHT, SceneCapture.FPS)
            localTrack = factory.createVideoTrack("airchess-view", source!!)
            for (view in views) { view.local?.let { localTrack?.addSink(it) } }
            scene.start(egl.eglBaseContext, source!!.capturerObserver) {
                dispatch(epoch) { fail("Shared view is unavailable") }
            }
            publish(CameraPhase.WAITING, "Waiting for opponent view")
            onSignal?.invoke(CameraSignalKind.READY, "")
        } catch (error: Exception) {
            fail("View sharing unavailable: ${error.message ?: "Could not capture the scene"}")
        }
    }

    fun receive(kind: CameraSignalKind, payload: String) {
        if (kind == CameraSignalKind.STOP) {
            stopCapture()
            return
        }
        if (!active) { return }
        try {
            when (kind) {
                CameraSignalKind.READY -> {
                    remoteReady = true
                    publish(CameraPhase.CONNECTING, "Connecting views")
                    createPeer()
                }
                CameraSignalKind.OFFER -> setRemote(SessionDescription.Type.OFFER, payload)
                CameraSignalKind.ANSWER -> setRemote(SessionDescription.Type.ANSWER, payload)
                CameraSignalKind.ICE -> {
                    val json = JSONObject(payload)
                    val mid = if (json.isNull("sdpMid")) null else json.getString("sdpMid")
                    val candidate = IceCandidate(mid, json.getInt("sdpMLineIndex"), json.getString("candidate"))
                    if (remoteDescriptionSet) { peer?.addIceCandidate(candidate) } else { pendingIce += candidate }
                }
                CameraSignalKind.STOP -> Unit
            }
        } catch (error: Exception) {
            fail("View connection failed: ${error.message ?: "Invalid connection data"}")
        }
    }

    fun createView(context: Context, layout: CameraLayout, onSwap: () -> Unit): View {
        val view = VideoView(context, onSwap)
        view.show(layout)
        views += view
        remoteTrack?.addSink(view.remote)
        view.local?.let { localTrack?.addSink(it) }
        return view
    }

    fun updateView(view: View, layout: CameraLayout) {
        val video = view as? VideoView ?: return
        if (video.layout == layout || video !in views) { return }
        video.local?.let { localTrack?.removeSink(it) }
        video.show(layout)
        if (layout != CameraLayout.PREVIEW) { video.local?.let { localTrack?.addSink(it) } }
    }

    fun releaseView(view: View) {
        val video = view as? VideoView ?: return
        if (!views.remove(video)) { return }
        remoteTrack?.removeSink(video.remote)
        video.local?.let { localTrack?.removeSink(it) }
        video.release()
    }

    fun stop() {
        if (active) { onSignal?.invoke(CameraSignalKind.STOP, "") }
        stopCapture()
    }

    fun close() {
        if (closed) { return }
        stop()
        while (views.isNotEmpty()) { releaseView(views.last()) }
        closed = true
        onStatus = null
        factory.dispose()
        egl.release()
    }

    private fun stopCapture() {
        generation++
        active = false
        remoteReady = false
        remoteDescriptionSet = false
        pendingIce.clear()
        capture?.stop()
        capture = null
        setRemoteTrack(null)
        val connection = peer
        peer = null
        connection?.close()
        connection?.dispose()
        for (view in views) { view.local?.let { localTrack?.removeSink(it) } }
        localTrack?.dispose()
        localTrack = null
        source?.dispose()
        source = null
        onSignal = null
        publish(CameraPhase.OFF, "Camera off")
    }

    private fun createPeer() {
        if (!active || !remoteReady || peer != null) { return }
        val epoch = generation
        val servers = iceConfig.map { server ->
            PeerConnection.IceServer.builder(server.urls)
                .setUsername(server.username).setPassword(server.credential).createIceServer()
        }
        val config = PeerConnection.RTCConfiguration(servers)
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        val connection = factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                dispatch(epoch) {
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> publish(CameraPhase.CONNECTED, "Views connected")
                        PeerConnection.IceConnectionState.FAILED, PeerConnection.IceConnectionState.DISCONNECTED -> fail("View connection lost")
                        else -> Unit
                    }
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
            override fun onIceCandidate(candidate: IceCandidate) {
                dispatch(epoch) {
                    val payload = JSONObject().put("candidate", candidate.sdp).put("sdpMid", candidate.sdpMid)
                        .put("sdpMLineIndex", candidate.sdpMLineIndex).toString()
                    onSignal?.invoke(CameraSignalKind.ICE, payload)
                }
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
            override fun onAddStream(stream: org.webrtc.MediaStream) = Unit
            override fun onRemoveStream(stream: org.webrtc.MediaStream) = Unit
            override fun onDataChannel(channel: org.webrtc.DataChannel) { channel.dispose() }
            override fun onRenegotiationNeeded() = Unit
            override fun onTrack(transceiver: RtpTransceiver) {
                dispatch(epoch) { setRemoteTrack(transceiver.receiver.track() as? VideoTrack) }
            }
        }) ?: error("Could not create view connection")
        peer = connection
        localTrack?.let { connection.addTrack(it, listOf("airchess-view")) }
        if (localSide == Side.WHITE) { createDescription(SessionDescription.Type.OFFER) }
    }

    private fun setRemote(type: SessionDescription.Type, sdp: String) {
        createPeer()
        val connection = peer ?: return
        val epoch = generation
        connection.setRemoteDescription(observer(epoch, applied = {
            remoteDescriptionSet = true
            pendingIce.forEach(connection::addIceCandidate)
            pendingIce.clear()
            if (type == SessionDescription.Type.OFFER) { createDescription(SessionDescription.Type.ANSWER) }
        }), SessionDescription(type, sdp))
    }

    private fun createDescription(type: SessionDescription.Type) {
        val connection = peer ?: return
        val epoch = generation
        val handler = observer(epoch, created = { description ->
            connection.setLocalDescription(observer(epoch, applied = {
                val kind = if (type == SessionDescription.Type.OFFER) CameraSignalKind.OFFER else CameraSignalKind.ANSWER
                onSignal?.invoke(kind, description.description)
            }), description)
        })
        if (type == SessionDescription.Type.OFFER) { connection.createOffer(handler, MediaConstraints()) }
        else { connection.createAnswer(handler, MediaConstraints()) }
    }

    private fun observer(epoch: Int, created: (SessionDescription) -> Unit = {}, applied: () -> Unit = {}) = object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) { dispatch(epoch) { created(description) } }
        override fun onSetSuccess() { dispatch(epoch, applied) }
        override fun onCreateFailure(error: String) { dispatch(epoch) { fail("View connection failed: $error") } }
        override fun onSetFailure(error: String) { dispatch(epoch) { fail("View connection failed: $error") } }
    }

    private fun dispatch(epoch: Int, action: () -> Unit) {
        main.post {
            if (epoch == generation && active && !closed) {
                try { action() } catch (error: Exception) { fail("View connection failed: ${error.message ?: "Unknown error"}") }
            }
        }
    }

    private fun setRemoteTrack(track: VideoTrack?) {
        if (remoteTrack === track) { return }
        // Receiver tracks belong to the peer and must be detached before it is disposed.
        for (view in views) { remoteTrack?.removeSink(view.remote) }
        remoteTrack = track
        for (view in views) { track?.addSink(view.remote) }
    }

    private fun fail(reason: String) {
        stop()
        publish(CameraPhase.ERROR, reason)
    }

    private fun publish(phase: CameraPhase, message: String) { onStatus?.invoke(phase, message) }

    private inner class VideoView(context: Context, onSwap: () -> Unit) : FrameLayout(context) {
        val remote = renderer(context)
        var local: VideoTexture? = null
            private set
        var layout: CameraLayout? = null
            private set
        private val full = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        private val swapListener = OnClickListener { onSwap() }
        private val preview = LayoutParams((PREVIEW_WIDTH_DP * resources.displayMetrics.density).toInt(), (PREVIEW_HEIGHT_DP * resources.displayMetrics.density).toInt(), Gravity.TOP or Gravity.END).apply {
            topMargin = (PREVIEW_TOP_DP * resources.displayMetrics.density).toInt()
            marginEnd = (PREVIEW_END_DP * resources.displayMetrics.density).toInt()
        }

        fun show(next: CameraLayout) {
            if (layout == next) { return }
            removeAllViews()
            layout = next
            if (next == CameraLayout.PREVIEW) {
                remote.setOnClickListener(null)
                addView(remote, full)
                return
            }
            val own = local ?: renderer(context).also { local = it }
            remote.setOnClickListener(null)
            remote.contentDescription = "Opponent view"
            own.setOnClickListener(swapListener)
            own.contentDescription = "Return to your view"
            addView(remote, full)
            addView(own, preview)
        }

        fun release() {
            removeAllViews()
            local?.release()
            remote.release()
        }
    }

    private fun renderer(context: Context) = VideoTexture(context, egl.eglBaseContext)

    companion object {
        private const val PREVIEW_WIDTH_DP = 112
        private const val PREVIEW_HEIGHT_DP = 154
        private const val PREVIEW_TOP_DP = 42
        private const val PREVIEW_END_DP = 14
        @Volatile private var initialized = false

        @Synchronized private fun initializeFactory(context: Context) {
            if (initialized) { return }
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
            initialized = true
        }
    }
}

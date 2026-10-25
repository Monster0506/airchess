package com.airchess.viewmodel

import com.airchess.media.CameraPhase
import com.airchess.media.ViewSharing
import com.airchess.media.ViewSharingSession
import com.airchess.protocol.CameraSignalKind
import com.airchess.protocol.ClientMessage
import com.airchess.protocol.IceServer
import com.airchess.protocol.ServerMessage
import com.airchess.protocol.Side
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

private class FakeSession : ViewSharingSession {
    var started: Side? = null
    var servers: List<IceServer> = emptyList()
    var stops = 0
    var closed = false
    val received = mutableListOf<Pair<CameraSignalKind, String>>()
    private var signal: ((CameraSignalKind, String) -> Unit)? = null
    private var status: ((CameraPhase, String) -> Unit)? = null

    override fun start(
        servers: List<IceServer>,
        side: Side,
        signal: (CameraSignalKind, String) -> Unit,
        status: (CameraPhase, String) -> Unit,
    ) {
        this.servers = servers
        started = side
        this.signal = signal
        this.status = status
        status(CameraPhase.STARTING, "Starting view sharing")
        signal(CameraSignalKind.READY, "")
    }

    override fun receive(kind: CameraSignalKind, payload: String) {
        received += kind to payload
    }

    override fun stop() {
        stops++
    }

    override fun close() {
        closed = true
    }

    fun report(phase: CameraPhase, text: String) {
        status?.invoke(phase, text)
    }
}

private class FakeSharing(override var isReady: Boolean, private val failure: Exception? = null) : ViewSharing {
    val session = FakeSession()
    var opened = 0

    override fun open(): ViewSharingSession {
        failure?.let { throw it }
        opened++
        return session
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ViewSharingTest {

    private val dispatcher = StandardTestDispatcher()
    private val client = FakeClient()
    private val ice = listOf(IceServer(listOf("stun:stun.example.test")))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(sharing: ViewSharing?) = ChessViewModel(
        engineProvider = { FakeEngine() },
        clientFactory = { client },
        engineDispatcher = dispatcher,
        now = { dispatcher.scheduler.currentTime },
        viewSharing = sharing,
    )

    private suspend fun TestScope.join(vm: ChessViewModel, configured: Boolean = true) {
        vm.joinOnlineGame("abc234", "Bob", "ws://host/ws")
        runCurrent()
        client.deliver(joined(Side.WHITE))
        runCurrent()
        if (configured) {
            client.deliver(ServerMessage.CameraConfig(ice))
            runCurrent()
        }
    }

    @Test
    fun withoutPlatformSupportSharingIsUnavailable() = runTest(dispatcher) {
        val vm = viewModel(null)
        join(vm)

        vm.startCamera()

        assertEquals("View sharing is unavailable", vm.state.value.camera.status)
        assertFalse(vm.state.value.camera.enabled)
    }

    @Test
    fun waitsForRelayConfigBeforeStarting() = runTest(dispatcher) {
        val sharing = FakeSharing(isReady = true)
        val vm = viewModel(sharing)
        join(vm, configured = false)

        vm.startCamera()

        assertEquals("Waiting for view-sharing configuration", vm.state.value.camera.status)
        assertEquals(0, sharing.opened)
    }

    @Test
    fun asksToOpenTheBoardWhenNothingCanBeCaptured() = runTest(dispatcher) {
        val sharing = FakeSharing(isReady = false)
        val vm = viewModel(sharing)
        join(vm)

        vm.startCamera()

        assertEquals("Open the AR or 3D board before sharing", vm.state.value.camera.status)
        assertEquals(0, sharing.opened)
    }

    @Test
    fun openFailureIsReportedWithoutStarting() = runTest(dispatcher) {
        val vm = viewModel(FakeSharing(isReady = true, failure = IllegalStateException("no webrtc")))
        join(vm)

        vm.startCamera()

        assertEquals("View sharing unavailable", vm.state.value.camera.status)
        assertFalse(vm.state.value.camera.enabled)
    }

    @Test
    fun startDrivesTheSessionAndRelaysSignals() = runTest(dispatcher) {
        val sharing = FakeSharing(isReady = true)
        val vm = viewModel(sharing)
        join(vm)

        vm.startCamera()

        assertEquals(Side.WHITE, sharing.session.started)
        assertEquals(ice, sharing.session.servers)
        assertTrue(vm.state.value.camera.enabled)
        assertTrue(client.sent.contains(ClientMessage.CameraSignal(CameraSignalKind.READY, "")))

        sharing.session.report(CameraPhase.CONNECTED, "Views connected")
        assertEquals("Views connected", vm.state.value.camera.status)

        sharing.session.report(CameraPhase.ERROR, "View connection lost")
        assertFalse(vm.state.value.camera.enabled)
    }

    @Test
    fun remoteSignalsReachTheSessionAndStopTurnsTheCameraOff() = runTest(dispatcher) {
        val sharing = FakeSharing(isReady = true)
        val vm = viewModel(sharing)
        join(vm)
        vm.startCamera()

        client.deliver(ServerMessage.CameraSignal(CameraSignalKind.OFFER, "sdp"))
        runCurrent()
        client.deliver(ServerMessage.CameraSignal(CameraSignalKind.STOP, ""))
        runCurrent()

        assertEquals(CameraSignalKind.OFFER to "sdp", sharing.session.received.first())
        assertNotNull(sharing.session.received.firstOrNull { it.first == CameraSignalKind.STOP })
        assertFalse(vm.state.value.camera.enabled)
        assertEquals("Camera off", vm.state.value.camera.status)
    }

    @Test
    fun stopCameraStopsTheSession() = runTest(dispatcher) {
        val sharing = FakeSharing(isReady = true)
        val vm = viewModel(sharing)
        join(vm)
        vm.startCamera()
        val before = sharing.session.stops

        vm.stopCamera()

        assertEquals(before + 1, sharing.session.stops)
        assertFalse(vm.state.value.camera.enabled)
    }
}

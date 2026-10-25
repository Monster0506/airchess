package com.airchess.viewmodel

import com.airchess.chess.ChessColor
import com.airchess.model.UiEvent
import com.airchess.net.ConnectionState
import com.airchess.protocol.ClientMessage
import com.airchess.protocol.ColorChoice
import com.airchess.protocol.ErrorCode
import com.airchess.protocol.GameResult
import com.airchess.protocol.GameSnapshot
import com.airchess.protocol.ResultReason
import com.airchess.protocol.ServerMessage
import com.airchess.protocol.Side
import com.airchess.protocol.TimeControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnlineViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val client = FakeClient()
    private val engine = FakeEngine()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ChessViewModel(
        engineProvider = { engine },
        clientFactory = { client },
        engineDispatcher = dispatcher,
        now = { dispatcher.scheduler.currentTime },
    )

    private fun ChessViewModel.tap(vararg squares: String) {
        squares.forEach { onSquareTapped(sq(it)) }
    }

    private fun TestScope.collectEvents(vm: ChessViewModel): List<UiEvent> {
        val events = mutableListOf<UiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        return events
    }

    private suspend fun TestScope.join(vm: ChessViewModel, side: Side, game: GameSnapshot = snapshot()) {
        vm.joinOnlineGame("abc234", "Bob", "ws://host/ws")
        runCurrent()
        client.deliver(joined(side, game))
        runCurrent()
    }

    private suspend fun TestScope.deliver(message: ServerMessage) {
        client.deliver(message)
        runCurrent()
    }

    @Test
    fun joinSendsUppercasedCodeAndConnects() = runTest(dispatcher) {
        val vm = viewModel()

        vm.joinOnlineGame(" abc234 ", " Bob ", "ws://host/ws")
        runCurrent()

        assertEquals(ClientMessage.JoinGame("ABC234", "Bob"), client.sent.single())
        assertEquals(ConnectionState.CONNECTED, vm.state.value.online?.connection)
        assertTrue(vm.state.value.online!!.inLobby)
    }

    @Test
    fun invalidJoinCodeShowsInlineErrorWithoutOpeningSocket() = runTest(dispatcher) {
        val vm = viewModel()

        vm.joinOnlineGame("AB0 34", "Bob", "ws://host/ws")

        assertNull(vm.state.value.online)
        assertEquals("Enter a valid 6-character game code.", vm.state.value.lobbyError)
        assertTrue(client.sent.isEmpty())
    }

    @Test
    fun savedOnlineCodeAndTokenResumeWhenViewModelStarts() = runTest(dispatcher) {
        val prefs = FakePrefs().apply {
            savedOnlineCode = "ABC234"
            savedOnlineToken = "resume-token"
        }
        val vm = ChessViewModel(
            engineProvider = { engine },
            clientFactory = { client },
            prefs = prefs,
            engineDispatcher = dispatcher,
            now = { dispatcher.scheduler.currentTime },
        )

        runCurrent()

        assertEquals(ClientMessage.Resume("ABC234", "resume-token"), client.sent.single())
        assertEquals("ABC234", vm.state.value.online?.code)
    }

    @Test
    fun deepLinkWaitsForOnboardingAndExplicitContinuation() = runTest(dispatcher) {
        val vm = viewModel()
        vm.handleDeepLink("ABC234")
        assertEquals("ABC234", vm.state.value.deepLinkCode)
        vm.completeOnboarding("Bob")
        assertEquals("ABC234", vm.state.value.deepLinkCode)
        assertTrue(client.sent.isEmpty())
        vm.continueDeepLink()
        runCurrent()

        assertNull(vm.state.value.deepLinkCode)
        assertEquals(ClientMessage.JoinGame("ABC234", "Bob"), client.sent.single())
    }

    @Test
    fun createSendsTimeControlAndColor() = runTest(dispatcher) {
        val vm = viewModel()

        vm.createOnlineGame("Alice", "ws://host/ws", TimeControl.BLITZ_3_2, ColorChoice.RANDOM)
        runCurrent()

        assertEquals(ClientMessage.CreateGame("Alice", TimeControl.BLITZ_3_2, ColorChoice.RANDOM), client.sent.single())
    }

    @Test
    fun joinedAsBlackFlipsPerspectiveAndLocksWhitePieces() = runTest(dispatcher) {
        val vm = viewModel()

        join(vm, Side.BLACK)
        vm.tap("e2")

        val state = vm.state.value
        assertEquals(ChessColor.BLACK, state.board.perspective)
        assertTrue(state.isOnlineGame)
        assertEquals("Alice", state.online?.opponentName)
        assertEquals("Opponent's move", state.statusText)
        assertNull(state.board.selectedSquare)
        assertFalse(state.canUndo)
    }

    @Test
    fun waitingForOpponentUntilBothSeated() = runTest(dispatcher) {
        val vm = viewModel()

        join(vm, Side.WHITE, snapshot(blackName = null))
        vm.tap("e2")

        assertTrue(vm.state.value.online!!.waitingForOpponent)
        assertEquals("Waiting for opponent", vm.state.value.statusText)
        assertNull(vm.state.value.board.selectedSquare)
    }

    @Test
    fun opponentSnapshotWithOneMoreMoveIsApplied() = runTest(dispatcher) {
        val vm = viewModel()
        join(vm, Side.BLACK)

        deliver(ServerMessage.Snapshot(snapshot(listOf("e2e4"))))

        val state = vm.state.value
        assertEquals("e2e4", state.board.lastMove?.uci)
        assertEquals(ChessColor.BLACK, state.currentTurn)
        assertEquals("Your move", state.statusText)
        assertEquals(listOf("e4"), state.moveHistory.map { it.san })
    }

    @Test
    fun identicalSnapshotChangesNothing() = runTest(dispatcher) {
        val vm = viewModel()
        join(vm, Side.BLACK, snapshot(listOf("e2e4")))
        val before = vm.state.value

        deliver(ServerMessage.Snapshot(snapshot(listOf("e2e4"))))

        assertEquals(before, vm.state.value)
    }

    @Test
    fun snapshotFarAheadRebuildsFromScratch() = runTest(dispatcher) {
        val vm = viewModel()
        join(vm, Side.WHITE)

        deliver(ServerMessage.Snapshot(snapshot(listOf("e2e4", "e7e5", "g1f3"))))

        assertEquals(listOf("e4", "e5", "Nf3"), vm.state.value.moveHistory.map { it.san })
    }

    @Test
    fun myMoveIsAppliedOptimisticallyAndSentWithPly() = runTest(dispatcher) {
        val vm = viewModel()
        join(vm, Side.BLACK, snapshot(listOf("e2e4")))

        vm.tap("e7", "e5")

        assertEquals(ChessColor.WHITE, vm.state.value.currentTurn)
        assertEquals(ClientMessage.MakeMove("e7e5", 1), client.sent.last())
        deliver(ServerMessage.Snapshot(snapshot(listOf("e2e4", "e7e5"))))
        assertEquals(2, vm.state.value.moveHistory.size)
    }

    @Test
    fun staleSnapshotDoesNotRevertMyOptimisticMove() = runTest(dispatcher) {
        val vm = viewModel()
        join(vm, Side.BLACK, snapshot(listOf("e2e4")))
        vm.tap("e7", "e5")

        deliver(ServerMessage.Snapshot(snapshot(listOf("e2e4"))))

        assertEquals(2, vm.state.value.moveHistory.size)
    }

    @Test
    fun moveRejectedResyncsFromItsSnapshot() = runTest(dispatcher) {
        val vm = viewModel()
        val events = collectEvents(vm)
        join(vm, Side.BLACK, snapshot(listOf("e2e4")))
        vm.tap("e7", "e5")

        deliver(ServerMessage.MoveRejected("e7e5", ErrorCode.ILLEGAL_MOVE, snapshot(listOf("e2e4"))))

        val state = vm.state.value
        assertEquals(1, state.moveHistory.size)
        assertEquals(ChessColor.BLACK, state.currentTurn)
        assertTrue(events.any { it is UiEvent.Message })
    }

    @Test
    fun undoAndResetAreDisabledOnline() = runTest(dispatcher) {
        val vm = viewModel()
        join(vm, Side.WHITE)
        vm.tap("e2", "e4")

        vm.undo()
        vm.reset()

        assertEquals(1, vm.state.value.moveHistory.size)
    }

    @Test
    fun clockOfSideToMoveCountsDownAndStopsOnResult() = runTest(dispatcher) {
        val vm = viewModel()
        join(vm, Side.WHITE, snapshot(listOf("e2e4", "e7e5"), whiteMillis = 50_000, blackMillis = 40_000, clockRunning = true))

        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(49_000, vm.state.value.online!!.whiteMillis)
        assertEquals(40_000, vm.state.value.online!!.blackMillis)

        val result = GameResult(Side.BLACK, ResultReason.RESIGNATION)
        deliver(ServerMessage.Snapshot(snapshot(listOf("e2e4", "e7e5"), whiteMillis = 48_500, blackMillis = 40_000, clockRunning = true, result = result)))
        advanceTimeBy(5_000)
        runCurrent()

        val online = vm.state.value.online!!
        assertEquals(48_500, online.whiteMillis)
        assertEquals("You lost - resignation", online.resultText)
        assertEquals("You lost - resignation", vm.state.value.statusText)
    }

    @Test
    fun clockStopsAtZero() = runTest(dispatcher) {
        val vm = viewModel()
        join(vm, Side.WHITE, snapshot(listOf("e2e4", "e7e5"), whiteMillis = 300, clockRunning = true))

        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(0, vm.state.value.online!!.whiteMillis)
    }

    @Test
    fun drawOffersMapToIncomingAndOutgoing() = runTest(dispatcher) {
        val vm = viewModel()
        join(vm, Side.WHITE)

        deliver(ServerMessage.Snapshot(snapshot(drawOfferBy = Side.BLACK)))
        assertTrue(vm.state.value.online!!.incomingDrawOffer)
        assertFalse(vm.state.value.online!!.outgoingDrawOffer)

        deliver(ServerMessage.Snapshot(snapshot(drawOfferBy = Side.WHITE)))
        assertFalse(vm.state.value.online!!.incomingDrawOffer)
        assertTrue(vm.state.value.online!!.outgoingDrawOffer)

        vm.offerDraw()
        vm.respondDraw(true)
        assertEquals(listOf(ClientMessage.OfferDraw, ClientMessage.RespondDraw(true)), client.sent.takeLast(2))
    }

    @Test
    fun resignAndRematchMessagesAreSent() = runTest(dispatcher) {
        val vm = viewModel()
        join(vm, Side.WHITE)

        vm.resign()
        vm.offerRematch()
        vm.respondRematch(false)

        assertEquals(
            listOf(ClientMessage.Resign, ClientMessage.OfferRematch, ClientMessage.RespondRematch(false)),
            client.sent.takeLast(3),
        )
    }

    @Test
    fun rematchJoinedWithSwappedSideResetsBoard() = runTest(dispatcher) {
        val vm = viewModel()
        val finished = GameResult(Side.WHITE, ResultReason.CHECKMATE)
        join(vm, Side.WHITE, snapshot(listOf("f2f3", "e7e5", "g2g4", "d8h4"), result = finished, rematchOfferBy = Side.BLACK))
        assertTrue(vm.state.value.online!!.incomingRematch)

        deliver(joined(Side.BLACK, snapshot(gameNumber = 2, whiteName = "Bob", blackName = "Alice")))

        val state = vm.state.value
        assertEquals(ChessColor.BLACK, state.board.perspective)
        assertTrue(state.moveHistory.isEmpty())
        assertNull(state.online!!.resultText)
        assertFalse(state.online.incomingRematch)
        assertEquals("Alice", state.online.myName)
        assertEquals("Opponent's move", state.statusText)
    }

    @Test
    fun droppedConnectionReconnectsAndResumes() = runTest(dispatcher) {
        val vm = viewModel()
        join(vm, Side.WHITE)

        client.drop()
        runCurrent()
        assertEquals(ConnectionState.RECONNECTING, vm.state.value.online!!.connection)
        vm.tap("e2")
        assertNull(vm.state.value.board.selectedSquare)

        advanceTimeBy(1_100)
        runCurrent()

        assertEquals(2, client.connectCount)
        assertEquals(ClientMessage.Resume("ABC234", "token-1"), client.sent.last())
        assertEquals(ConnectionState.CONNECTED, vm.state.value.online!!.connection)
    }

    @Test
    fun reconnectRetriesWithBackoffWhileServerIsDown() = runTest(dispatcher) {
        val vm = viewModel()
        join(vm, Side.WHITE)
        client.failConnects = 2

        client.drop()
        runCurrent()
        advanceTimeBy(1_100)
        assertEquals(ConnectionState.RECONNECTING, vm.state.value.online!!.connection)
        advanceTimeBy(2_000)
        assertEquals(ConnectionState.RECONNECTING, vm.state.value.online!!.connection)
        advanceTimeBy(4_000)
        runCurrent()

        assertEquals(ConnectionState.CONNECTED, vm.state.value.online!!.connection)
        assertEquals(4, client.connectCount)
    }

    @Test
    fun unreachableServerReportsLobbyError() = runTest(dispatcher) {
        client.failConnects = 1
        val vm = viewModel()

        vm.joinOnlineGame("abc234", "Bob", "ws://host/ws")
        runCurrent()

        assertNull(vm.state.value.online)
        assertEquals("Could not connect to the server", vm.state.value.lobbyError)
        assertTrue(client.closed)
    }

    @Test
    fun serverErrorInLobbyReturnsToLocalGame() = runTest(dispatcher) {
        val vm = viewModel()
        vm.joinOnlineGame("abc234", "Bob", "ws://host/ws")
        runCurrent()

        deliver(ServerMessage.Error(ErrorCode.GAME_NOT_FOUND, "No game with code ABC234"))

        assertNull(vm.state.value.online)
        assertEquals("No game with code ABC234", vm.state.value.lobbyError)
    }

    @Test
    fun leavingSendsLeaveAndStartsFreshLocalGame() = runTest(dispatcher) {
        val vm = viewModel()
        join(vm, Side.BLACK, snapshot(listOf("e2e4")))

        vm.leaveOnlineGame()

        val state = vm.state.value
        assertEquals(ClientMessage.Leave, client.sent.last())
        assertTrue(client.closed)
        assertNull(state.online)
        assertEquals(ChessColor.WHITE, state.board.perspective)
        assertTrue(state.moveHistory.isEmpty())
        vm.tap("e2", "e4")
        assertEquals(1, vm.state.value.moveHistory.size)
    }

    @Test
    fun hintsAreBlockedDuringOnlineGame() = runTest(dispatcher) {
        val vm = viewModel()
        join(vm, Side.WHITE)

        vm.requestHint()
        runCurrent()

        assertTrue(engine.fens.isEmpty())
        assertFalse(vm.state.value.hintsAllowed)
        assertNull(vm.state.value.board.hint)
    }

    @Test
    fun hintsAllowedOnceOnlineGameIsOver() = runTest(dispatcher) {
        val vm = viewModel()
        val finished = GameResult(Side.BLACK, ResultReason.RESIGNATION)
        join(vm, Side.WHITE, snapshot(listOf("e2e4", "e7e5"), result = finished))

        vm.requestHint()
        runCurrent()

        assertTrue(vm.state.value.hintsAllowed)
        assertEquals(1, engine.fens.size)
    }
}

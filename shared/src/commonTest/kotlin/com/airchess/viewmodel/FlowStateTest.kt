package com.airchess.viewmodel

import com.airchess.engine.ChessEngine
import com.airchess.engine.EngineAnalysis
import com.airchess.model.ComputerDifficulty
import com.airchess.model.GameMode
import com.airchess.protocol.ColorChoice
import com.airchess.protocol.ErrorCode
import com.airchess.protocol.ServerMessage
import com.airchess.protocol.Side
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlowStateTest {
    private val dispatcher = StandardTestDispatcher()
    private val engine = WaitingEngine()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun model(prefs: FakePrefs? = null, client: FakeClient = FakeClient()) = ChessViewModel(
        engineProvider = { engine },
        clientFactory = { client },
        prefs = prefs,
        engineDispatcher = dispatcher,
    )

    @Test
    fun emptyComputerCanResume() = runTest(dispatcher) {
        val prefs = FakePrefs()
        val vm = model(prefs)
        vm.startComputerGame(ColorChoice.BLACK, ComputerDifficulty.EASY)
        runCurrent()

        assertTrue(model(prefs).state.value.hasResumeGame)
        vm.openGame(GameMode.LOCAL)
    }

    @Test
    fun resumeKeepsUnsavedPosition() = runTest(dispatcher) {
        val vm = model()
        vm.openGame(GameMode.LOCAL)
        vm.onSquareTapped(sq("e2"))
        vm.onSquareTapped(sq("e4"))
        vm.resumeLocal()

        assertEquals(listOf("e4"), vm.state.value.moveHistory.map { it.san })
    }

    @Test
    fun undoDuringReplyKeepsRound() = runTest(dispatcher) {
        val vm = model()
        vm.startComputerGame(ColorChoice.WHITE, ComputerDifficulty.EASY)
        vm.onSquareTapped(sq("e2"))
        vm.onSquareTapped(sq("e4"))
        runCurrent()
        engine.moves.send("e7e5")
        runCurrent()
        vm.onSquareTapped(sq("g1"))
        vm.onSquareTapped(sq("f3"))
        runCurrent()
        vm.undo()

        assertEquals(listOf("e4", "e5"), vm.state.value.moveHistory.map { it.san })
        assertFalse(vm.state.value.engineThinking)
        vm.openGame(GameMode.LOCAL)
    }

    @Test
    fun leavingClearsSavedPosition() = runTest(dispatcher) {
        val prefs = FakePrefs().apply {
            savedMode = "local"
            savedMoves = "e2e4"
        }
        val client = FakeClient()
        val vm = model(prefs, client)
        vm.joinOnlineGame("ABC234", "Player", "ws://localhost:8080/ws")
        runCurrent()
        client.deliver(joined(Side.BLACK))
        runCurrent()
        vm.leaveOnlineGame()

        val restored = model(prefs).state.value
        assertFalse(restored.hasResumeGame)
        assertTrue(restored.moveHistory.isEmpty())
    }

    @Test
    fun resumeAppliesServerPosition() = runTest(dispatcher) {
        val prefs = FakePrefs().apply {
            savedOnlineCode = "ABC234"
            savedOnlineToken = "token-1"
        }
        val client = FakeClient()
        val vm = model(prefs, client)
        runCurrent()
        client.deliver(joined(Side.BLACK, snapshot(listOf("e2e4"))))
        runCurrent()

        assertEquals(listOf("e4"), vm.state.value.moveHistory.map { it.san })
        assertEquals(Side.BLACK, vm.state.value.online?.mySide)
        vm.leaveOnlineGame()
    }

    @Test
    fun computerTurnRejectsInput() = runTest(dispatcher) {
        val vm = model()
        vm.startComputerGame(ColorChoice.BLACK, ComputerDifficulty.EASY)
        runCurrent()
        vm.onSquareTapped(sq("e2"))
        vm.onSquareTapped(sq("e4"))

        assertTrue(vm.state.value.moveHistory.isEmpty())
        vm.openGame(GameMode.LOCAL)
    }

    @Test
    fun hintKeepsComputerSearch() = runTest(dispatcher) {
        val vm = model()
        vm.startComputerGame(ColorChoice.BLACK, ComputerDifficulty.EASY)
        runCurrent()
        vm.requestHint()
        runCurrent()
        engine.moves.send("e2e4")
        runCurrent()

        assertEquals(listOf("e4"), vm.state.value.moveHistory.map { it.san })
        vm.openGame(GameMode.LOCAL)
    }

    @Test
    fun expiredResumeClearsMode() = runTest(dispatcher) {
        val prefs = FakePrefs().apply {
            savedMode = GameMode.ONLINE.key
            savedOnlineCode = "ABC234"
            savedOnlineToken = "expired"
        }
        val client = FakeClient()
        val vm = model(prefs, client)
        runCurrent()
        client.deliver(ServerMessage.Error(ErrorCode.GAME_NOT_FOUND, "Game not found"))
        runCurrent()

        assertEquals(GameMode.LOCAL, vm.state.value.gameMode)
        assertFalse(vm.state.value.hasResumeGame)
        assertNotNull(vm.state.value.lobbyError)
    }

    private class WaitingEngine : ChessEngine {
        val moves = Channel<String>(Channel.UNLIMITED)

        override suspend fun analyze(fen: String, depth: Int): EngineAnalysis =
            EngineAnalysis(moves.receive(), null, emptyList(), depth)

        override fun close() = Unit
    }
}

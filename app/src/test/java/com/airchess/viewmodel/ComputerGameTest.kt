package com.airchess.viewmodel
import com.airchess.model.ComputerDifficulty
import com.airchess.protocol.ColorChoice

import kotlinx.coroutines.Dispatchers
import com.airchess.model.GameMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ComputerGameTest {
    private val dispatcher = StandardTestDispatcher()
    private val engine = FakeEngine(bestMove = "e7e5")

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
        clientFactory = { FakeClient() },
        engineDispatcher = dispatcher,
    )

    @Test
    fun computerRoundCanBeUndone() = runTest(dispatcher) {
        val vm = viewModel()
        vm.openGame(GameMode.COMPUTER)
        vm.onSquareTapped(sq("e2"))
        vm.onSquareTapped(sq("e4"))
        advanceUntilIdle()

        assertEquals(listOf("e4", "e5"), vm.state.value.moveHistory.map { it.san })
        vm.undo()
        assertEquals(emptyList<String>(), vm.state.value.moveHistory.map { it.san })
    }

    @Test
    fun engineFailureAllowsLocalPlay() = runTest(dispatcher) {
        engine.failure = IllegalStateException("binary unavailable")
        val vm = viewModel()
        vm.openGame(GameMode.COMPUTER)
        vm.onSquareTapped(sq("e2"))
        vm.onSquareTapped(sq("e4"))
        advanceUntilIdle()

        assertNotNull(vm.state.value.engine.engineError)
        assertFalse(vm.state.value.engineThinking)
        vm.playLocally()
        vm.onSquareTapped(sq("e7"))
        vm.onSquareTapped(sq("e5"))
        assertEquals(listOf("e4", "e5"), vm.state.value.moveHistory.map { it.san })
    }

    @Test
    fun blackGameRestoresSettings() = runTest(dispatcher) {
        val prefs = FakePrefs()
        engine.bestMove = "e2e4"
        val vm = ChessViewModel(
            engineProvider = { engine },
            clientFactory = { FakeClient() },
            prefs = prefs,
            engineDispatcher = dispatcher,
        )

        vm.startComputerGame(ColorChoice.BLACK, ComputerDifficulty.EASY)
        advanceUntilIdle()

        assertEquals(listOf(8), engine.depths)
        assertEquals(listOf("e4"), vm.state.value.moveHistory.map { it.san })
        assertEquals("e2e4", prefs.savedMoves)

        val restored = ChessViewModel(
            engineProvider = { engine },
            clientFactory = { FakeClient() },
            prefs = prefs,
            engineDispatcher = dispatcher,
        )
        assertEquals(GameMode.COMPUTER, restored.state.value.gameMode)
        assertEquals(com.airchess.chess.ChessColor.BLACK, restored.state.value.computerSide)
        assertEquals(ComputerDifficulty.EASY, restored.state.value.computerDifficulty)
        assertEquals(listOf("e4"), restored.state.value.moveHistory.map { it.san })
    }
}

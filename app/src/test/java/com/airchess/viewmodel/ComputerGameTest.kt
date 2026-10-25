package com.airchess.viewmodel

import kotlinx.coroutines.Dispatchers
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
    fun computerMovesAfterHumanAndUndoRemovesBothPlies() = runTest(dispatcher) {
        val vm = viewModel()
        vm.openGame("computer")
        vm.onSquareTapped(sq("e2"))
        vm.onSquareTapped(sq("e4"))
        advanceUntilIdle()

        assertEquals(listOf("e4", "e5"), vm.state.value.moveHistory.map { it.san })
        vm.undo()
        assertEquals(emptyList<String>(), vm.state.value.moveHistory.map { it.san })
    }

    @Test
    fun engineFailureIsVisibleAndLocalModeCanBeChosen() = runTest(dispatcher) {
        engine.failure = IllegalStateException("binary unavailable")
        val vm = viewModel()
        vm.openGame("computer")
        vm.onSquareTapped(sq("e2"))
        vm.onSquareTapped(sq("e4"))
        advanceUntilIdle()

        assertNotNull(vm.state.value.engine.engineError)
        assertFalse(vm.state.value.engineThinking)
        vm.openGame("local")
        assertEquals("local", vm.state.value.gameMode)
    }
}

package com.airchess.viewmodel

import com.airchess.chess.ChessColor
import com.airchess.chess.ChessPiece
import com.airchess.chess.PieceType
import com.airchess.engine.EngineScore
import com.airchess.model.GameSettings
import com.airchess.model.HapticKind
import com.airchess.model.UiEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChessViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val engine = FakeEngine()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ChessViewModel(
        engineProvider = { engine },
        clientFactory = { FakeClient() },
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

    @Test
    fun initialStateIsStartPosition() {
        val state = viewModel().state.value

        assertEquals(ChessColor.WHITE, state.currentTurn)
        assertEquals("White to move", state.statusText)
        assertFalse(state.canUndo)
        assertNull(state.online)
    }

    @Test
    fun selectingPawnShowsLegalMoves() {
        val vm = viewModel()

        vm.tap("e2")

        val board = vm.state.value.board
        assertEquals(sq("e2"), board.selectedSquare)
        assertEquals(setOf(sq("e3"), sq("e4")), board.legalMoves.map { it.to }.toSet())
    }

    @Test
    fun legalMoveIndicatorsHiddenButMovesStillWork() {
        val vm = viewModel()
        vm.updateSettings(GameSettings(showLegalMoves = false))

        vm.tap("e2")
        assertTrue(vm.state.value.board.legalMoves.isEmpty())
        assertEquals(sq("e2"), vm.state.value.board.selectedSquare)

        vm.tap("e4")
        assertEquals(ChessColor.BLACK, vm.state.value.currentTurn)
    }

    @Test
    fun tappingDestinationMovesAndFlipsTurn() {
        val vm = viewModel()

        vm.tap("e2", "e4")

        val state = vm.state.value
        assertEquals(ChessColor.BLACK, state.currentTurn)
        assertEquals(ChessPiece(ChessColor.WHITE, PieceType.PAWN), state.board.position[sq("e4")])
        assertNull(state.board.selectedSquare)
        assertEquals(sq("e4"), state.board.lastMove?.to)
        assertEquals(listOf("e4"), state.moveHistory.map { it.san })
        assertEquals("Black to move", state.statusText)
        assertTrue(state.canUndo)
    }

    @Test
    fun illegalDestinationRejectsAndClearsSelection() = runTest(dispatcher) {
        val vm = viewModel()
        val events = collectEvents(vm)

        vm.tap("e2", "e5")

        assertNull(vm.state.value.board.selectedSquare)
        assertEquals(ChessColor.WHITE, vm.state.value.currentTurn)
        assertTrue(events.contains(UiEvent.Haptic(HapticKind.REJECTED)))
    }

    @Test
    fun selectingOtherOwnPieceSwitchesSelection() {
        val vm = viewModel()

        vm.tap("e2", "g1")

        assertEquals(sq("g1"), vm.state.value.board.selectedSquare)
        assertEquals(setOf(sq("f3"), sq("h3")), vm.state.value.board.legalMoves.map { it.to }.toSet())
    }

    @Test
    fun opponentPiecesAreNotSelectable() {
        val vm = viewModel()

        vm.tap("e7")

        assertNull(vm.state.value.board.selectedSquare)
    }

    @Test
    fun promotionWaitsForChoiceThenPlaysPiece() {
        val vm = viewModel()
        vm.tap("h2", "h4", "g7", "g5", "h4", "g5", "h7", "h6", "g5", "h6", "g8", "f6")
        vm.tap("h6", "h7", "h8", "g8", "h7", "g8")

        val pending = vm.state.value.pendingPromotion
        assertNotNull(pending)
        assertEquals(sq("g8"), pending!!.to)
        assertEquals(ChessColor.WHITE, pending.color)

        vm.tap("a2")
        assertEquals(pending, vm.state.value.pendingPromotion)

        vm.onPromotionChosen(PieceType.KNIGHT)

        val state = vm.state.value
        assertNull(state.pendingPromotion)
        assertEquals(ChessPiece(ChessColor.WHITE, PieceType.KNIGHT), state.board.position[sq("g8")])
        assertTrue(state.moveHistory.last().san.contains("=N"))
    }

    @Test
    fun promotionDismissKeepsPositionUnchanged() {
        val vm = viewModel()
        vm.tap("h2", "h4", "g7", "g5", "h4", "g5", "h7", "h6", "g5", "h6", "g8", "f6")
        vm.tap("h6", "h7", "h8", "g8", "h7", "g8")

        vm.onPromotionDismissed()

        assertNull(vm.state.value.pendingPromotion)
        assertEquals(ChessColor.WHITE, vm.state.value.currentTurn)
    }

    @Test
    fun undoRestoresPositionAndHistory() {
        val vm = viewModel()
        vm.tap("e2", "e4", "e7", "e5")

        vm.undo()

        val state = vm.state.value
        assertEquals(ChessColor.BLACK, state.currentTurn)
        assertEquals(listOf("e4"), state.moveHistory.map { it.san })
        assertNull(state.board.position[sq("e5")])

        vm.undo()
        assertFalse(vm.state.value.canUndo)
        assertEquals(ChessPiece(ChessColor.WHITE, PieceType.PAWN), vm.state.value.board.position[sq("e2")])
    }

    @Test
    fun resetReturnsToStart() {
        val vm = viewModel()
        vm.tap("e2", "e4")

        vm.reset()

        val state = vm.state.value
        assertTrue(state.moveHistory.isEmpty())
        assertEquals(ChessColor.WHITE, state.currentTurn)
        assertNull(state.board.lastMove)
    }

    @Test
    fun checkIsReportedWithKingSquare() {
        val vm = viewModel()
        vm.tap("e2", "e4", "f7", "f6", "d1", "h5", "g7", "g6", "h5", "g6")

        val state = vm.state.value
        assertEquals(sq("e8"), state.board.checkedKingSquare)
        assertEquals("Black to move - Check", state.statusText)
    }

    @Test
    fun checkmateBlocksFurtherInput() {
        val vm = viewModel()
        vm.tap("f2", "f3", "e7", "e5", "g2", "g4", "d8", "h4")

        val state = vm.state.value
        assertEquals("Checkmate - Black wins", state.statusText)
        vm.tap("e2")
        assertNull(vm.state.value.board.selectedSquare)
    }

    @Test
    fun threatenedSquaresListSideToMovePiecesUnderAttack() {
        val vm = viewModel()
        vm.updateSettings(GameSettings(showThreats = true))
        vm.tap("e2", "e4", "d7", "d5")

        assertEquals(setOf(sq("e4")), vm.state.value.board.threatenedSquares)
    }

    @Test
    fun hintSetsBoardHintAndTextThenClearsAfterMove() = runTest(dispatcher) {
        val vm = viewModel()

        vm.requestHint()
        assertTrue(vm.state.value.engine.isAnalyzing)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.engine.isAnalyzing)
        assertEquals("e2e4", state.board.hint?.uci)
        assertEquals("Suggested move: e4", state.engine.hintText)
        assertEquals("+0.35", state.engine.evalText)

        vm.tap("e2", "e4")
        assertNull(vm.state.value.board.hint)
        assertNull(vm.state.value.engine.hintText)
    }

    @Test
    fun evalIsShownFromWhitePerspective() = runTest(dispatcher) {
        val vm = viewModel()
        vm.tap("e2", "e4")
        engine.bestMove = "e7e5"
        engine.score = EngineScore.Centipawns(35)

        vm.requestHint()
        advanceUntilIdle()
        assertEquals("-0.35", vm.state.value.engine.evalText)

        engine.score = EngineScore.Mate(2)
        vm.requestHint()
        advanceUntilIdle()
        assertEquals("-M2", vm.state.value.engine.evalText)
    }

    @Test
    fun hintIsDroppedWhenPositionChangesMidAnalysis() = runTest(dispatcher) {
        val vm = viewModel()

        vm.requestHint()
        vm.tap("d2", "d4")
        advanceUntilIdle()

        assertNull(vm.state.value.board.hint)
        assertFalse(vm.state.value.engine.isAnalyzing)
    }

    @Test
    fun engineFailureSetsErrorAndGameStaysPlayable() = runTest(dispatcher) {
        engine.failure = IllegalStateException("no binary")
        val vm = viewModel()

        vm.requestHint()
        advanceUntilIdle()

        assertEquals("no binary", vm.state.value.engine.engineError)
        assertFalse(vm.state.value.engine.isAnalyzing)
        vm.tap("e2", "e4")
        assertEquals(ChessColor.BLACK, vm.state.value.currentTurn)
        assertNull(vm.state.value.engine.engineError)
    }

    @Test
    fun engineProviderFailureIsReportedAndRetried() = runTest(dispatcher) {
        var attempts = 0
        val vm = ChessViewModel(
            engineProvider = {
                attempts++
                if (attempts == 1) error("cannot start") else engine
            },
            clientFactory = { FakeClient() },
            engineDispatcher = dispatcher,
        )

        vm.requestHint()
        advanceUntilIdle()
        assertEquals("cannot start", vm.state.value.engine.engineError)

        vm.requestHint()
        advanceUntilIdle()
        assertEquals("Suggested move: e4", vm.state.value.engine.hintText)
    }

    @Test
    fun repositionIncrementsToken() {
        val vm = viewModel()

        vm.reposition()
        vm.reposition()

        assertEquals(2, vm.state.value.repositionToken)
    }
}

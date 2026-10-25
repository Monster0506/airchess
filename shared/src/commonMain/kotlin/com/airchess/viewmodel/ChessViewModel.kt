package com.airchess.viewmodel

import com.airchess.media.CameraPhase
import com.airchess.media.ViewSharing
import com.airchess.media.ViewSharingSession
import com.airchess.platform.monotonicMillis
import com.airchess.protocol.CameraSignalKind
import com.airchess.protocol.IceServer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airchess.chess.ChessColor
import com.airchess.chess.ChessGame
import com.airchess.chess.ChessMove
import com.airchess.chess.ChessSquare
import com.airchess.chess.PieceType
import com.airchess.chess.San
import com.airchess.data.Prefs
import com.airchess.engine.ChessEngine
import com.airchess.engine.EngineAnalysis
import com.airchess.model.ArStatus
import com.airchess.model.EngineUiState
import com.airchess.model.GameSettings
import com.airchess.model.GameUiState
import com.airchess.model.ComputerDifficulty
import com.airchess.model.GameMode
import com.airchess.model.HapticKind
import com.airchess.model.OnlineUiState
import com.airchess.model.PendingPromotion
import com.airchess.model.UiEvent
import com.airchess.model.toColor
import com.airchess.net.ConnectionState
import com.airchess.net.OnlineGameClient
import com.airchess.protocol.ClientMessage
import com.airchess.protocol.ColorChoice
import com.airchess.protocol.ErrorCode
import com.airchess.protocol.GameSnapshot
import com.airchess.protocol.Protocol
import com.airchess.protocol.ServerMessage
import com.airchess.protocol.Side
import com.airchess.protocol.TimeControl
import kotlin.concurrent.Volatile
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val EVENT_BUFFER = 16
private const val CLOCK_TICK_MS = 100L
private const val RECONNECT_MIN_MS = 1_000L
private const val RECONNECT_MAX_MS = 8_000L
private const val FALLBACK_SERVER_URL = "ws://localhost:${Protocol.DEFAULT_PORT}${Protocol.WEBSOCKET_PATH}"

private data class ClockAnchor(
    val whiteMillis: Long,
    val blackMillis: Long,
    val receivedAt: Long,
    val running: Boolean,
    val turn: Side,
)

private enum class Sync {
    NONE,
    APPENDED,
    REBUILT,
}

/**
 * Single owner of the [ChessGame]. Every change flows UI -> here -> ChessGame -> [GameUiState].
 * All state is touched on the main dispatcher; only engine analysis hops to [engineDispatcher].
 */
class ChessViewModel(
    private val engineProvider: () -> ChessEngine,
    private val clientFactory: () -> OnlineGameClient,
    private val prefs: Prefs? = null,
    defaultServerUrl: String = FALLBACK_SERVER_URL,
    private val engineDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val now: () -> Long = ::monotonicMillis,
    val viewSharing: ViewSharing? = null,
) : ViewModel() {

    private val game = ChessGame()

    private val mutableState = MutableStateFlow(
        GameUiState(
            settings = prefs?.settings ?: GameSettings(),
            playerName = prefs?.playerName ?: "Player",
            serverUrl = prefs?.serverUrl ?: defaultServerUrl,
            onboardingComplete = prefs?.onboardingComplete == true,
            prefer3d = prefs?.prefer3d == true,
            gameMode = GameMode.fromStored(prefs?.savedMode),
            hasLocalGame = prefs?.savedMode?.let { it == GameMode.LOCAL.key || it == GameMode.COMPUTER.key } == true,
            computerSide = prefs?.savedComputerSide?.let { runCatching { ChessColor.valueOf(it) }.getOrNull() } ?: ChessColor.WHITE,
            computerDifficulty = prefs?.savedComputerDifficulty?.let { runCatching { ComputerDifficulty.valueOf(it) }.getOrNull() } ?: ComputerDifficulty.MEDIUM,
        ),
    )
    private val mutableEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = EVENT_BUFFER)

    val state: StateFlow<GameUiState> = mutableState.asStateFlow()

    val events: SharedFlow<UiEvent> = mutableEvents.asSharedFlow()

    @Volatile
    private var engine: ChessEngine? = null
    private var analysisJob: Job? = null

    private var client: OnlineGameClient? = null
    private var sessionJob: Job? = null
    private var tickerJob: Job? = null
    private var sessionCode: String? = null
    private var token: String? = null
    private var clockAnchor: ClockAnchor? = null
    private var cameraSession: ViewSharingSession? = null
    private var iceServers: List<IceServer> = emptyList()

    init {
        restoreLocalGame()
        mutate { it }
        val code = prefs?.savedOnlineCode.orEmpty()
        val key = prefs?.savedOnlineToken.orEmpty()
        if (code.isNotEmpty() && key.isNotEmpty()) {
            resumeOnline(code, key)
        }
    }


    fun setName(name: String) {
        val clean = name.trim().take(20).ifEmpty { "Player" }
        prefs?.playerName = clean
        mutate { it.copy(playerName = clean) }
    }

    fun setServerUrl(url: String) {
        val clean = url.trim()
        prefs?.serverUrl = clean
        mutate { it.copy(serverUrl = clean) }
    }

    fun setPrefer3d(value: Boolean) {
        prefs?.prefer3d = value
        mutate { it.copy(prefer3d = value) }
    }

    fun handleDeepLink(code: String) {
        mutate { it.copy(deepLinkCode = code) }
    }

    fun continueDeepLink() {
        val code = mutableState.value.deepLinkCode ?: return
        mutate { it.copy(deepLinkCode = null) }
        joinOnlineGame(code, mutableState.value.playerName, mutableState.value.serverUrl)
    }

    fun dismissDeepLink() {
        mutate { it.copy(deepLinkCode = null) }
    }

    fun completeOnboarding(name: String) {
        val clean = name.trim().ifEmpty { "Player" }
        prefs?.playerName = clean
        prefs?.onboardingComplete = true
        mutate { it.copy(playerName = clean, onboardingComplete = true) }
    }


    fun resumeLocal() {
        positionChanged()
        val current = mutableState.value
        if (current.gameMode == GameMode.COMPUTER && game.currentTurn != current.computerSide && !game.gameStatus.isGameOver) {
            playComputerMove()
        }
    }

    fun pauseGame() {
        positionChanged()
        stopCamera()
        persistLocalGame()
    }

    fun playLocally() {
        closeSession()
        mutate { it.copy(online = null, gameMode = GameMode.LOCAL, hasLocalGame = true) }
        positionChanged()
        persistLocalGame()
    }

    private fun restoreLocalGame() {
        game.reset()
        val mode = GameMode.fromStored(prefs?.savedMode)
        if (mode == GameMode.ONLINE) {
            return
        }
        val moves = prefs?.savedMoves.orEmpty().split(',').filter(String::isNotBlank)
        for (uci in moves) {
            val move = game.findMoveByUci(uci) ?: return
            if (!game.makeMove(move)) {
                return
            }
        }
    }

    fun resumeOnline(code: String, key: String) {
        val url = mutableState.value.serverUrl
        closeSession()
        val session = clientFactory()
        client = session
        sessionCode = code
        token = key
        game.reset()
        mutate { it.copy(online = OnlineUiState(code = code, myName = it.playerName), gameMode = GameMode.ONLINE, hasLocalGame = false) }
        sessionJob = viewModelScope.launch { runSession(session, url, ClientMessage.Resume(code, key)) }
    }


    fun openGame(mode: GameMode) {
        require(mode != GameMode.ONLINE)
        closeSession()
        game.reset()
        mutate {
            it.copy(
                online = null,
                gameMode = mode,
                hasLocalGame = true,
                board = it.board.copy(perspective = if (mode == GameMode.COMPUTER) it.computerSide else ChessColor.WHITE),
            )
        }
        positionChanged()
        persistLocalGame()
        if (mode == GameMode.COMPUTER && mutableState.value.computerSide == ChessColor.BLACK) {
            playComputerMove()
        }
    }

    fun startComputerGame(colorChoice: ColorChoice, difficulty: ComputerDifficulty) {
        val humanColor = when (colorChoice) {
            ColorChoice.WHITE -> ChessColor.WHITE
            ColorChoice.BLACK -> ChessColor.BLACK
            ColorChoice.RANDOM -> if (kotlin.random.Random.nextBoolean()) ChessColor.WHITE else ChessColor.BLACK
        }
        mutate { it.copy(computerSide = humanColor, computerDifficulty = difficulty) }
        openGame(GameMode.COMPUTER)
    }

    fun onSquareTapped(square: ChessSquare) {
        val current = mutableState.value
        if (current.pendingPromotion != null || current.gameStatus.isGameOver || !canInteract(current)) {
            return
        }

        val hadSelection = game.selectedSquare != null
        val moves = game.legalMoves.filter { it.to == square }
        if (hadSelection && moves.isNotEmpty()) {
            val first = moves.first()
            if (first.promotion != null) {
                mutate { it.copy(pendingPromotion = PendingPromotion(first.from, first.to, first.piece.color)) }
                return
            }
            playMove(first)
            return
        }

        val selected = game.selectSquare(square)
        if (selected) {
            haptic(HapticKind.SELECT)
        } else if (hadSelection) {
            haptic(HapticKind.REJECTED)
        }
        mutate { it }
    }

    fun onPromotionChosen(type: PieceType) {
        val pending = mutableState.value.pendingPromotion ?: return
        val uci = pending.from.algebraic + pending.to.algebraic + type.symbol
        val move = game.findMoveByUci(uci)
        if (move == null) {
            onPromotionDismissed()
            return
        }
        mutate { it.copy(pendingPromotion = null) }
        playMove(move)
    }

    fun onPromotionDismissed() {
        mutate { it.copy(pendingPromotion = null) }
    }

    fun undo() {
        if (mutableState.value.online != null) {
            return
        }
        if (!game.undoMove()) {
            return
        }
        val mode = mutableState.value
        if (mode.gameMode == GameMode.COMPUTER && game.currentTurn != mode.computerSide && game.moveHistory.isNotEmpty()) {
            game.undoMove()
        }
        positionChanged()
        persistLocalGame()
        val current = mutableState.value
        if (current.gameMode == GameMode.COMPUTER && game.currentTurn != current.computerSide && !game.gameStatus.isGameOver) {
            playComputerMove()
        }
    }

    fun reset() {
        if (mutableState.value.online != null) {
            return
        }
        game.reset()
        positionChanged()
        persistLocalGame()
        if (mutableState.value.gameMode == GameMode.COMPUTER && mutableState.value.computerSide == ChessColor.BLACK) {
            playComputerMove()
        }
    }
    fun playAgain() = reset()

    fun reposition() {
        mutate { it.copy(repositionToken = it.repositionToken + 1) }
    }

    fun onArStatus(status: ArStatus) {
        mutableState.update { it.copy(arStatus = status) }
    }

    fun updateSettings(settings: GameSettings) {
        prefs?.settings = settings
        mutate { it.copy(settings = settings) }
    }

    fun requestHint() {
        if (game.gameStatus.isGameOver || !mutableState.value.hintsAllowed || mutableState.value.engineThinking) {
            return
        }
        analysisJob?.cancel()
        val fen = game.exportFen()
        val depth = mutableState.value.settings.engineDepth
        mutate { it.copy(board = it.board.copy(hint = null), engine = EngineUiState(isAnalyzing = true)) }

        analysisJob = viewModelScope.launch {
            try {
                val analysis = withContext(engineDispatcher) { obtainEngine().analyze(fen, depth) }
                showHint(fen, analysis)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = e.message ?: "Engine unavailable"
                mutate { it.copy(engine = EngineUiState(engineError = error)) }
            }
        }
    }

    fun createOnlineGame(name: String, serverUrl: String, timeControl: TimeControl, colorChoice: ColorChoice) {
        startOnline(name, serverUrl) { ClientMessage.CreateGame(it, timeControl, colorChoice) }
    }

    fun joinOnlineGame(code: String, name: String, serverUrl: String) {
        val normalized = code.trim().uppercase()
        if (normalized.length != Protocol.CODE_LENGTH || normalized.any { it !in Protocol.CODE_ALPHABET }) {
            mutate { it.copy(lobbyError = "Enter a valid ${Protocol.CODE_LENGTH}-character game code.") }
            return
        }
        startOnline(name, serverUrl) { ClientMessage.JoinGame(normalized, it) }
    }

    fun leaveOnlineGame() {
        if (mutableState.value.online == null) {
            return
        }
        prefs?.savedOnlineCode = ""
        prefs?.savedOnlineToken = ""
        prefs?.savedMode = ""
        prefs?.savedMoves = ""
        cameraSession?.stop()
        client?.send(ClientMessage.Leave)
        closeSession()
        game.reset()
        mutate {
            it.copy(
                online = null,
                gameMode = GameMode.LOCAL,
                hasLocalGame = false,
                board = it.board.copy(hint = null, perspective = ChessColor.WHITE),
                engine = EngineUiState(),
                pendingPromotion = null,
            )
        }
    }

    fun resign() {
        sendOnline(ClientMessage.Resign)
    }

    fun offerDraw() {
        sendOnline(ClientMessage.OfferDraw)
    }

    fun respondDraw(accept: Boolean) {
        sendOnline(ClientMessage.RespondDraw(accept))
    }

    fun offerRematch() {
        sendOnline(ClientMessage.OfferRematch)
    }

    fun respondRematch(accept: Boolean) {
        sendOnline(ClientMessage.RespondRematch(accept))
    }

    fun startCamera() {
        val current = mutableState.value
        if (!current.isOnlineGame || current.cameraError != null || current.camera.enabled) { return }
        val sharing = viewSharing
        if (sharing == null) {
            setCameraStatus("View sharing is unavailable")
            return
        }
        if (iceServers.isEmpty()) {
            setCameraStatus("Waiting for view-sharing configuration")
            return
        }
        if (!sharing.isReady) {
            setCameraStatus("Open the AR or 3D board before sharing")
            return
        }
        val side = current.online?.mySide
        if (side == null) {
            setCameraStatus("View sharing is unavailable")
            return
        }
        val camera = try {
            cameraSession ?: sharing.open().also { cameraSession = it }
        } catch (_: Exception) {
            setCameraStatus("View sharing unavailable")
            return
        }
        mutate { it.copy(camera = it.camera.copy(enabled = true, status = "Starting view sharing")) }
        camera.start(
            servers = iceServers,
            side = side,
            signal = { kind, payload -> client?.send(ClientMessage.CameraSignal(kind, payload)) },
            status = { phase, status ->
                mutableState.update {
                    it.copy(camera = it.camera.copy(enabled = phase != CameraPhase.OFF && phase != CameraPhase.ERROR, status = status))
                }
            },
        )
    }

    fun stopCamera() {
        cameraSession?.stop()
        mutate { it.copy(camera = it.camera.copy(enabled = false, status = "Camera off")) }
    }

    fun receiveCameraSignal(kind: CameraSignalKind, payload: String) {
        cameraSession?.receive(kind, payload)
        if (kind == CameraSignalKind.STOP) {
            mutate { it.copy(camera = it.camera.copy(enabled = false, status = "Camera off")) }
        }
    }

    private fun setCameraStatus(status: String) {
        mutableState.update { it.copy(camera = it.camera.copy(status = status)) }
    }

    override fun onCleared() {
        engine?.close()
        cameraSession?.close()
        client?.close()
    }

    private fun mutate(change: (GameUiState) -> GameUiState) {
        mutableState.update { derive(change(it)) }
    }

    private fun derive(base: GameUiState): GameUiState {
        val position = game.currentPosition
        val status = game.gameStatus
        val turn = game.currentTurn
        val history = game.moveHistory
        val online = base.online
        val threatened = if (base.settings.showThreats && !status.isGameOver) {
            game.attackedSquares(turn.opposite).filter { position[it]?.color == turn }.toSet()
        } else {
            emptySet()
        }
        val statusText = if (online?.code != null) {
            onlineStatusText(status, online.myColor == turn, online.waitingForOpponent, online.resultText)
        } else {
            localStatusText(status, turn)
        }

        return base.copy(
            board = base.board.copy(
                position = position,
                selectedSquare = game.selectedSquare,
                legalMoves = if (base.settings.showLegalMoves) game.legalMoves else emptyList(),
                lastMove = history.lastOrNull()?.move,
                threatenedSquares = threatened,
                checkedKingSquare = if (status.isCheck) position.kingSquare(turn) else null,
            ),
            currentTurn = turn,
            gameStatus = status,
            statusText = statusText,
            moveHistory = history,
            canUndo = online == null && history.any {
                base.gameMode != GameMode.COMPUTER || it.move.piece.color == base.computerSide
            },
        )
    }

    private fun canInteract(current: GameUiState): Boolean {
        val online = current.online ?: return current.gameMode != GameMode.COMPUTER ||
            (!current.engineThinking && game.currentTurn == current.computerSide)
        return online.code != null &&
            online.connection == ConnectionState.CONNECTED &&
            online.resultText == null &&
            !online.waitingForOpponent &&
            online.myColor == game.currentTurn
    }

    private fun playMove(move: ChessMove) {
        val ply = game.moveHistory.size
        if (!game.makeMove(move)) {
            haptic(HapticKind.REJECTED)
            return
        }
        positionChanged()
        moveHaptic()
        if (mutableState.value.online != null && !sendOnline(ClientMessage.MakeMove(move.uci, ply))) {
            game.undoMove()
            positionChanged()
            return
        }
        persistLocalGame()
        val afterMove = mutableState.value
        if (afterMove.gameMode == GameMode.COMPUTER && game.currentTurn != afterMove.computerSide && !game.gameStatus.isGameOver) {
            playComputerMove()
        }
    }

    private fun persistLocalGame() {
        if (mutableState.value.online != null) {
            return
        }
        prefs?.savedMode = mutableState.value.gameMode.key
        prefs?.savedMoves = game.moveHistory.joinToString(",") { it.move.uci }
        prefs?.savedComputerSide = mutableState.value.computerSide.name
        prefs?.savedComputerDifficulty = mutableState.value.computerDifficulty.name
    }

    private fun positionChanged() {
        analysisJob?.cancel()
        analysisJob = null
        mutate {
            it.copy(
                board = it.board.copy(hint = null),
                engine = EngineUiState(),
                pendingPromotion = null,
                engineThinking = false,
            )
        }
    }

    private fun playComputerMove() {
        val computer = mutableState.value
        if (computer.gameMode != GameMode.COMPUTER || game.currentTurn == computer.computerSide || game.gameStatus.isGameOver) {
            return
        }
        val fen = game.exportFen()
        val depth = computer.computerDifficulty.depth
        mutate { it.copy(engineThinking = true, engine = EngineUiState(isAnalyzing = true)) }
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            try {
                val answer = withContext(engineDispatcher) {
                    obtainEngine().analyze(fen, depth)
                }
                if (game.exportFen() != fen) {
                    return@launch
                }
                val move = answer.bestMove?.let { game.findMoveByUci(it) }
                if (move == null || !game.makeMove(move)) {
                    mutate { it.copy(engineThinking = false, engine = EngineUiState(engineError = "Stockfish found no usable move")) }
                    return@launch
                }
                positionChanged()
                persistLocalGame()
                moveHaptic()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val reason = e.message ?: "Stockfish is unavailable"
                mutate { it.copy(engineThinking = false, engine = EngineUiState(engineError = "Computer unavailable: $reason")) }
            }
        }
    }

    private fun moveHaptic() {
        val status = game.gameStatus
        when {
            status.isGameOver -> haptic(HapticKind.GAME_OVER)
            status.isCheck -> haptic(HapticKind.CHECK)
            else -> haptic(HapticKind.MOVE)
        }
    }

    private fun haptic(kind: HapticKind) {
        mutableEvents.tryEmit(UiEvent.Haptic(kind))
    }

    private fun emitMessage(text: String) {
        mutableEvents.tryEmit(UiEvent.Message(text))
    }

    private suspend fun obtainEngine(): ChessEngine = engine ?: engineProvider().also { engine = it }

    private fun showHint(fen: String, analysis: EngineAnalysis) {
        if (game.exportFen() != fen) {
            return
        }
        val move = analysis.bestMove?.let { game.findMoveByUci(it) }
        if (move == null) {
            mutate { it.copy(engine = EngineUiState(engineError = "Engine found no usable move")) }
            return
        }
        val san = San.format(game.currentPosition, move)
        val eval = analysis.evaluation?.let { formatEval(it, game.currentTurn) }
        mutate {
            it.copy(
                board = it.board.copy(hint = move),
                engine = EngineUiState(hintText = "Suggested move: $san", evalText = eval),
            )
        }
    }

    private fun startOnline(name: String, serverUrl: String, opening: (String) -> ClientMessage) {
        val cleanName = name.trim()
        val url = serverUrl.trim()
        closeSession()
        prefs?.playerName = cleanName
        prefs?.serverUrl = url

        val session = clientFactory()
        client = session
        mutate {
            it.copy(
                online = OnlineUiState(myName = cleanName),
                playerName = cleanName,
                serverUrl = url,
                lobbyError = null,
            )
        }
        sessionJob = viewModelScope.launch { runSession(session, url, opening(cleanName)) }
    }

    private fun closeSession() {
        analysisJob?.cancel()
        analysisJob = null
        cameraSession?.stop()
        mutableState.update {
            it.copy(camera = it.camera.copy(enabled = false, status = "Camera off"), cameraError = null)
        }
        iceServers = emptyList()
        sessionJob?.cancel()
        tickerJob?.cancel()
        client?.close()
        sessionJob = null
        tickerJob = null
        client = null
        sessionCode = null
        token = null
        clockAnchor = null
    }

    private fun failLobby(text: String) {
        closeSession()
        mutate { it.copy(online = null, lobbyError = text) }
    }

    private suspend fun runSession(session: OnlineGameClient, url: String, opening: ClientMessage) {
        coroutineScope {
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                session.incoming.collect { onServerMessage(it) }
            }
            try {
                holdSession(session, url, opening)
            } finally {
                collector.cancel()
            }
        }
    }

    private suspend fun holdSession(session: OnlineGameClient, url: String, opening: ClientMessage) {
        if (!tryConnect(session, url)) {
            if (token == null) {
                failLobby("Could not connect to the server")
                return
            }
            reconnect(session, url)
        } else {
            setConnection(ConnectionState.CONNECTED)
            session.send(opening)
        }

        while (true) {
            session.state.first { it == ConnectionState.DISCONNECTED }
            if (token == null) {
                failLobby("Connection lost")
                return
            }
            reconnect(session, url)
        }
    }

    private suspend fun reconnect(session: OnlineGameClient, url: String) {
        setConnection(ConnectionState.RECONNECTING)
        var wait = RECONNECT_MIN_MS
        while (true) {
            delay(wait)
            if (tryConnect(session, url)) {
                val code = sessionCode
                val key = token
                if (code != null && key != null) {
                    session.send(ClientMessage.Resume(code, key))
                }
                setConnection(ConnectionState.CONNECTED)
                return
            }
            wait = min(wait * 2, RECONNECT_MAX_MS)
        }
    }

    private suspend fun tryConnect(session: OnlineGameClient, url: String): Boolean =
        try {
            session.connect(url)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

    private fun setConnection(connection: ConnectionState) {
        if (connection == ConnectionState.RECONNECTING || connection == ConnectionState.DISCONNECTED) {
            stopCamera()
        }
        mutableState.update { s -> s.online?.let { s.copy(online = it.copy(connection = connection)) } ?: s }
    }

    private fun sendOnline(message: ClientMessage): Boolean {
        val sent = client?.send(message) ?: false
        if (!sent) {
            emitMessage("Not connected to the server")
        }
        return sent
    }

    private fun onServerMessage(message: ServerMessage) {
        when (message) {
            is ServerMessage.Joined -> onJoined(message)
            is ServerMessage.Snapshot -> applySnapshot(message.snapshot, forceRebuild = false)
            is ServerMessage.MoveRejected -> {
                applySnapshot(message.snapshot, forceRebuild = true)
                emitMessage(rejectionText(message.reason))
            }
            is ServerMessage.Error -> onServerError(message)
            is ServerMessage.CameraConfig -> {
                iceServers = message.iceServers
                mutableState.update { it.copy(cameraError = message.relayError) }
                message.relayError?.let { relayError ->
                    cameraSession?.stop()
                    mutate { it.copy(camera = it.camera.copy(enabled = false, status = relayError)) }
                    emitMessage(relayError)
                }
            }
            is ServerMessage.CameraSignal -> receiveCameraSignal(message.kind, message.payload)
            ServerMessage.Pong -> Unit
        }
    }

    private fun onJoined(joined: ServerMessage.Joined) {
        stopCamera()
        iceServers = emptyList()
        token = joined.token
        sessionCode = joined.code
        prefs?.savedOnlineCode = joined.code
        prefs?.savedOnlineToken = joined.token
        prefs?.savedMode = GameMode.ONLINE.key
        prefs?.savedMoves = ""
        mutate {
            it.copy(
                board = it.board.copy(perspective = joined.side.toColor()),
                gameMode = GameMode.ONLINE,
                hasLocalGame = false,
                online = (it.online ?: OnlineUiState()).copy(code = joined.code, mySide = joined.side),
            )
        }
        applySnapshot(joined.snapshot, forceRebuild = true)
    }

    private fun onServerError(error: ServerMessage.Error) {
        val online = mutableState.value.online ?: return
        if (online.code == null) {
            failLobby(error.message)
            return
        }
        mutate { it.copy(online = online.copy(lastError = error.message)) }
        emitMessage(error.message)
        if (error.code == ErrorCode.GAME_NOT_FOUND || error.code == ErrorCode.INVALID_TOKEN) {
            closeSession()
            prefs?.savedOnlineCode = ""
            prefs?.savedOnlineToken = ""
            prefs?.savedMode = ""
            prefs?.savedMoves = ""
            game.reset()
            mutate {
                it.copy(
                    online = null,
                    gameMode = GameMode.LOCAL,
                    hasLocalGame = false,
                    lobbyError = error.message,
                    board = it.board.copy(hint = null, perspective = ChessColor.WHITE),
                )
            }
        }
    }

    private fun rejectionText(reason: ErrorCode): String =
        when (reason) {
            ErrorCode.ILLEGAL_MOVE -> "Move rejected: illegal move"
            ErrorCode.NOT_YOUR_TURN -> "Move rejected: not your turn"
            ErrorCode.GAME_OVER -> "Move rejected: game is over"
            else -> "Move rejected"
        }

    private fun applySnapshot(snapshot: GameSnapshot, forceRebuild: Boolean) {
        val previousResult = mutableState.value.online?.resultText
        val sync = syncMoves(snapshot.moves, forceRebuild)
        if (sync != Sync.NONE) {
            analysisJob?.cancel()
            analysisJob = null
        }
        clockAnchor = ClockAnchor(
            whiteMillis = snapshot.whiteMillis,
            blackMillis = snapshot.blackMillis,
            receivedAt = now(),
            running = snapshot.clockRunning && snapshot.result == null,
            turn = snapshot.sideToMove,
        )

        mutate { s ->
            val online = s.online ?: return@mutate s
            val me = online.mySide
            val white = me == Side.WHITE
            val moved = sync != Sync.NONE
            s.copy(
                board = if (moved) s.board.copy(hint = null) else s.board,
                engine = if (moved) EngineUiState() else s.engine,
                pendingPromotion = if (moved) null else s.pendingPromotion,
                online = online.copy(
                    myName = (if (white) snapshot.whiteName else snapshot.blackName) ?: online.myName,
                    opponentName = if (white) snapshot.blackName else snapshot.whiteName,
                    opponentConnected = if (white) snapshot.blackConnected else snapshot.whiteConnected,
                    whiteMillis = snapshot.whiteMillis,
                    blackMillis = snapshot.blackMillis,
                    timeControlLabel = snapshot.timeControl.label,
                    waitingForOpponent = !snapshot.started,
                    incomingDrawOffer = me != null && snapshot.drawOfferBy == me.opposite,
                    outgoingDrawOffer = me != null && snapshot.drawOfferBy == me,
                    incomingRematch = me != null && snapshot.rematchOfferBy == me.opposite,
                    outgoingRematch = me != null && snapshot.rematchOfferBy == me,
                    resultText = snapshot.result?.let { resultText(it, me) },
                ),
            )
        }
        restartTicker()

        if (snapshot.result != null) {
            prefs?.savedOnlineCode = ""
            prefs?.savedOnlineToken = ""
        }

        if (sync == Sync.APPENDED) {
            moveHaptic()
        } else if (previousResult == null && snapshot.result != null) {
            haptic(HapticKind.GAME_OVER)
        }
    }

    private fun syncMoves(moves: List<String>, forceRebuild: Boolean): Sync {
        if (!forceRebuild) {
            val local = game.moveHistory
            val localUci = local.map { it.move.uci }
            if (moves == localUci) {
                return Sync.NONE
            }
            if (isStaleEcho(moves, localUci, local.lastOrNull()?.move?.piece?.color)) {
                return Sync.NONE
            }
            if (moves.size == localUci.size + 1 && moves.subList(0, localUci.size) == localUci) {
                val move = game.findMoveByUci(moves.last())
                if (move != null && game.makeMove(move)) {
                    return Sync.APPENDED
                }
            }
        }
        rebuild(moves)
        return Sync.REBUILT
    }

    /** A snapshot sent before the server saw my optimistic move; the next one will contain it. */
    private fun isStaleEcho(moves: List<String>, local: List<String>, lastMover: ChessColor?): Boolean {
        val mine = mutableState.value.online?.myColor
        return mine != null &&
            lastMover == mine &&
            moves.size == local.size - 1 &&
            local.subList(0, moves.size) == moves
    }

    private fun rebuild(moves: List<String>) {
        game.reset()
        for (uci in moves) {
            val move = game.findMoveByUci(uci)
            if (move == null || !game.makeMove(move)) {
                emitMessage("Game state out of sync")
                return
            }
        }
    }

    private fun restartTicker() {
        tickerJob?.cancel()
        tickerJob = null
        val expired = publishClocks()
        if (expired || clockAnchor?.running != true) {
            return
        }
        tickerJob = viewModelScope.launch {
            do {
                delay(CLOCK_TICK_MS)
            } while (!publishClocks())
        }
    }

    /** Returns true once the ticking clock reached zero. */
    private fun publishClocks(): Boolean {
        val anchor = clockAnchor ?: return true
        val elapsed = if (anchor.running) max(0, now() - anchor.receivedAt) else 0
        val white = if (anchor.running && anchor.turn == Side.WHITE) max(0, anchor.whiteMillis - elapsed) else anchor.whiteMillis
        val black = if (anchor.running && anchor.turn == Side.BLACK) max(0, anchor.blackMillis - elapsed) else anchor.blackMillis
        mutableState.update { s ->
            val online = s.online ?: return@update s
            if (online.whiteMillis == white && online.blackMillis == black) {
                s
            } else {
                s.copy(online = online.copy(whiteMillis = white, blackMillis = black))
            }
        }
        val ticking = if (anchor.turn == Side.WHITE) white else black
        return anchor.running && ticking <= 0
    }
}

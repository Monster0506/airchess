package com.airchess.net

import com.airchess.protocol.ClientMessage
import com.airchess.protocol.Protocol
import com.airchess.protocol.ServerMessage
import com.airchess.platform.monotonicMillis
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException

private const val OUTBOX_CAPACITY = 64
private const val INBOX_CAPACITY = 64
private const val PING_INTERVAL_MS = 15_000L
private const val SILENCE_LIMIT_MS = 40_000L
private const val CLOSE_GRACE_MS = 1_000L

class KtorOnlineGameClient(
    private val http: HttpClient = platformHttpClient(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : OnlineGameClient {

    private val inbox = MutableSharedFlow<ServerMessage>(extraBufferCapacity = INBOX_CAPACITY)
    private val connection = MutableStateFlow(ConnectionState.DISCONNECTED)

    @Volatile
    private var session: Job? = null

    @Volatile
    private var outbox: Channel<ClientMessage>? = null

    override val incoming: Flow<ServerMessage> = inbox.asSharedFlow()

    override val state: StateFlow<ConnectionState> = connection.asStateFlow()

    override suspend fun connect(url: String) {
        session?.cancelAndJoin()
        connection.value = ConnectionState.CONNECTING

        val opened = CompletableDeferred<Unit>()
        val box = Channel<ClientMessage>(OUTBOX_CAPACITY)
        val job = scope.launch { run(url, box, opened) }
        session = job
        try {
            opened.await()
        } catch (e: CancellationException) {
            job.cancel()
            throw e
        }
    }

    override fun send(message: ClientMessage): Boolean =
        outbox?.trySend(message)?.isSuccess ?: false

    override fun close() {
        outbox?.close()
        outbox = null

        val job = session
        scope.launch {
            withTimeoutOrNull(CLOSE_GRACE_MS) { job?.join() }
            job?.cancel()
            http.close()
            scope.cancel()
        }
    }

    private suspend fun run(url: String, box: Channel<ClientMessage>, opened: CompletableDeferred<Unit>) {
        try {
            http.webSocket(url) {
                outbox = box
                connection.value = ConnectionState.CONNECTED
                opened.complete(Unit)
                serve(this, box)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            opened.completeExceptionally(e)
        } finally {
            opened.completeExceptionally(IOException("Connection closed"))
            if (outbox === box) {
                outbox = null
            }
            box.close()
            connection.value = ConnectionState.DISCONNECTED
        }
    }

    private suspend fun serve(socket: DefaultClientWebSocketSession, box: Channel<ClientMessage>) = coroutineScope {
        val heard = Heartbeat()

        val writer = launch {
            for (message in box) {
                socket.send(Frame.Text(Protocol.encode(message)))
            }
            socket.close()
        }
        val pinger = launch {
            while (true) {
                delay(PING_INTERVAL_MS)
                if (monotonicMillis() - heard.at > SILENCE_LIMIT_MS) {
                    throw IOException("Server stopped responding")
                }
                box.trySend(ClientMessage.Ping)
            }
        }

        try {
            for (frame in socket.incoming) {
                if (frame !is Frame.Text) {
                    continue
                }
                heard.at = monotonicMillis()
                val message = decode(frame.readText()) ?: continue
                inbox.emit(message)
            }
        } finally {
            writer.cancel()
            pinger.cancel()
        }
    }

    private class Heartbeat {
        @Volatile
        var at: Long = monotonicMillis()
    }

    private fun decode(text: String): ServerMessage? =
        try {
            Protocol.decodeServer(text)
        } catch (e: IllegalArgumentException) {
            null
        }
}

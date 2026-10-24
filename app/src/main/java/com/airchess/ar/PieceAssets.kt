package com.airchess.ar

import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import com.airchess.chess.ChessColor
import com.airchess.chess.ChessPiece
import com.airchess.chess.PieceType
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.model.Model
import io.github.sceneview.model.ModelInstance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Load progress of one asset; [Missing] means the file is absent or unreadable. */
sealed interface AssetState<out T> {
    data object Loading : AssetState<Nothing>
    data object Missing : AssetState<Nothing>
    data class Ready<T>(val value: T) : AssetState<T>
}

private const val TAG = "AirChessAssets"

/** Asset path of the board model. */
const val BOARD_MODEL_PATH = "models/board.glb"

/** Asset path of the model for [piece], e.g. `models/white_pawn.glb`. */
fun pieceModelPath(piece: ChessPiece): String {
    val color = if (piece.color == ChessColor.WHITE) "white" else "black"
    return "models/${color}_${piece.type.name.lowercase()}.glb"
}

/**
 * Loads each piece model once and hands out one fresh instance per piece on the board.
 * A node destroys the instance it wraps, so instances are never returned to a pool.
 */
class PieceAssets(private val loader: ModelLoader) {

    private val states = mutableStateMapOf<ChessPiece, AssetState<Model>>()
    private val primaryTaken = HashSet<ChessPiece>()

    /** Observable: reading it in composition recomposes when the model finishes loading. */
    fun state(piece: ChessPiece): AssetState<Model> = states[piece] ?: AssetState.Loading

    suspend fun loadAll() = coroutineScope {
        ALL_PIECES.forEach { piece ->
            launch { states[piece] = load(piece) }
        }
    }

    /** A new instance of [piece]'s model, or null when it is not loaded or cannot be instanced. */
    fun acquire(piece: ChessPiece): ModelInstance? {
        val model = (states[piece] as? AssetState.Ready)?.value ?: return null
        return if (primaryTaken.add(piece)) model.instance else loader.createInstance(model)
    }

    fun release() {
        states.values.forEach { state ->
            if (state is AssetState.Ready) {
                loader.destroyModel(state.value)
            }
        }
        states.clear()
    }

    private suspend fun load(piece: ChessPiece): AssetState<Model> {
        val path = pieceModelPath(piece)
        return try {
            loader.loadModel(path)?.let { AssetState.Ready(it) } ?: AssetState.Missing
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Could not load $path, using procedural piece", error)
            AssetState.Missing
        }
    }

    private companion object {
        val ALL_PIECES = ChessColor.entries.flatMap { color ->
            PieceType.entries.map { type -> ChessPiece(color, type) }
        }
    }
}

/** Loads [BOARD_MODEL_PATH] as a single instance, or [AssetState.Missing]. */
suspend fun ModelLoader.loadBoardModel(): AssetState<ModelInstance> {
    return try {
        loadModelInstance(BOARD_MODEL_PATH)?.let { AssetState.Ready(it) } ?: AssetState.Missing
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.w(TAG, "Could not load $BOARD_MODEL_PATH, using procedural board", error)
        AssetState.Missing
    }
}

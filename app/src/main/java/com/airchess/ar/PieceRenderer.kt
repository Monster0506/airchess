package com.airchess.ar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.airchess.chess.ChessPiece
import com.airchess.chess.ChessPosition
import com.airchess.chess.ChessSquare
import com.airchess.chess.PieceType
import io.github.sceneview.SceneScope
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.node.Node as NodeImpl

private const val NOT_STARTED = Long.MIN_VALUE
private const val HIDDEN_SCALE = 0.001f

private enum class MotionKind { MOVE, SPAWN, REMOVE }

private class Motion(
    val kind: MotionKind,
    val fromX: Float,
    val fromZ: Float,
    val toX: Float,
    val toZ: Float,
    val arcHeight: Float,
    val promoteTo: ChessPiece?,
) {
    var startNanos = NOT_STARTED
}

/**
 * One rendered piece. Its transform lives here and is pushed straight to the node from the frame
 * callback, so animating never recomposes anything; only [piece] (promotion) is Compose state.
 */
internal class PieceVisual(
    val id: Int,
    piece: ChessPiece,
    square: ChessSquare,
    private val geometry: BoardGeometry,
    spawnAnimated: Boolean,
    private val onRemoved: (PieceVisual) -> Unit,
) {
    var piece by mutableStateOf(piece)
        private set

    private var x: Float
    private var y = 0f
    private var z: Float
    private var scale = if (spawnAnimated) HIDDEN_SCALE else 1f
    private var pushedScale = Float.NaN

    private var node: NodeImpl? = null
    private var motion: Motion? = null
    private val tick: (Long) -> Unit = { nanos -> onFrame(nanos) }

    init {
        val start = geometry.squareToLocalPosition(square)
        x = start.x
        z = start.z
        if (spawnAnimated) {
            motion = Motion(MotionKind.SPAWN, x, z, x, z, 0f, null)
        }
    }

    val startPosition: Position = Position(x, 0f, z)
    val startScale: Scale = Scale(scale)

    fun bind(target: NodeImpl) {
        node = target
    }

    fun unbind() {
        node?.onFrame = null
        node = null
    }

    /** Writes the current state to the node and (re)arms the frame callback if needed. */
    fun sync() {
        val target = node ?: return
        push(target)
        target.onFrame = if (motion != null) tick else null
    }

    private fun push(target: NodeImpl) {
        target.position = Position(x, y, z)
        if (scale != pushedScale) {
            pushedScale = scale
            target.scale = Scale(scale)
        }
    }

    fun moveTo(square: ChessSquare, arc: Boolean, promoteTo: ChessPiece?) {
        finishMotion()
        val end = geometry.squareToLocalPosition(square)
        motion = Motion(MotionKind.MOVE, x, z, end.x, end.z, if (arc) PieceMotion.KNIGHT_ARC_HEIGHT else 0f, promoteTo)
        sync()
    }

    fun snapTo(square: ChessSquare, promoteTo: ChessPiece?) {
        finishMotion()
        val end = geometry.squareToLocalPosition(square)
        x = end.x
        z = end.z
        promoteTo?.let { piece = it }
        sync()
    }

    fun remove(animated: Boolean) {
        finishMotion()
        if (!animated) {
            onRemoved(this)
            return
        }
        motion = Motion(MotionKind.REMOVE, x, z, x, z, 0f, null)
        sync()
    }

    private fun finishMotion() {
        val current = motion ?: return
        if (current.kind == MotionKind.REMOVE) {
            return
        }
        write(current, 1f)
        current.promoteTo?.let { piece = it }
        motion = null
    }

    private fun onFrame(frameNanos: Long) {
        val current = motion ?: return
        if (current.startNanos == NOT_STARTED) {
            current.startNanos = frameNanos
        }

        val progress = PieceMotion.progress(frameNanos - current.startNanos)
        write(current, progress)
        node?.let { push(it) }

        if (progress < 1f) {
            return
        }

        motion = null
        current.promoteTo?.let { piece = it }
        node?.onFrame = null
        if (current.kind == MotionKind.REMOVE) {
            onRemoved(this)
        }
    }

    private fun write(motion: Motion, progress: Float) {
        val eased = PieceMotion.easeInOut(progress)
        when (motion.kind) {
            MotionKind.MOVE -> {
                x = motion.fromX + (motion.toX - motion.fromX) * eased
                z = motion.fromZ + (motion.toZ - motion.fromZ) * eased
                y = PieceMotion.arc(progress, motion.arcHeight)
            }
            MotionKind.SPAWN -> scale = maxOf(eased, HIDDEN_SCALE)
            MotionKind.REMOVE -> scale = maxOf(1f - eased, HIDDEN_SCALE)
        }
    }
}

/** Turns position changes into [PieceVisual]s and animations through the [PieceReconciler]. */
internal class PieceRenderer(private val geometry: BoardGeometry) {

    private val reconciler = PieceReconciler()
    private val byId = HashMap<Int, PieceVisual>()

    val visuals = mutableStateListOf<PieceVisual>()

    fun show(position: ChessPosition, animate: Boolean) {
        reconciler.update(position).forEach { transition -> apply(transition, animate) }
    }

    private fun apply(transition: PieceTransition, animate: Boolean) {
        val from = transition.from
        val to = transition.to

        if (from == null && to != null) {
            val visual = PieceVisual(transition.id, transition.piece, to, geometry, animate, ::dropped)
            byId[transition.id] = visual
            visuals += visual
            return
        }

        if (to == null) {
            byId.remove(transition.id)?.remove(animate)
            return
        }

        val visual = byId[transition.id] ?: return
        val promoted = transition.piece.takeIf { transition.promotedFrom != null }
        if (animate) {
            val mover = (transition.promotedFrom ?: transition.piece).type
            visual.moveTo(to, arc = mover == PieceType.KNIGHT, promoteTo = promoted)
        } else {
            visual.snapTo(to, promoted)
        }
    }

    private fun dropped(visual: PieceVisual) {
        byId.remove(visual.id)
        visuals.remove(visual)
    }
}

/** All pieces of [position], one node per piece, animated between positions. */
@Composable
internal fun SceneScope.PieceLayer(position: ChessPosition, geometry: BoardGeometry, materials: BoardMaterials) {
    val assets = remember(modelLoader) { PieceAssets(modelLoader) }
    LaunchedEffect(assets) { assets.loadAll() }
    DisposableEffect(assets) { onDispose { assets.release() } }

    val renderer = remember(geometry) { PieceRenderer(geometry).also { it.show(position, animate = false) } }
    LaunchedEffect(renderer, position) { renderer.show(position, animate = true) }

    renderer.visuals.forEach { visual ->
        key(visual.id) { PieceNode(visual, assets, materials) }
    }
}

@Composable
private fun SceneScope.PieceNode(visual: PieceVisual, assets: PieceAssets, materials: BoardMaterials) {
    Node(
        position = visual.startPosition,
        scale = visual.startScale,
        apply = { visual.bind(this) },
    ) {
        key(visual.piece) { PieceBody(visual.piece, assets, materials) }
    }

    SideEffect { visual.sync() }
    DisposableEffect(visual) { onDispose { visual.unbind() } }
}

@Composable
private fun SceneScope.PieceBody(piece: ChessPiece, assets: PieceAssets, materials: BoardMaterials) {
    when (assets.state(piece)) {
        AssetState.Loading -> Unit
        AssetState.Missing -> ProceduralPiece(piece, materials)
        is AssetState.Ready -> {
            val instance = remember(piece) { assets.acquire(piece) }
            if (instance == null) {
                ProceduralPiece(piece, materials)
            } else {
                ModelNode(modelInstance = instance, autoAnimate = false)
            }
        }
    }
}

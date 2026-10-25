package com.airchess.ios

import com.airchess.ar.ArrowLayout
import com.airchess.ar.BoardGeometry
import com.airchess.ar.BoardPicker
import com.airchess.ar.CameraAccess
import com.airchess.ar.PieceMotion
import com.airchess.chess.ChessSquare
import com.airchess.chess.PieceType
import platform.UIKit.UIViewController

/*
 * Kotlin <-> Swift board bridge. Everything here is flat on purpose: squares are Ints
 * (a1 = 0 .. h8 = 63), enums are plain, lists hold final classes. Swift implements the
 * interfaces; Kotlin owns all chess knowledge and tells Swift what to draw.
 */

/** Square value meaning "none" in [BoardFrame]. */
const val NO_SQUARE = -1

enum class BoardMode { THREE_D, AR }

enum class PieceSide { WHITE, BLACK }

enum class PieceKind { PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING }

/** What a tinted square is tinted for. */
enum class MarkKind { SELECTED, CHECK, HINT, LAST_MOVE, THREATENED }

/** A piece with a stable [id]: the same id on a new square means the piece moved. */
class PieceFrame(val id: Int, val square: Int, val kind: PieceKind, val side: PieceSide)

/** One tinted square; at most one mark per square, already prioritized. */
class MarkFrame(val square: Int, val kind: MarkKind)

/** A legal destination of the selected piece: a ring when it captures, a dot otherwise. */
class DestinationFrame(val square: Int, val capture: Boolean)

/** Everything the board shows at one moment. Swift diffs it against what is on screen. */
class BoardFrame(
    val pieces: List<PieceFrame>,
    val marks: List<MarkFrame>,
    val destinations: List<DestinationFrame>,
    val hintFrom: Int,
    val hintTo: Int,
    val perspective: PieceSide,
    /** False for the first frame and for jumps: pieces appear without animation. */
    val animate: Boolean,
)

/** A flat bar of the hint arrow in board-local meters, yaw in degrees about +Y. */
class ArrowBar(val centerX: Float, val centerZ: Float, val length: Float, val yawDegrees: Float)

/** Board-local geometry and motion math, so Swift never duplicates numbers owned by shared code. */
class BoardLayout {
    private val geometry = BoardGeometry()

    val boardSize: Float = geometry.boardSize
    val squareSize: Float = geometry.squareSize
    val slideSeconds: Double = PieceMotion.DURATION_NANOS / NANOS_PER_SECOND
    val knightArcHeight: Float = PieceMotion.KNIGHT_ARC_HEIGHT
    val arrowBarWidth: Float = geometry.squareSize * ArrowLayout.BAR_WIDTH

    fun centerX(square: Int): Float = geometry.squareToLocalPosition(ChessSquare.of(square)).x

    fun centerZ(square: Int): Float = geometry.squareToLocalPosition(ChessSquare.of(square)).z

    /** Square under the board-local point, or [NO_SQUARE] outside the board. */
    fun squareAt(x: Float, z: Float): Int = geometry.localPositionToSquare(x, z)?.index ?: NO_SQUARE

    fun pieceHeight(kind: PieceKind): Float = BoardPicker.height(kind.toType())

    fun ease(progress: Float): Float = PieceMotion.easeInOut(progress)

    fun arc(progress: Float, height: Float): Float = PieceMotion.arc(progress, height)

    fun arrow(from: Int, to: Int): List<ArrowBar> {
        val start = geometry.squareToLocalPosition(ChessSquare.of(from))
        val end = geometry.squareToLocalPosition(ChessSquare.of(to))
        return ArrowLayout.segments(start, end, geometry.squareSize).map {
            ArrowBar(it.centerX, it.centerZ, it.length, it.yawDegrees)
        }
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}

/** Swift reports what the user and the AR session did. */
interface BoardListener {
    fun onSquareTapped(square: Int)

    /** Raw per-frame AR facts; Kotlin folds them into the view model's ArStatus. */
    fun onArFrame(
        timestampNanos: Long,
        cameraTracking: Boolean,
        failure: String?,
        planeCount: Int,
        anchorState: String,
        anchored: Boolean,
    )

    /** The AR session could not run; Kotlin falls back to the gate's unsupported screen. */
    fun onSessionFailed(message: String)
}

/** One live board view, either the 3D scene or the AR scene. */
interface BoardController {
    val viewController: UIViewController

    fun render(frame: BoardFrame)

    fun pause()

    fun resume()

    fun dispose()

    /** AR only: drop the anchor and return to surface placement. */
    fun resetPlacement()
}

/** One-shot callback Swift invokes when the camera permission prompt closes. */
interface CameraPromptCallback {
    fun onAnswered()
}

/** Swift side of the board: creates views and answers device questions for the AR gate. */
interface BoardControllerFactory {
    val arSupported: Boolean

    fun create(mode: BoardMode, layout: BoardLayout, listener: BoardListener): BoardController

    fun cameraAccess(): CameraAccess

    fun requestCamera(callback: CameraPromptCallback)

    fun openSettings()
}

internal fun PieceKind.toType(): PieceType = when (this) {
    PieceKind.PAWN -> PieceType.PAWN
    PieceKind.KNIGHT -> PieceType.KNIGHT
    PieceKind.BISHOP -> PieceType.BISHOP
    PieceKind.ROOK -> PieceType.ROOK
    PieceKind.QUEEN -> PieceType.QUEEN
    PieceKind.KING -> PieceType.KING
}

internal fun PieceType.toKind(): PieceKind = when (this) {
    PieceType.PAWN -> PieceKind.PAWN
    PieceType.KNIGHT -> PieceKind.KNIGHT
    PieceType.BISHOP -> PieceKind.BISHOP
    PieceType.ROOK -> PieceKind.ROOK
    PieceType.QUEEN -> PieceKind.QUEEN
    PieceType.KING -> PieceKind.KING
}

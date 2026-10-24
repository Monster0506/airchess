package com.airchess.chess

/**
 * Stateless chess rules: move generation, attack detection, move application and game status.
 * Works on indices internally (a1 = 0 .. h8 = 63) to keep generation allocation-light.
 */
object ChessRules {

    private const val SIZE = ChessSquare.BOARD_SIZE
    private const val COUNT = ChessSquare.COUNT
    private const val DOUBLE_PUSH_STEP = 2
    private const val MOVE_CAPACITY = 48
    private const val FIFTY_MOVE_HALFMOVES = 100
    private const val REPETITION_LIMIT = 3
    private const val QUEENSIDE_ROOK_OFFSET = -4
    private const val KINGSIDE_ROOK_OFFSET = 3

    private const val WHITE_KING_HOME = 4
    private const val BLACK_KING_HOME = 60
    private const val WHITE_QUEENSIDE_ROOK = 0
    private const val WHITE_KINGSIDE_ROOK = 7
    private const val BLACK_QUEENSIDE_ROOK = 56
    private const val BLACK_KINGSIDE_ROOK = 63

    private val KNIGHT_STEPS = intArrayOf(1, 2, 2, 1, 2, -1, 1, -2, -1, -2, -2, -1, -2, 1, -1, 2)
    private val KING_STEPS = intArrayOf(1, 0, 1, 1, 0, 1, -1, 1, -1, 0, -1, -1, 0, -1, 1, -1)
    private val ORTHOGONAL = intArrayOf(1, 0, -1, 0, 0, 1, 0, -1)
    private val DIAGONAL = intArrayOf(1, 1, 1, -1, -1, 1, -1, -1)

    fun legalMoves(position: ChessPosition): List<ChessMove> {
        val board = position.board.toTypedArray()
        val us = position.sideToMove
        val kingAtStart = kingIndex(board, us)
        val result = ArrayList<ChessMove>(MOVE_CAPACITY)

        for (move in pseudoLegalMoves(position, board)) {
            val kingAt = if (move.piece.type == PieceType.KING) move.to.index else kingAtStart
            if (kingAt < 0 || !exposesKing(board, move, kingAt, us.opposite)) {
                result.add(move)
            }
        }
        addCastling(position, board, result)
        return result
    }

    fun legalMovesFrom(position: ChessPosition, from: ChessSquare): List<ChessMove> =
        legalMoves(position).filter { it.from == from }

    fun applyMove(position: ChessPosition, move: ChessMove): ChessPosition {
        val board = position.board.toTypedArray()
        val moved = if (move.promotion != null) ChessPiece(move.piece.color, move.promotion) else move.piece

        board[move.from.index] = null
        board[move.to.index] = moved
        if (move.kind == MoveKind.EN_PASSANT) {
            board[(move.captureSquare ?: move.to).index] = null
        }
        moveCastlingRook(board, move)

        val enPassant = if (move.kind == MoveKind.DOUBLE_PAWN_PUSH) {
            ChessSquare.at(move.from.file, (move.from.rank + move.to.rank) / 2)
        } else {
            null
        }
        val resetsClock = move.piece.type == PieceType.PAWN || move.isCapture

        return ChessPosition(
            board = board.asList(),
            sideToMove = position.sideToMove.opposite,
            castling = updateCastling(position.castling, move),
            enPassant = enPassant,
            halfmoveClock = if (resetsClock) 0 else position.halfmoveClock + 1,
            fullmoveNumber = position.fullmoveNumber + if (position.sideToMove == ChessColor.BLACK) 1 else 0,
        )
    }

    fun isInCheck(position: ChessPosition, color: ChessColor): Boolean {
        val board = position.board.toTypedArray()
        val king = kingIndex(board, color)
        return king >= 0 && isAttacked(board, king, color.opposite)
    }

    /** Squares attacked by [by], including squares occupied by its own pieces (defended squares). */
    fun attackedSquares(position: ChessPosition, by: ChessColor): Set<ChessSquare> {
        val board = position.board.toTypedArray()
        val result = HashSet<ChessSquare>()
        for (from in 0 until COUNT) {
            val piece = board[from] ?: continue
            if (piece.color != by) {
                continue
            }
            forEachAttack(board, from, piece) { result.add(ChessSquare.of(it)) }
        }
        return result
    }

    fun status(position: ChessPosition, legalMoves: List<ChessMove>, repetitions: Int): GameStatus {
        val inCheck = isInCheck(position, position.sideToMove)
        if (legalMoves.isEmpty()) {
            return if (inCheck) GameStatus.CHECKMATE else GameStatus.STALEMATE
        }
        if (hasInsufficientMaterial(position)) {
            return GameStatus.DRAW_INSUFFICIENT_MATERIAL
        }
        if (repetitions >= REPETITION_LIMIT) {
            return GameStatus.DRAW_THREEFOLD_REPETITION
        }
        if (position.halfmoveClock >= FIFTY_MOVE_HALFMOVES) {
            return GameStatus.DRAW_FIFTY_MOVES
        }
        return if (inCheck) GameStatus.CHECK else GameStatus.IN_PROGRESS
    }

    fun status(position: ChessPosition): GameStatus = status(position, legalMoves(position), 1)

    /**
     * Identity of a position for repetition purposes: placement, side, castling and an
     * en passant square only when a legal en passant capture actually exists.
     */
    fun repetitionKey(position: ChessPosition, legalMoves: List<ChessMove>): String {
        val hasEnPassant = legalMoves.any { it.kind == MoveKind.EN_PASSANT }
        val normalized = position.copy(
            enPassant = position.enPassant.takeIf { hasEnPassant },
            halfmoveClock = 0,
            fullmoveNumber = 1,
        )
        return Fen.export(normalized)
    }

    fun hasInsufficientMaterial(position: ChessPosition): Boolean {
        val minors = ArrayList<Pair<ChessSquare, ChessPiece>>(2)
        for ((square, piece) in position.pieces()) {
            when (piece.type) {
                PieceType.KING -> Unit
                PieceType.KNIGHT, PieceType.BISHOP -> minors.add(square to piece)
                else -> return false
            }
        }
        if (minors.size <= 1) {
            return true
        }
        val allBishops = minors.all { it.second.type == PieceType.BISHOP }
        return allBishops && minors.map { it.first.isLight }.distinct().size == 1
    }

    private fun kingIndex(board: Array<ChessPiece?>, color: ChessColor): Int {
        for (index in 0 until COUNT) {
            val piece = board[index]
            if (piece != null && piece.color == color && piece.type == PieceType.KING) {
                return index
            }
        }
        return -1
    }

    private fun inBounds(file: Int, rank: Int): Boolean = file in 0 until SIZE && rank in 0 until SIZE

    private fun pawnDirection(color: ChessColor): Int = if (color == ChessColor.WHITE) 1 else -1

    private fun isAttacked(board: Array<ChessPiece?>, square: Int, by: ChessColor): Boolean {
        val file = square % SIZE
        val rank = square / SIZE

        val pawnRank = rank - pawnDirection(by)
        for (pawnFile in intArrayOf(file - 1, file + 1)) {
            if (inBounds(pawnFile, pawnRank) && isPiece(board[pawnRank * SIZE + pawnFile], by, PieceType.PAWN)) {
                return true
            }
        }
        if (attackedByStepper(board, file, rank, by, PieceType.KNIGHT, KNIGHT_STEPS)) {
            return true
        }
        if (attackedByStepper(board, file, rank, by, PieceType.KING, KING_STEPS)) {
            return true
        }
        return attackedBySlider(board, file, rank, by, ORTHOGONAL, PieceType.ROOK) ||
            attackedBySlider(board, file, rank, by, DIAGONAL, PieceType.BISHOP)
    }

    private fun isPiece(piece: ChessPiece?, color: ChessColor, type: PieceType): Boolean =
        piece != null && piece.color == color && piece.type == type

    private fun attackedByStepper(
        board: Array<ChessPiece?>,
        file: Int,
        rank: Int,
        by: ChessColor,
        type: PieceType,
        steps: IntArray,
    ): Boolean {
        for (i in steps.indices step 2) {
            val f = file + steps[i]
            val r = rank + steps[i + 1]
            if (inBounds(f, r) && isPiece(board[r * SIZE + f], by, type)) {
                return true
            }
        }
        return false
    }

    private fun attackedBySlider(
        board: Array<ChessPiece?>,
        file: Int,
        rank: Int,
        by: ChessColor,
        directions: IntArray,
        sliderType: PieceType,
    ): Boolean {
        for (i in directions.indices step 2) {
            var f = file + directions[i]
            var r = rank + directions[i + 1]
            while (inBounds(f, r)) {
                val piece = board[r * SIZE + f]
                if (piece != null) {
                    if (piece.color == by && (piece.type == sliderType || piece.type == PieceType.QUEEN)) {
                        return true
                    }
                    break
                }
                f += directions[i]
                r += directions[i + 1]
            }
        }
        return false
    }

    /** Calls [action] for every square the piece on [from] attacks (pawns: diagonals only). */
    private fun forEachAttack(board: Array<ChessPiece?>, from: Int, piece: ChessPiece, action: (Int) -> Unit) {
        val file = from % SIZE
        val rank = from / SIZE
        when (piece.type) {
            PieceType.PAWN -> {
                val target = rank + pawnDirection(piece.color)
                for (f in intArrayOf(file - 1, file + 1)) {
                    if (inBounds(f, target)) {
                        action(target * SIZE + f)
                    }
                }
            }
            PieceType.KNIGHT -> forEachStep(file, rank, KNIGHT_STEPS, action)
            PieceType.KING -> forEachStep(file, rank, KING_STEPS, action)
            PieceType.BISHOP -> forEachSlide(board, file, rank, DIAGONAL, action)
            PieceType.ROOK -> forEachSlide(board, file, rank, ORTHOGONAL, action)
            PieceType.QUEEN -> {
                forEachSlide(board, file, rank, DIAGONAL, action)
                forEachSlide(board, file, rank, ORTHOGONAL, action)
            }
        }
    }

    private fun forEachStep(file: Int, rank: Int, steps: IntArray, action: (Int) -> Unit) {
        for (i in steps.indices step 2) {
            val f = file + steps[i]
            val r = rank + steps[i + 1]
            if (inBounds(f, r)) {
                action(r * SIZE + f)
            }
        }
    }

    private fun forEachSlide(
        board: Array<ChessPiece?>,
        file: Int,
        rank: Int,
        directions: IntArray,
        action: (Int) -> Unit,
    ) {
        for (i in directions.indices step 2) {
            var f = file + directions[i]
            var r = rank + directions[i + 1]
            while (inBounds(f, r)) {
                val index = r * SIZE + f
                action(index)
                if (board[index] != null) {
                    break
                }
                f += directions[i]
                r += directions[i + 1]
            }
        }
    }

    private fun pseudoLegalMoves(position: ChessPosition, board: Array<ChessPiece?>): List<ChessMove> {
        val us = position.sideToMove
        val moves = ArrayList<ChessMove>(MOVE_CAPACITY)

        for (from in 0 until COUNT) {
            val piece = board[from] ?: continue
            if (piece.color != us) {
                continue
            }
            if (piece.type == PieceType.PAWN) {
                addPawnMoves(position, board, from, piece, moves)
                continue
            }
            forEachAttack(board, from, piece) { to ->
                val target = board[to]
                if (target == null || target.color != us) {
                    moves.add(ChessMove(ChessSquare.of(from), ChessSquare.of(to), piece, target))
                }
            }
        }
        return moves
    }

    private fun addPawnMoves(
        position: ChessPosition,
        board: Array<ChessPiece?>,
        from: Int,
        pawn: ChessPiece,
        moves: MutableList<ChessMove>,
    ) {
        val file = from % SIZE
        val rank = from / SIZE
        val direction = pawnDirection(pawn.color)
        val startRank = if (pawn.color == ChessColor.WHITE) 1 else SIZE - 2
        val forward = rank + direction
        if (forward !in 0 until SIZE) {
            return
        }

        val oneStep = forward * SIZE + file
        if (board[oneStep] == null) {
            addPawnMove(moves, from, oneStep, pawn, null, MoveKind.NORMAL)
            val twoStep = (rank + DOUBLE_PUSH_STEP * direction) * SIZE + file
            if (rank == startRank && board[twoStep] == null) {
                addPawnMove(moves, from, twoStep, pawn, null, MoveKind.DOUBLE_PAWN_PUSH)
            }
        }

        for (targetFile in intArrayOf(file - 1, file + 1)) {
            if (!inBounds(targetFile, forward)) {
                continue
            }
            val to = forward * SIZE + targetFile
            val target = board[to]
            if (target != null && target.color != pawn.color) {
                addPawnMove(moves, from, to, pawn, target, MoveKind.NORMAL)
                continue
            }
            if (target == null && position.enPassant?.index == to) {
                val victim = board[rank * SIZE + targetFile]
                if (isPiece(victim, pawn.color.opposite, PieceType.PAWN)) {
                    addPawnMove(moves, from, to, pawn, victim, MoveKind.EN_PASSANT)
                }
            }
        }
    }

    private fun addPawnMove(
        moves: MutableList<ChessMove>,
        from: Int,
        to: Int,
        pawn: ChessPiece,
        captured: ChessPiece?,
        kind: MoveKind,
    ) {
        val fromSquare = ChessSquare.of(from)
        val toSquare = ChessSquare.of(to)
        val lastRank = if (pawn.color == ChessColor.WHITE) SIZE - 1 else 0
        if (toSquare.rank != lastRank) {
            moves.add(ChessMove(fromSquare, toSquare, pawn, captured, null, kind))
            return
        }
        for (promotion in PieceType.PROMOTIONS) {
            moves.add(ChessMove(fromSquare, toSquare, pawn, captured, promotion, kind))
        }
    }

    /** Plays the move on [board], tests whether the king is attacked, then restores [board]. */
    private fun exposesKing(
        board: Array<ChessPiece?>,
        move: ChessMove,
        kingAt: Int,
        enemy: ChessColor,
    ): Boolean {
        val from = move.from.index
        val to = move.to.index
        val captureAt = (move.captureSquare ?: move.to).index
        val savedFrom = board[from]
        val savedTo = board[to]
        val savedCapture = board[captureAt]

        board[captureAt] = null
        board[to] = savedFrom
        board[from] = null
        val exposed = isAttacked(board, kingAt, enemy)

        board[from] = savedFrom
        board[to] = savedTo
        board[captureAt] = savedCapture
        return exposed
    }

    private fun addCastling(position: ChessPosition, board: Array<ChessPiece?>, moves: MutableList<ChessMove>) {
        val us = position.sideToMove
        val white = us == ChessColor.WHITE
        val home = if (white) WHITE_KING_HOME else BLACK_KING_HOME
        val king = board[home] ?: return
        if (!isPiece(king, us, PieceType.KING) || isAttacked(board, home, us.opposite)) {
            return
        }

        val rights = position.castling
        val kingside = if (white) rights.whiteKingside else rights.blackKingside
        val queenside = if (white) rights.whiteQueenside else rights.blackQueenside

        if (kingside && canCastle(board, home, home + KINGSIDE_ROOK_OFFSET, 1, us)) {
            moves.add(castleMove(home, home + DOUBLE_PUSH_STEP, king, MoveKind.CASTLE_KINGSIDE))
        }
        if (queenside && canCastle(board, home, home + QUEENSIDE_ROOK_OFFSET, -1, us)) {
            moves.add(castleMove(home, home - DOUBLE_PUSH_STEP, king, MoveKind.CASTLE_QUEENSIDE))
        }
    }

    private fun castleMove(from: Int, to: Int, king: ChessPiece, kind: MoveKind) =
        ChessMove(ChessSquare.of(from), ChessSquare.of(to), king, null, null, kind)

    /** Rook present, squares between empty, king's transit and landing squares not attacked. */
    private fun canCastle(board: Array<ChessPiece?>, home: Int, rookAt: Int, step: Int, us: ChessColor): Boolean {
        if (!isPiece(board[rookAt], us, PieceType.ROOK)) {
            return false
        }
        var square = home + step
        while (square != rookAt) {
            if (board[square] != null) {
                return false
            }
            square += step
        }
        return !isAttacked(board, home + step, us.opposite) &&
            !isAttacked(board, home + DOUBLE_PUSH_STEP * step, us.opposite)
    }

    private fun moveCastlingRook(board: Array<ChessPiece?>, move: ChessMove) {
        val to = move.to.index
        val rookFrom: Int
        val rookTo: Int
        when (move.kind) {
            MoveKind.CASTLE_KINGSIDE -> {
                rookFrom = to + 1
                rookTo = to - 1
            }
            MoveKind.CASTLE_QUEENSIDE -> {
                rookFrom = to - DOUBLE_PUSH_STEP
                rookTo = to + 1
            }
            else -> return
        }
        board[rookTo] = board[rookFrom]
        board[rookFrom] = null
    }

    private fun updateCastling(rights: CastlingRights, move: ChessMove): CastlingRights =
        revokeFor(revokeFor(rights, move.from.index), move.to.index)

    private fun revokeFor(rights: CastlingRights, square: Int): CastlingRights = when (square) {
        WHITE_KING_HOME -> rights.copy(whiteKingside = false, whiteQueenside = false)
        BLACK_KING_HOME -> rights.copy(blackKingside = false, blackQueenside = false)
        WHITE_KINGSIDE_ROOK -> rights.copy(whiteKingside = false)
        WHITE_QUEENSIDE_ROOK -> rights.copy(whiteQueenside = false)
        BLACK_KINGSIDE_ROOK -> rights.copy(blackKingside = false)
        BLACK_QUEENSIDE_ROOK -> rights.copy(blackQueenside = false)
        else -> rights
    }
}

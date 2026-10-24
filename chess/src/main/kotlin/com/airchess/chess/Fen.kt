package com.airchess.chess

object Fen {

    const val START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    private const val MIN_FIELDS = 4
    private const val MAX_FIELDS = 6
    private const val SIZE = ChessSquare.BOARD_SIZE

    /** Parses and validates [fen]; throws [IllegalArgumentException] when malformed. */
    fun parse(fen: String): ChessPosition {
        val fields = fen.trim().split(Regex("\\s+"))
        require(fields.size in MIN_FIELDS..MAX_FIELDS) { "FEN needs $MIN_FIELDS to $MAX_FIELDS fields: '$fen'" }

        val board = parseBoard(fields[0])
        val sideToMove = when (fields[1]) {
            "w" -> ChessColor.WHITE
            "b" -> ChessColor.BLACK
            else -> throw IllegalArgumentException("Invalid side to move: ${fields[1]}")
        }
        val position = ChessPosition(
            board = board,
            sideToMove = sideToMove,
            castling = parseCastling(fields[2]),
            enPassant = parseEnPassant(fields[3], sideToMove),
            halfmoveClock = fields.getOrNull(4)?.let { parseCounter(it, 0, "halfmove clock") } ?: 0,
            fullmoveNumber = fields.getOrNull(5)?.let { parseCounter(it, 1, "fullmove number") } ?: 1,
        )
        validateKings(position)
        return position
    }

    fun export(position: ChessPosition): String {
        val placement = (SIZE - 1 downTo 0).joinToString("/") { rank -> exportRank(position, rank) }
        val side = if (position.sideToMove == ChessColor.WHITE) "w" else "b"
        val ep = position.enPassant?.algebraic ?: "-"
        return "$placement $side ${exportCastling(position.castling)} $ep " +
            "${position.halfmoveClock} ${position.fullmoveNumber}"
    }

    private fun parseBoard(placement: String): List<ChessPiece?> {
        val ranks = placement.split("/")
        require(ranks.size == SIZE) { "FEN needs $SIZE ranks: '$placement'" }

        val board = arrayOfNulls<ChessPiece>(ChessSquare.COUNT)
        for ((row, text) in ranks.withIndex()) {
            val rank = SIZE - 1 - row
            var file = 0
            for (c in text) {
                if (c in '1'..'8') {
                    file += c - '0'
                    continue
                }
                val piece = requireNotNull(ChessPiece.fromFenChar(c)) { "Invalid piece '$c' in FEN" }
                require(file < SIZE) { "Rank too long: '$text'" }
                require(piece.type != PieceType.PAWN || rank != 0 && rank != SIZE - 1) {
                    "Pawn on back rank: '$text'"
                }
                board[rank * SIZE + file] = piece
                file++
            }
            require(file == SIZE) { "Rank must cover $SIZE files: '$text'" }
        }
        return board.asList()
    }

    private fun parseCastling(field: String): CastlingRights {
        if (field == "-") {
            return CastlingRights.NONE
        }
        require(field.isNotEmpty() && field.all { it in "KQkq" } && field.toSet().size == field.length) {
            "Invalid castling field: $field"
        }
        return CastlingRights(
            whiteKingside = 'K' in field,
            whiteQueenside = 'Q' in field,
            blackKingside = 'k' in field,
            blackQueenside = 'q' in field,
        )
    }

    private fun parseEnPassant(field: String, sideToMove: ChessColor): ChessSquare? {
        if (field == "-") {
            return null
        }
        val square = requireNotNull(ChessSquare.parse(field)) { "Invalid en passant square: $field" }
        val expectedRank = if (sideToMove == ChessColor.WHITE) SIZE - 3 else 2
        require(square.rank == expectedRank) { "En passant square $field impossible for $sideToMove to move" }
        return square
    }

    private fun parseCounter(field: String, min: Int, name: String): Int {
        val value = requireNotNull(field.toIntOrNull()) { "Invalid $name: $field" }
        require(value >= min) { "Invalid $name: $field" }
        return value
    }

    private fun validateKings(position: ChessPosition) {
        for (color in ChessColor.entries) {
            val kings = position.pieces().count { it.second == ChessPiece(color, PieceType.KING) }
            require(kings == 1) { "Expected exactly one $color king, found $kings" }
        }
        require(!ChessRules.isInCheck(position, position.sideToMove.opposite)) {
            "Side not to move is in check"
        }
    }

    private fun exportRank(position: ChessPosition, rank: Int): String {
        val builder = StringBuilder()
        var empty = 0
        for (file in 0 until SIZE) {
            val piece = position.board[rank * SIZE + file]
            if (piece == null) {
                empty++
                continue
            }
            if (empty > 0) {
                builder.append(empty)
                empty = 0
            }
            builder.append(piece.fenChar)
        }
        if (empty > 0) {
            builder.append(empty)
        }
        return builder.toString()
    }

    private fun exportCastling(rights: CastlingRights): String {
        val text = buildString {
            if (rights.whiteKingside) {
                append('K')
            }
            if (rights.whiteQueenside) {
                append('Q')
            }
            if (rights.blackKingside) {
                append('k')
            }
            if (rights.blackQueenside) {
                append('q')
            }
        }
        return text.ifEmpty { "-" }
    }
}

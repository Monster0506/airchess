package com.airchess.chess

enum class GameStatus {
    IN_PROGRESS,
    CHECK,
    CHECKMATE,
    STALEMATE,
    DRAW_FIFTY_MOVES,
    DRAW_INSUFFICIENT_MATERIAL,
    DRAW_THREEFOLD_REPETITION;

    val isGameOver: Boolean
        get() = this != IN_PROGRESS && this != CHECK

    val isCheck: Boolean
        get() = this == CHECK || this == CHECKMATE
}

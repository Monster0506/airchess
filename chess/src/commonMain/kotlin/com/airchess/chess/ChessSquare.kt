package com.airchess.chess

import kotlin.jvm.JvmInline

/**
 * One of the 64 board squares. Index layout: a1 = 0, b1 = 1, ..., h1 = 7, a2 = 8, ..., h8 = 63.
 * File and rank are zero-based (file 0 = 'a', rank 0 = '1').
 */
@JvmInline
value class ChessSquare private constructor(val index: Int) {

    val file: Int
        get() = index % BOARD_SIZE

    val rank: Int
        get() = index / BOARD_SIZE

    val algebraic: String
        get() = "${'a' + file}${'1' + rank}"

    val isLight: Boolean
        get() = (file + rank) % 2 == 1

    fun offset(dFile: Int, dRank: Int): ChessSquare? = at(file + dFile, rank + dRank)

    override fun toString(): String = algebraic

    companion object {
        const val BOARD_SIZE = 8
        const val COUNT = BOARD_SIZE * BOARD_SIZE

        val ALL: List<ChessSquare> = List(COUNT) { ChessSquare(it) }

        fun of(index: Int): ChessSquare {
            require(index in 0 until COUNT) { "Square index out of range: $index" }
            return ALL[index]
        }

        fun at(file: Int, rank: Int): ChessSquare? {
            if (file !in 0 until BOARD_SIZE || rank !in 0 until BOARD_SIZE) {
                return null
            }
            return ALL[rank * BOARD_SIZE + file]
        }

        fun parse(algebraic: String): ChessSquare? {
            if (algebraic.length != 2) {
                return null
            }
            return at(algebraic[0].lowercaseChar() - 'a', algebraic[1] - '1')
        }

        fun fromAlgebraic(algebraic: String): ChessSquare =
            requireNotNull(parse(algebraic)) { "Invalid square: $algebraic" }

        val A1 = of(0)
        val B1 = of(1)
        val C1 = of(2)
        val D1 = of(3)
        val E1 = of(4)
        val F1 = of(5)
        val G1 = of(6)
        val H1 = of(7)
        val A8 = of(56)
        val B8 = of(57)
        val C8 = of(58)
        val D8 = of(59)
        val E8 = of(60)
        val F8 = of(61)
        val G8 = of(62)
        val H8 = of(63)
    }
}

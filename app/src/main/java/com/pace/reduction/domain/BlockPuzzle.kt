package com.pace.reduction.domain

import kotlin.random.Random

/**
 * A falling-block puzzle, as a pure state machine.
 *
 * This is the one tool in the kit with a specific mechanism behind it rather than general calm.
 * Skorka-Brown et al. found that a few minutes of a falling-block puzzle measurably cut cravings
 * for cigarettes, food and drink, and the proposed reason is competition for visuospatial working
 * memory: a craving is largely an *image* of smoking, and the image cannot be held while the same
 * faculty is busy fitting shapes. So the demand on the eye is the active ingredient, and the game
 * is tuned for occupation rather than for score — a narrow board, a forgiving speed, and a board
 * that resets itself instead of ending, because "you lose" is not a thing to hand someone who is
 * two minutes into resisting a cigarette.
 *
 * All of it is deterministic given a seed, which is what makes it testable without a screen.
 */
data class BlockPuzzleState(
    /** Row-major, [WIDTH] * [HEIGHT]; 0 is empty, otherwise the piece kind + 1. */
    val cells: List<Int>,
    val piece: ActivePiece?,
    val nextKind: Int,
    val linesCleared: Int,
    /** Counts full boards, so the tool can say "cleared and carried on" rather than "game over". */
    val resets: Int,
    val seed: Long,
) {
    /** The board with the falling piece painted in, which is what a renderer actually wants. */
    fun render(): List<Int> {
        val piece = piece ?: return cells
        val out = cells.toMutableList()
        BlockPuzzle.cellsOf(piece).forEach { (x, y) ->
            if (x in 0 until BlockPuzzle.WIDTH && y in 0 until BlockPuzzle.HEIGHT) {
                out[y * BlockPuzzle.WIDTH + x] = piece.kind + 1
            }
        }
        return out
    }
}

data class ActivePiece(val kind: Int, val rotation: Int, val x: Int, val y: Int)

object BlockPuzzle {
    const val WIDTH = 8
    const val HEIGHT = 14

    /**
     * The seven tetrominoes, each as its cells inside a square box of [boxSizes] a side. Rotation
     * is computed from these rather than stored per orientation, so a piece cannot be defined
     * inconsistently across its four turns.
     */
    private val shapes: List<List<Pair<Int, Int>>> = listOf(
        listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1), // I
        listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1), // O
        listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1), // T
        listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1), // S
        listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1), // Z
        listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1), // J
        listOf(2 to 0, 0 to 1, 1 to 1, 2 to 1), // L
    )
    private val boxSizes = listOf(4, 2, 3, 3, 3, 3, 3)

    val kindCount: Int get() = shapes.size

    fun start(seed: Long): BlockPuzzleState {
        val random = Random(seed)
        val first = random.nextInt(kindCount)
        val next = random.nextInt(kindCount)
        return spawn(
            BlockPuzzleState(
                cells = List(WIDTH * HEIGHT) { 0 },
                piece = null,
                nextKind = next,
                linesCleared = 0,
                resets = 0,
                seed = random.nextLong(),
            ),
            first,
        )
    }

    /** Board coordinates the piece currently occupies. */
    fun cellsOf(piece: ActivePiece): List<Pair<Int, Int>> {
        val size = boxSizes[piece.kind]
        return shapes[piece.kind].map { (x, y) ->
            var cx = x
            var cy = y
            repeat(piece.rotation.mod(4)) {
                val rotatedX = size - 1 - cy
                cy = cx
                cx = rotatedX
            }
            (piece.x + cx) to (piece.y + cy)
        }
    }

    fun moveLeft(state: BlockPuzzleState): BlockPuzzleState = nudge(state, -1)

    fun moveRight(state: BlockPuzzleState): BlockPuzzleState = nudge(state, 1)

    /**
     * Turns the piece, shifting it back in if the turn would push it through a wall.
     *
     * Without that kick, rotating against the edge silently does nothing, and a control that
     * sometimes ignores you is worse than no control at all when you are already agitated.
     */
    fun rotate(state: BlockPuzzleState): BlockPuzzleState {
        val piece = state.piece ?: return state
        val turned = piece.copy(rotation = (piece.rotation + 1).mod(4))
        for (kick in listOf(0, -1, 1, -2, 2)) {
            val candidate = turned.copy(x = turned.x + kick)
            if (fits(state.cells, candidate)) return state.copy(piece = candidate)
        }
        return state
    }

    /** One tick of gravity: fall if there is room, otherwise land and bring in the next piece. */
    fun step(state: BlockPuzzleState): BlockPuzzleState {
        val piece = state.piece ?: return spawnNext(state)
        val dropped = piece.copy(y = piece.y + 1)
        return if (fits(state.cells, dropped)) state.copy(piece = dropped) else land(state, piece)
    }

    /** Sends the piece straight down, which is the whole appeal of the genre. */
    fun drop(state: BlockPuzzleState): BlockPuzzleState {
        var piece = state.piece ?: return state
        while (fits(state.cells, piece.copy(y = piece.y + 1))) {
            piece = piece.copy(y = piece.y + 1)
        }
        return land(state, piece)
    }

    private fun nudge(state: BlockPuzzleState, dx: Int): BlockPuzzleState {
        val piece = state.piece ?: return state
        val moved = piece.copy(x = piece.x + dx)
        return if (fits(state.cells, moved)) state.copy(piece = moved) else state
    }

    private fun land(state: BlockPuzzleState, piece: ActivePiece): BlockPuzzleState {
        val cells = state.cells.toMutableList()
        cellsOf(piece).forEach { (x, y) ->
            if (x in 0 until WIDTH && y in 0 until HEIGHT) cells[y * WIDTH + x] = piece.kind + 1
        }
        val (settled, cleared) = clearFullRows(cells)
        return spawnNext(
            state.copy(cells = settled, piece = null, linesCleared = state.linesCleared + cleared),
        )
    }

    private fun spawnNext(state: BlockPuzzleState): BlockPuzzleState {
        val random = Random(state.seed)
        val kind = state.nextKind
        return spawn(state.copy(nextKind = random.nextInt(kindCount), seed = random.nextLong()), kind)
    }

    /**
     * Brings a piece in at the top — and when there is no room for it, wipes the board and brings
     * it in anyway. Nothing here ends.
     */
    private fun spawn(state: BlockPuzzleState, kind: Int): BlockPuzzleState {
        val piece = ActivePiece(
            kind = kind,
            rotation = 0,
            x = (WIDTH - boxSizes[kind]) / 2,
            y = 0,
        )
        return if (fits(state.cells, piece)) {
            state.copy(piece = piece)
        } else {
            state.copy(cells = List(WIDTH * HEIGHT) { 0 }, piece = piece, resets = state.resets + 1)
        }
    }

    private fun clearFullRows(cells: MutableList<Int>): Pair<List<Int>, Int> {
        val kept = (0 until HEIGHT)
            .map { row -> cells.subList(row * WIDTH, row * WIDTH + WIDTH).toList() }
            .filterNot { row -> row.all { it != 0 } }
        val cleared = HEIGHT - kept.size
        val blank = List(cleared) { List(WIDTH) { 0 } }
        return (blank + kept).flatten() to cleared
    }

    private fun fits(cells: List<Int>, piece: ActivePiece): Boolean =
        cellsOf(piece).all { (x, y) ->
            x in 0 until WIDTH && y in 0 until HEIGHT && cells[y * WIDTH + x] == 0
        }
}

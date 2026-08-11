package com.pace.reduction.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockPuzzleTest {

    private val width = BlockPuzzle.WIDTH
    private val height = BlockPuzzle.HEIGHT

    private fun emptyBoard() = List(width * height) { 0 }

    private fun BlockPuzzleState.occupied() = BlockPuzzle.cellsOf(requireNotNull(piece))

    @Test
    fun `a new board is empty and already has a piece in play`() {
        val state = BlockPuzzle.start(seed = 7L)
        assertTrue(state.cells.all { it == 0 })
        assertNotNull(state.piece)
        assertEquals(4, state.occupied().size)
    }

    @Test
    fun `every kind of piece spawns and turns inside the board`() {
        var state = BlockPuzzle.start(seed = 42L)
        val seen = mutableSetOf<Int>()
        repeat(80) {
            seen += requireNotNull(state.piece).kind
            repeat(4) {
                state = BlockPuzzle.rotate(state)
                state.occupied().forEach { (x, y) ->
                    assertTrue("$x,$y out of the board", x in 0 until width && y in 0 until height)
                }
            }
            state = BlockPuzzle.drop(state)
        }
        assertEquals(BlockPuzzle.kindCount, seen.size)
    }

    @Test
    fun `gravity moves the piece down one row per step`() {
        val state = BlockPuzzle.start(seed = 3L)
        val before = state.occupied().map { it.second }
        val after = BlockPuzzle.step(state).occupied().map { it.second }
        assertEquals(before.map { it + 1 }, after)
    }

    @Test
    fun `a dropped piece settles on the floor and hands over to the next one`() {
        val state = BlockPuzzle.drop(BlockPuzzle.start(seed = 11L))
        assertTrue(state.cells.any { it != 0 })
        // The lowest row of the board now holds something.
        assertTrue(state.cells.takeLast(width).any { it != 0 })
        assertNotNull(state.piece)
        assertEquals(0, state.piece!!.y)
    }

    @Test
    fun `a completed row is cleared and the rows above drop into its place`() {
        val cells = emptyBoard().toMutableList()
        // Bottom row full except the two right-hand columns, which the O piece will fill.
        for (x in 0 until width - 2) cells[(height - 1) * width + x] = 1
        val state = BlockPuzzleState(
            cells = cells,
            piece = ActivePiece(kind = O_PIECE, rotation = 0, x = width - 2, y = 0),
            nextKind = O_PIECE,
            linesCleared = 0,
            resets = 0,
            seed = 5L,
        )

        val after = BlockPuzzle.drop(state)

        assertEquals(1, after.linesCleared)
        assertEquals(0, after.resets)
        // Only the O's upper half survives, and it has fallen to the floor.
        assertEquals(2, after.cells.count { it != 0 })
        assertEquals(2, after.cells.takeLast(width).count { it != 0 })
    }

    @Test
    fun `a full board resets itself instead of ending the game`() {
        val state = BlockPuzzleState(
            cells = List(width * height) { 1 },
            piece = null,
            nextKind = O_PIECE,
            linesCleared = 4,
            resets = 0,
            seed = 9L,
        )

        val after = BlockPuzzle.step(state)

        assertEquals(1, after.resets)
        assertNotNull(after.piece)
        assertTrue(after.cells.all { it == 0 })
        // Work already done is not taken away as a punishment.
        assertEquals(4, after.linesCleared)
    }

    @Test
    fun `moving into a wall is refused rather than clipped through it`() {
        var state = BlockPuzzle.start(seed = 2L)
        repeat(width + 4) { state = BlockPuzzle.moveLeft(state) }
        assertTrue(state.occupied().all { (x, _) -> x >= 0 })

        repeat(2 * width + 8) { state = BlockPuzzle.moveRight(state) }
        assertTrue(state.occupied().all { (x, _) -> x < width })
    }

    @Test
    fun `four rotations return the piece to where it started`() {
        val state = BlockPuzzle.start(seed = 13L)
        var rotated = state
        repeat(4) { rotated = BlockPuzzle.rotate(rotated) }
        assertEquals(state.occupied().sortedBy { it.first * 100 + it.second }, rotated.occupied().sortedBy { it.first * 100 + it.second })
    }

    @Test
    fun `rotating against the wall kicks the piece back in rather than doing nothing`() {
        // A T stood on its side can legally sit one column left of the board's origin; turning it
        // flat from there needs the kick, and without one the tap would silently do nothing.
        val state = BlockPuzzleState(
            cells = emptyBoard(),
            piece = ActivePiece(kind = T_PIECE, rotation = 1, x = -1, y = 0),
            nextKind = T_PIECE,
            linesCleared = 0,
            resets = 0,
            seed = 1L,
        )

        val turned = BlockPuzzle.rotate(state)

        assertEquals(2, requireNotNull(turned.piece).rotation)
        assertEquals(0, requireNotNull(turned.piece).x)
        assertTrue(turned.occupied().all { (x, y) -> x in 0 until width && y in 0 until height })
    }

    /** Indexes into [BlockPuzzle]'s shape table. */
    private val O_PIECE = 1
    private val T_PIECE = 2
}

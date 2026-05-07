package connect4.game

/**
 * Immutable rectangular board. Row 0 is the top, row [rows]-1 is the bottom.
 * Cells are stored row-major.
 */
data class Board(
    val rows: Int,
    val cols: Int,
    val cells: List<Cell>,
) {
    init {
        require(rows > 0 && cols > 0) { "board must have positive dimensions" }
        require(cells.size == rows * cols) {
            "cell count (${cells.size}) does not match dimensions (${rows}x$cols)"
        }
    }

    operator fun get(row: Int, col: Int): Cell {
        require(row in 0 until rows && col in 0 until cols) {
            "($row,$col) out of bounds for ${rows}x$cols"
        }
        return cells[row * cols + col]
    }

    fun with(row: Int, col: Int, cell: Cell): Board {
        require(row in 0 until rows && col in 0 until cols)
        val updated = cells.toMutableList().also { it[row * cols + col] = cell }
        return copy(cells = updated)
    }

    /** The lowest empty row in [col], or null if the column is full. */
    fun lowestEmptyRow(col: Int): Int? {
        require(col in 0 until cols) { "column $col out of bounds" }
        for (row in rows - 1 downTo 0) {
            if (this[row, col] == Cell.EMPTY) return row
        }
        return null
    }

    fun isFull(): Boolean = cells.none { it == Cell.EMPTY }

    companion object {
        fun empty(rows: Int, cols: Int): Board =
            Board(rows, cols, List(rows * cols) { Cell.EMPTY })
    }
}

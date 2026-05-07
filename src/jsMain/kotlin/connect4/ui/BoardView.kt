package connect4.ui

import androidx.compose.runtime.Composable
import connect4.game.Cell
import connect4.game.GameState
import connect4.game.GameStatus
import connect4.game.Position
import org.jetbrains.compose.web.css.fr
import org.jetbrains.compose.web.css.gridTemplateColumns
import org.jetbrains.compose.web.dom.Div

@Composable
fun BoardView(state: GameState, onColumnClick: (Int) -> Unit) {
    val cols = state.config.cols
    val rows = state.config.rows
    val gameOver = state.isOver
    val winningCells: Set<Position> =
        (state.status as? GameStatus.Won)?.line?.toSet().orEmpty()

    Div(attrs = { classes("board-wrap") }) {
        Div(attrs = {
            classes("board")
            style { gridTemplateColumns("repeat($cols, 1fr)") }
        }) {
            for (c in 0 until cols) {
                val columnFull = state.board.lowestEmptyRow(c) == null
                val disabled = gameOver || columnFull
                Div(attrs = {
                    classes("col")
                    if (disabled) classes("disabled")
                    if (!disabled) {
                        attr("role", "button")
                        attr("tabindex", "0")
                        attr("aria-label", "Drop in column ${c + 1}")
                        onClick { onColumnClick(c) }
                    }
                }) {
                    for (r in 0 until rows) {
                        val cell = state.board[r, c]
                        val pos = Position(r, c)
                        Div(attrs = {
                            classes("cell")
                            classes(
                                when (cell) {
                                    Cell.EMPTY -> "empty"
                                    Cell.P1 -> "p1"
                                    Cell.P2 -> "p2"
                                },
                            )
                            if (state.lastMove == pos && cell != Cell.EMPTY) classes("drop")
                            if (pos in winningCells) classes("win")
                        })
                    }
                }
            }
        }
    }
}

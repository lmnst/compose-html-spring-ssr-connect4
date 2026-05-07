package connect4.view

import connect4.game.Cell
import connect4.game.GameState
import connect4.game.GameStatus
import connect4.game.Player
import connect4.game.Position
import connect4.ssr.HtmlNode
import connect4.ssr.HtmlScope
import connect4.ssr.View
import connect4.ssr.html

/**
 * Pure, platform-independent rendering of a [GameState]. The same function
 * runs on the JVM (used by the Spring controller to produce server-side
 * HTML) and is referenced by the Compose HTML client to keep server and
 * client output structurally aligned.
 *
 * The output is the application body fragment: header, status, board, and
 * an optional inline error message. The surrounding HTML document (head,
 * scripts, embedded state) is the responsibility of [PageRenderer].
 *
 * The board is wrapped in a `<form action="..." method="post">` whose
 * action URL is supplied by the controller (typically
 * `/games/{id}/move`). Each column button submits the player's move.
 * With JavaScript disabled, the page is fully playable through classic
 * HTML form submissions. With JavaScript enabled, the Compose HTML
 * client replaces the form when it mounts at `#root`.
 */
object ConnectFourView : View<ConnectFourView.Model> {

    /**
     * View model. The [error] is surfaced when the server rejects a move
     * (for example, an attempt to drop into a full column). The view does
     * not know how the error was produced, only how to display it.
     *
     * [gameId], when present, is rendered as a hidden field and a CSS
     * class so the page identifies which server-side game the form
     * targets. The view itself does not interpret the id; the controller
     * supplies the corresponding [moveAction].
     */
    data class Model(
        val state: GameState,
        val moveAction: String,
        val error: String? = null,
        val gameId: String? = null,
    )

    override fun render(model: Model): HtmlNode = html {
        div(class_ = "app") {
            div(class_ = "header") {
                h1 { +"Connect Four" }
                p(class_ = "subtitle") {
                    +"Compose HTML server-side rendering with a Spring Boot controller. Drop discs into a column, first to win-length in a row wins."
                }
            }
            renderStatus(model.state)
            if (model.error != null) {
                div(class_ = "panel error", attrs = mapOf("role" to "alert")) {
                    +model.error
                }
            }
            renderBoardForm(model)
            noscript(class_ = "panel noscript") {
                p {
                    +"This page is fully rendered on the server. With JavaScript disabled, every column click reloads the page through a normal form POST."
                }
            }
        }
    }

    private fun HtmlScope.renderStatus(state: GameState) {
        div(class_ = "panel status", attrs = mapOf("aria-live" to "polite")) {
            when (val status = state.status) {
                GameStatus.Ongoing -> {
                    span(class_ = "turn-dot ${dotClass(state.currentPlayer)}")
                    +"${label(state.currentPlayer)}'s turn"
                }
                GameStatus.Draw -> +"Draw, the board is full."
                is GameStatus.Won -> {
                    span(class_ = "turn-dot ${dotClass(status.winner)}")
                    +"${label(status.winner)} wins!"
                }
            }
        }
    }

    private fun HtmlScope.renderBoardForm(model: Model) {
        val state = model.state
        val rows = state.config.rows
        val cols = state.config.cols
        val winningCells: Set<Position> =
            (state.status as? GameStatus.Won)?.line?.toSet().orEmpty()
        val gameOver = state.isOver

        form(action = model.moveAction, method = "post", class_ = "board-form") {
            if (model.gameId != null) {
                input(name = "gameId", value = model.gameId)
            }
            div(class_ = "board-wrap") {
                div(
                    class_ = "board",
                    attrs = mapOf(
                        "role" to "grid",
                        "aria-label" to "Connect Four board, $rows rows by $cols columns",
                        "style" to "grid-template-columns: repeat($cols, 1fr);",
                        "data-rows" to rows.toString(),
                        "data-cols" to cols.toString(),
                    ),
                ) {
                    for (c in 0 until cols) {
                        renderColumnButton(state, c, gameOver, winningCells)
                    }
                }
            }
        }
    }

    private fun HtmlScope.renderColumnButton(
        state: GameState,
        c: Int,
        gameOver: Boolean,
        winningCells: Set<Position>,
    ) {
        val rows = state.config.rows
        val columnFull = state.board.lowestEmptyRow(c) == null
        val disabled = gameOver || columnFull
        val classes = buildString {
            append("col")
            if (disabled) append(" disabled")
        }
        val attrs = LinkedHashMap<String, String>()
        attrs["type"] = "submit"
        attrs["name"] = "col"
        attrs["value"] = c.toString()
        attrs["data-col"] = c.toString()
        if (disabled) {
            attrs["disabled"] = "disabled"
            attrs["aria-disabled"] = "true"
        } else {
            attrs["aria-label"] = "Drop in column ${c + 1}"
        }
        button(class_ = classes, attrs = attrs) {
            for (r in 0 until rows) {
                renderCell(state, r, c, winningCells)
            }
        }
    }

    private fun HtmlScope.renderCell(
        state: GameState,
        r: Int,
        c: Int,
        winningCells: Set<Position>,
    ) {
        val cell = state.board[r, c]
        val pos = Position(r, c)
        val cls = buildString {
            append("cell ")
            append(
                when (cell) {
                    Cell.EMPTY -> "empty"
                    Cell.P1 -> "p1"
                    Cell.P2 -> "p2"
                },
            )
            if (state.lastMove == pos && cell != Cell.EMPTY) append(" drop")
            if (pos in winningCells) append(" win")
        }
        div(
            class_ = cls,
            attrs = mapOf(
                "data-row" to r.toString(),
                "data-col" to c.toString(),
            ),
        )
    }

    fun label(player: Player): String = when (player) {
        Player.ONE -> "Player 1"
        Player.TWO -> "Player 2"
    }

    fun dotClass(player: Player): String = when (player) {
        Player.ONE -> "p1"
        Player.TWO -> "p2"
    }
}

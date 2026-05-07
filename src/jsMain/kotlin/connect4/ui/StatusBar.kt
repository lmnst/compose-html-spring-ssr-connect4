package connect4.ui

import androidx.compose.runtime.Composable
import connect4.game.GameState
import connect4.game.GameStatus
import connect4.game.Player
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
fun StatusBar(state: GameState) {
    Div(attrs = { classes("panel", "status") }) {
        when (val status = state.status) {
            GameStatus.Ongoing -> {
                Span(attrs = { classes("turn-dot", state.currentPlayer.dotClass()) })
                Text("${state.currentPlayer.label()}'s turn")
            }
            GameStatus.Draw -> Text("Draw, the board is full.")
            is GameStatus.Won -> {
                Span(attrs = { classes("turn-dot", status.winner.dotClass()) })
                Text("${status.winner.label()} wins!")
            }
        }
    }
}

internal fun Player.label(): String = when (this) {
    Player.ONE -> "Player 1"
    Player.TWO -> "Player 2"
}

internal fun Player.dotClass(): String = when (this) {
    Player.ONE -> "p1"
    Player.TWO -> "p2"
}

package connect4.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import connect4.game.ConnectFourEngine
import connect4.game.GameConfig
import connect4.game.GameState
import connect4.game.MoveResult
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

@Composable
fun App(initial: GameState) {
    var state by remember { mutableStateOf(initial) }
    var configError by remember { mutableStateOf<String?>(null) }

    fun update(newState: GameState) {
        state = newState
        Persistence.save(newState)
    }

    Div(attrs = { classes("app") }) {
        Div(attrs = { classes("header") }) {
            H1 { Text("Connect Four") }
            P(attrs = { classes("subtitle") }) {
                Text("Compose HTML server-side rendering with a Spring Boot controller. Drop discs into a column, first to win-length in a row wins.")
            }
        }

        ConfigPanel(
            current = state.config,
            error = configError,
            onApply = { rows, cols, winLength ->
                val err = GameConfig.validate(rows, cols, winLength)
                if (err != null) {
                    configError = err
                } else {
                    Persistence.clear()
                    update(ConnectFourEngine.newGame(GameConfig(rows, cols, winLength)))
                    configError = null
                }
            },
            onReset = {
                Persistence.clear()
                update(ConnectFourEngine.newGame(state.config))
                configError = null
            },
        )

        StatusBar(state)

        BoardView(state) { col ->
            val r = ConnectFourEngine.move(state, col)
            if (r is MoveResult.Success) update(r.newState)
        }
    }
}

package connect4.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import connect4.game.GameConfig
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.max
import org.jetbrains.compose.web.attributes.min
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Button

@Composable
fun ConfigPanel(
    current: GameConfig,
    error: String?,
    onApply: (rows: Int, cols: Int, winLength: Int) -> Unit,
    onReset: () -> Unit,
) {
    var rowsText by remember(current) { mutableStateOf(current.rows.toString()) }
    var colsText by remember(current) { mutableStateOf(current.cols.toString()) }
    var winText by remember(current) { mutableStateOf(current.winLength.toString()) }

    Div(attrs = { classes("panel") }) {
        Div(attrs = { classes("config-grid") }) {
            NumberField(
                title = "Rows (${GameConfig.MIN_DIMENSION} to ${GameConfig.MAX_DIMENSION})",
                value = rowsText,
                min = GameConfig.MIN_DIMENSION,
                max = GameConfig.MAX_DIMENSION,
                onChange = { rowsText = it },
            )
            NumberField(
                title = "Columns (${GameConfig.MIN_DIMENSION} to ${GameConfig.MAX_DIMENSION})",
                value = colsText,
                min = GameConfig.MIN_DIMENSION,
                max = GameConfig.MAX_DIMENSION,
                onChange = { colsText = it },
            )
            NumberField(
                title = "Win length (${GameConfig.MIN_WIN_LENGTH} to ${GameConfig.MAX_WIN_LENGTH})",
                value = winText,
                min = GameConfig.MIN_WIN_LENGTH,
                max = GameConfig.MAX_WIN_LENGTH,
                onChange = { winText = it },
            )
        }

        Div(attrs = { classes("actions") }) {
            Button(attrs = {
                classes("btn")
                onClick {
                    val rows = rowsText.toIntOrNull()
                    val cols = colsText.toIntOrNull()
                    val win = winText.toIntOrNull()
                    if (rows == null || cols == null || win == null) return@onClick
                    onApply(rows, cols, win)
                }
            }) { Text("Start new game") }

            Button(attrs = {
                classes("btn", "secondary")
                onClick { onReset() }
            }) { Text("Reset board") }
        }

        if (error != null) {
            Div(attrs = { classes("error") }) { Text(error) }
        }
    }
}

@Composable
private fun NumberField(
    title: String,
    value: String,
    min: Int,
    max: Int,
    onChange: (String) -> Unit,
) {
    Label {
        Span { Text(title) }
        Input(type = InputType.Number) {
            value(value)
            min(min.toString())
            max(max.toString())
            onInput { ev -> onChange(ev.value?.toString().orEmpty()) }
        }
    }
}

package connect4

import connect4.game.ConnectFourEngine
import connect4.game.GameConfig
import connect4.game.GameState
import connect4.state.StateCodec
import connect4.ui.App
import connect4.ui.Persistence
import kotlinx.browser.document
import org.jetbrains.compose.web.renderComposable

private const val ROOT_ELEMENT_ID = "root"
private const val INITIAL_STATE_ELEMENT_ID = "connect4-initial-state"

/**
 * Client entry point. Reads the SSR-embedded initial state, prefers a
 * compatible saved state from localStorage when present, then mounts the
 * Compose HTML app onto the same `#root` element the server populated.
 */
fun main() {
    val ssrState = readEmbeddedInitialState()
    val initial = Persistence.resume(ssrState)
        ?: ssrState
        ?: ConnectFourEngine.newGame(GameConfig.DEFAULT)

    renderComposable(rootElementId = ROOT_ELEMENT_ID) {
        App(initial)
    }
}

private fun readEmbeddedInitialState(): GameState? {
    val element = document.getElementById(INITIAL_STATE_ELEMENT_ID) ?: return null
    val payload = element.textContent?.trim().orEmpty()
    if (payload.isEmpty()) return null
    return StateCodec.decode(payload)
}

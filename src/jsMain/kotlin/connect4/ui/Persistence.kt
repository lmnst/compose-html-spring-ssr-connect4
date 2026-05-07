package connect4.ui

import connect4.game.GameState
import connect4.state.StateCodec
import kotlinx.browser.localStorage
import org.w3c.dom.get
import org.w3c.dom.set

/**
 * Browser-side persistence. The encoding is delegated to [StateCodec] in
 * commonMain, so the same payload format is produced by the server and
 * stored locally.
 *
 * The resume rule: a saved state is only used when it shares the same
 * board configuration as the state the server rendered. This avoids the
 * common "what page did I refresh?" problem where a user opens the page
 * with `?rows=5&cols=5` but localStorage holds an in-progress 6x7 game.
 */
object Persistence {

    private const val KEY = "connect4-state-v1"

    fun save(state: GameState) {
        runCatching { localStorage[KEY] = StateCodec.encode(state) }
    }

    fun clear() {
        runCatching { localStorage.removeItem(KEY) }
    }

    /**
     * Returns the saved state if it exists and is compatible with the
     * server-rendered [ssr] state (same rows, cols, win length). Returns
     * null otherwise. Also clears any unparseable payload as a side effect
     * so the next save replaces it cleanly.
     */
    fun resume(ssr: GameState?): GameState? {
        val raw = runCatching { localStorage[KEY] }.getOrNull() ?: return null
        val saved = StateCodec.decode(raw)
        if (saved == null) {
            clear()
            return null
        }
        if (ssr != null && saved.config != ssr.config) return null
        return saved
    }
}

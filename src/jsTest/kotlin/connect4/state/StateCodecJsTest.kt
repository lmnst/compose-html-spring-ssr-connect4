package connect4.state

import connect4.game.ConnectFourEngine
import connect4.game.GameConfig
import connect4.game.GameStatus
import connect4.game.MoveResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Cross-target validation. The same [StateCodec] code that runs on the
 * JVM (server) must also run on the JS target (client) so that the
 * SSR-embedded payload decodes identically wherever the bundle is hosted.
 *
 * These tests exercise the codec from compiled JS, which is the path
 * actually taken by the browser at hydration time.
 */
class StateCodecJsTest {

    @Test
    fun roundtrip_default_state() {
        val s = ConnectFourEngine.newGame(GameConfig.DEFAULT)
        val decoded = StateCodec.decode(StateCodec.encode(s))
        assertEquals(s, decoded)
    }

    @Test
    fun roundtrip_state_after_a_few_moves() {
        var s = ConnectFourEngine.newGame(GameConfig(rows = 5, cols = 6, winLength = 4))
        for (col in intArrayOf(0, 1, 0, 1, 0)) {
            val r = ConnectFourEngine.move(s, col)
            assertIs<MoveResult.Success>(r)
            s = r.newState
        }
        assertEquals(s, StateCodec.decode(StateCodec.encode(s)))
    }

    @Test
    fun roundtrip_won_state_preserves_winning_line() {
        var s = ConnectFourEngine.newGame(GameConfig.DEFAULT)
        for (col in intArrayOf(0, 6, 1, 6, 2, 6, 3)) {
            s = (ConnectFourEngine.move(s, col) as MoveResult.Success).newState
        }
        assertIs<GameStatus.Won>(s.status)
        val decoded = StateCodec.decode(StateCodec.encode(s))
        assertNotNull(decoded)
        val won = decoded.status
        assertIs<GameStatus.Won>(won)
        assertEquals(4, won.line.size)
    }

    @Test
    fun decode_returns_null_for_unknown_version() {
        assertNull(StateCodec.decode("v0|6|7|4|ONE|O|" + ".".repeat(42) + "|"))
    }

    @Test
    fun decode_returns_null_for_corrupt_payload() {
        assertNull(StateCodec.decode("v1|6|7"))
        assertNull(StateCodec.decode("v1|6|7|4|ONE|O|" + "x".repeat(42) + "|"))
    }

    @Test
    fun encoded_payload_is_safe_to_embed_in_html_text_context() {
        val s = ConnectFourEngine.newGame(GameConfig.DEFAULT)
        val payload = StateCodec.encode(s)
        // The payload must not contain HTML-meaningful characters; the
        // server's renderer additionally guards `<script>` raw-text.
        assertEquals(payload, payload.filter { it != '<' && it != '>' })
    }
}

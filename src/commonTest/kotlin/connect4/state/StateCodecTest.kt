package connect4.state

import connect4.game.ConnectFourEngine
import connect4.game.GameConfig
import connect4.game.GameState
import connect4.game.GameStatus
import connect4.game.MoveResult
import connect4.game.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StateCodecTest {

    @Test
    fun roundtrip_initial_state() {
        val s = ConnectFourEngine.newGame(GameConfig.DEFAULT)
        val decoded = StateCodec.decode(StateCodec.encode(s))
        assertEquals(s, decoded)
    }

    @Test
    fun roundtrip_state_after_a_few_moves() {
        var s = ConnectFourEngine.newGame(GameConfig(5, 6, 4))
        for (col in intArrayOf(0, 1, 0, 1, 0)) {
            val r = ConnectFourEngine.move(s, col)
            assertIs<MoveResult.Success>(r)
            s = r.newState
        }
        val decoded = StateCodec.decode(StateCodec.encode(s))
        assertEquals(s, decoded)
        assertEquals(Player.TWO, decoded!!.currentPlayer)
    }

    @Test
    fun roundtrip_won_state_preserves_winning_line() {
        var s = ConnectFourEngine.newGame(GameConfig.DEFAULT)
        for (col in intArrayOf(0, 6, 1, 6, 2, 6, 3)) {
            val r = ConnectFourEngine.move(s, col)
            assertIs<MoveResult.Success>(r)
            s = r.newState
        }
        assertIs<GameStatus.Won>(s.status)
        val decoded = StateCodec.decode(StateCodec.encode(s))
        assertEquals(s, decoded)
        val won = decoded!!.status
        assertIs<GameStatus.Won>(won)
        assertEquals(4, won.line.size)
    }

    @Test
    fun decode_returns_null_for_unknown_version() {
        assertNull(StateCodec.decode("v0|6|7|4|ONE|O|" + ".".repeat(42) + "|"))
    }

    @Test
    fun decode_returns_null_for_truncated_payload() {
        assertNull(StateCodec.decode("v1|6|7"))
    }

    @Test
    fun decode_returns_null_for_corrupt_cells() {
        assertNull(StateCodec.decode("v1|6|7|4|ONE|O|" + "x".repeat(42) + "|"))
    }

    @Test
    fun decode_returns_null_for_wrong_cell_count() {
        assertNull(StateCodec.decode("v1|6|7|4|ONE|O|" + ".".repeat(41) + "|"))
    }

    @Test
    fun decode_returns_null_for_invalid_player_or_status() {
        assertNull(StateCodec.decode("v1|6|7|4|THREE|O|" + ".".repeat(42) + "|"))
        assertNull(StateCodec.decode("v1|6|7|4|ONE|Z|" + ".".repeat(42) + "|"))
    }

    @Test
    fun encoded_payload_is_safe_for_embedding_in_html_script_block() {
        val s = ConnectFourEngine.newGame(GameConfig.DEFAULT)
        val payload = StateCodec.encode(s)
        // No raw HTML control characters that could break out of a script tag.
        assertTrue(payload.none { it == '<' || it == '>' })
    }
}

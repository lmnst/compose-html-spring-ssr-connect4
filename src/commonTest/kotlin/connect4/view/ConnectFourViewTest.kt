package connect4.view

import connect4.game.ConnectFourEngine
import connect4.game.GameConfig
import connect4.game.GameState
import connect4.game.MoveResult
import connect4.ssr.HtmlRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConnectFourViewTest {

    private val renderer = HtmlRenderer()

    private fun model(
        state: GameState,
        moveAction: String = "/games/test1234/move",
        error: String? = null,
        gameId: String? = "test1234",
    ): ConnectFourView.Model = ConnectFourView.Model(state, moveAction, error, gameId)

    private fun render(
        state: GameState,
        moveAction: String = "/games/test1234/move",
        error: String? = null,
        gameId: String? = "test1234",
    ): String = renderer.render(ConnectFourView.render(model(state, moveAction, error, gameId)))

    private fun renderDefault(): String = render(ConnectFourEngine.newGame(GameConfig.DEFAULT))

    @Test
    fun default_state_renders_a_board_with_42_empty_cells() {
        val out = renderDefault()
        val emptyCells = Regex("""class="cell empty"""").findAll(out).count()
        assertEquals(42, emptyCells)
    }

    @Test
    fun default_state_announces_player_one_turn() {
        val out = renderDefault()
        assertTrue(out.contains("Player 1's turn"))
        assertTrue(out.contains("turn-dot p1"))
    }

    @Test
    fun board_data_attributes_match_config() {
        val state = ConnectFourEngine.newGame(GameConfig(rows = 5, cols = 6, winLength = 4))
        val out = render(state)
        assertTrue(out.contains("""data-rows="5""""))
        assertTrue(out.contains("""data-cols="6""""))
        for (c in 0 until 6) assertTrue(out.contains("""data-col="$c""""))
    }

    @Test
    fun playable_columns_emit_real_button_elements_focusable_by_default() {
        val out = renderDefault()
        val playable = Regex("""<button class="col" type="submit" name="col" value="(\d)"""")
            .findAll(out).count()
        assertEquals(7, playable)
        assertFalse(out.contains("""tabindex="0""""), "real buttons should not need tabindex")
    }

    @Test
    fun board_form_targets_the_supplied_move_action() {
        val out = render(ConnectFourEngine.newGame(GameConfig.DEFAULT), moveAction = "/games/abcd1234/move")
        assertTrue(out.contains("""<form action="/games/abcd1234/move" method="post" class="board-form">"""))
    }

    @Test
    fun board_form_includes_the_game_id_as_a_hidden_input_when_present() {
        val out = render(ConnectFourEngine.newGame(GameConfig.DEFAULT), gameId = "demo5678")
        assertTrue(out.contains("""<input type="hidden" name="gameId" value="demo5678" />"""))
    }

    @Test
    fun board_form_omits_the_game_id_input_when_no_id_is_supplied() {
        val out = render(ConnectFourEngine.newGame(GameConfig.DEFAULT), gameId = null)
        assertFalse(out.contains("""name="gameId""""))
    }

    @Test
    fun full_columns_disable_their_buttons() {
        var s = ConnectFourEngine.newGame(GameConfig(rows = 4, cols = 4, winLength = 4))
        repeat(4) {
            val r = ConnectFourEngine.move(s, 0)
            assertIs<MoveResult.Success>(r)
            s = r.newState
        }
        val out = render(s)
        assertTrue(out.contains("""<button class="col disabled""""))
        assertTrue(out.contains("""disabled="disabled""""))
        assertTrue(out.contains("""aria-disabled="true""""))
    }

    @Test
    fun winning_line_marks_cells_with_win_class() {
        var s = ConnectFourEngine.newGame(GameConfig.DEFAULT)
        for (col in intArrayOf(0, 6, 1, 6, 2, 6, 3)) {
            val r = ConnectFourEngine.move(s, col)
            assertIs<MoveResult.Success>(r)
            s = r.newState
        }
        val out = render(s)
        val winCells = Regex(""" win"""").findAll(out).count()
        assertEquals(4, winCells)
        assertTrue(out.contains("Player 1 wins!"))
    }

    @Test
    fun draw_state_announces_draw() {
        var s = ConnectFourEngine.newGame(GameConfig(rows = 4, cols = 5, winLength = 5))
        val plan = intArrayOf(
            0, 1, 0, 1,
            2, 3, 2, 3,
            1, 0, 1, 0,
            3, 2, 3, 2,
            4, 4, 4, 4,
        )
        for (col in plan) {
            val r = ConnectFourEngine.move(s, col)
            assertIs<MoveResult.Success>(r)
            s = r.newState
        }
        val out = render(s)
        assertTrue(out.contains("Draw, the board is full."))
    }

    @Test
    fun column_indices_use_one_based_aria_labels() {
        val out = renderDefault()
        for (col in 1..7) {
            assertTrue(out.contains("""aria-label="Drop in column $col""""), "missing label for $col")
        }
    }

    @Test
    fun error_message_renders_with_alert_role() {
        val out = render(
            ConnectFourEngine.newGame(GameConfig.DEFAULT),
            error = "That column is full",
        )
        assertTrue(out.contains("""<div class="panel error" role="alert">That column is full</div>"""))
    }

    @Test
    fun no_error_panel_when_no_error_supplied() {
        val out = renderDefault()
        assertFalse(out.contains("""class="panel error""""))
    }
}

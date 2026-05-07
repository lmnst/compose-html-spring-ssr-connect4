package connect4.view

import connect4.game.ConnectFourEngine
import connect4.game.GameConfig
import connect4.game.GameState
import connect4.state.StateCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PageRendererTest {

    private val renderer = PageRenderer(
        bodyView = ConnectFourView,
        styleHref = "/static/connect4.css",
        scriptSrc = "/static/connect4.js",
    )

    private fun model(state: GameState, gameId: String = "abcdef12"): ConnectFourView.Model =
        ConnectFourView.Model(
            state = state,
            moveAction = "/games/$gameId/move",
            gameId = gameId,
        )

    @Test
    fun output_is_a_complete_html5_document() {
        val state = ConnectFourEngine.newGame(GameConfig.DEFAULT)
        val out = renderer.render(model(state))
        assertTrue(out.startsWith("<!DOCTYPE html>"), out.take(50))
        assertTrue(out.contains("<html lang=\"en\">"))
        assertTrue(out.contains("<title>Connect Four, Compose HTML SSR</title>"))
    }

    @Test
    fun page_references_configured_stylesheet_and_script() {
        val out = renderer.render(model(ConnectFourEngine.newGame(GameConfig.DEFAULT)))
        assertTrue(out.contains("""href="/static/connect4.css""""))
        assertTrue(out.contains("""src="/static/connect4.js""""))
    }

    @Test
    fun page_embeds_initial_state_decodable_back_to_input() {
        val state = ConnectFourEngine.newGame(GameConfig(rows = 5, cols = 6, winLength = 4))
        val out = renderer.render(model(state))
        val match = Regex("""<script type="application/x-connect4-state" id="connect4-initial-state">([^<]+)</script>""")
            .find(out)
        assertNotNull(match, "embedded state script must be present in:\n$out")
        val payload = match.groupValues[1]
        val decoded = StateCodec.decode(payload)
        assertEquals(state, decoded)
    }

    @Test
    fun page_renders_the_board_inside_the_root_mount_element() {
        val out = renderer.render(model(ConnectFourEngine.newGame(GameConfig.DEFAULT)))
        val rootIdx = out.indexOf("""id="root"""")
        val boardIdx = out.indexOf("""class="board"""")
        assertTrue(rootIdx > 0 && boardIdx > rootIdx, "board must be nested inside #root")
    }

    @Test
    fun script_payload_does_not_break_out_of_script_block() {
        val state = ConnectFourEngine.newGame(GameConfig.DEFAULT)
        val out = renderer.render(model(state))
        val payloadStart = out.indexOf("application/x-connect4-state")
        val payloadEnd = out.indexOf("</script>", payloadStart)
        assertTrue(payloadStart > 0 && payloadEnd > payloadStart)
    }

    @Test
    fun form_action_targets_the_id_keyed_move_endpoint() {
        val out = renderer.render(model(ConnectFourEngine.newGame(GameConfig.DEFAULT), "demo1234"))
        assertTrue(out.contains("""<form action="/games/demo1234/move" method="post" class="board-form">"""))
        assertTrue(out.contains("""<input type="hidden" name="gameId" value="demo1234" />"""))
    }
}

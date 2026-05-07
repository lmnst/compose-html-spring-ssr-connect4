package connect4.server

import connect4.repo.GameId
import connect4.state.StateCodec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class Connect4ControllerTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var repository: InMemoryGameRepository

    private fun extractEmbeddedState(body: String): String {
        val m = Regex(
            """<script type="application/x-connect4-state" id="connect4-initial-state">([^<]+)</script>""",
        ).find(body) ?: error("no embedded state in body:\n$body")
        return m.groupValues[1]
    }

    private fun followRedirectToGameId(action: () -> String): String {
        val location = action()
        val match = Regex("""/games/([a-z0-9]+)""").matchEntire(location)
            ?: error("not a /games/{id} redirect: $location")
        return match.groupValues[1]
    }

    private fun postCreateGame(rows: Int? = null, cols: Int? = null, win: Int? = null): String {
        val mvcResult = mvc.post("/games") {
            if (rows != null) param("rows", rows.toString())
            if (cols != null) param("cols", cols.toString())
            if (win != null) param("win", win.toString())
        }.andExpect {
            status().isSeeOther
            redirectedUrlPattern("/games/*")
        }.andReturn()
        return mvcResult.response.getHeader("Location") ?: error("no Location")
    }

    @Test
    fun root_redirects_to_a_freshly_created_game() {
        val location = mvc.get("/").andExpect {
            status().isSeeOther
            redirectedUrlPattern("/games/*")
            header().string("Cache-Control", "no-store")
        }.andReturn().response.getHeader("Location")!!
        val id = location.removePrefix("/games/")
        assertThat(GameId.isValid(id)).isTrue
        assertThat(repository.get(GameId(id))).isNotNull
    }

    @Test
    fun root_passes_query_params_through_to_game_config() {
        val location = mvc.get("/?rows=5&cols=5&win=4").andReturn().response.getHeader("Location")!!
        val id = location.removePrefix("/games/")
        val state = repository.get(GameId(id))!!
        assertThat(state.config.rows).isEqualTo(5)
        assertThat(state.config.cols).isEqualTo(5)
        assertThat(state.config.winLength).isEqualTo(4)
    }

    @Test
    fun root_falls_back_to_default_config_for_invalid_query_params() {
        val location = mvc.get("/?rows=2&cols=2&win=10").andReturn().response.getHeader("Location")!!
        val id = location.removePrefix("/games/")
        val state = repository.get(GameId(id))!!
        assertThat(state.config.rows).isEqualTo(6)
        assertThat(state.config.cols).isEqualTo(7)
    }

    @Test
    fun post_games_creates_a_game_and_redirects() {
        val location = postCreateGame(rows = 8, cols = 8, win = 5)
        val id = location.removePrefix("/games/")
        val state = repository.get(GameId(id))!!
        assertThat(state.config.rows).isEqualTo(8)
        assertThat(state.config.cols).isEqualTo(8)
        assertThat(state.config.winLength).isEqualTo(5)
    }

    @Test
    fun get_games_id_renders_the_page_with_id_keyed_form_action() {
        val location = postCreateGame()
        val id = location.removePrefix("/games/")

        val body = mvc.get(location).andExpect {
            status().isOk
            header().string("Content-Type", "text/html;charset=UTF-8")
        }.andReturn().response.contentAsString

        assertThat(body)
            .contains("""<form action="/games/$id/move" method="post" class="board-form">""")
            .contains("""<input type="hidden" name="gameId" value="$id" />""")
            .contains("Player 1's turn")
        // Hidden state input is gone now: the server is authoritative.
        assertThat(body).doesNotContain("""name="state"""")
    }

    @Test
    fun get_games_unknown_id_returns_404_with_a_friendly_body() {
        mvc.get("/games/notreal01").andExpect {
            status().isNotFound
            header().string("Content-Type", "text/html;charset=UTF-8")
        }.andReturn().response.contentAsString.let {
            assertThat(it).contains("404 Game not found")
            assertThat(it).contains("notreal01")
        }
    }

    @Test
    fun get_games_invalid_id_returns_404() {
        mvc.get("/games/UPPER").andExpect { status().isNotFound }
        mvc.get("/games/has-dashes").andExpect { status().isNotFound }
    }

    @Test
    fun post_games_id_move_advances_state_and_redirects_to_show() {
        val location = postCreateGame()
        val id = location.removePrefix("/games/")

        mvc.post("/games/$id/move") {
            param("col", "3")
        }.andExpect {
            status().isSeeOther
            redirectedUrl("/games/$id")
        }

        val body = mvc.get("/games/$id").andReturn().response.contentAsString
        val current = StateCodec.decode(extractEmbeddedState(body))!!
        assertThat(current.lastMove?.col).isEqualTo(3)
        assertThat(current.lastMove?.row).isEqualTo(current.config.rows - 1)
        assertThat(current.currentPlayer.name).isEqualTo("TWO")
    }

    @Test
    fun post_games_id_move_preserves_state_across_multiple_requests() {
        val id = postCreateGame().removePrefix("/games/")
        mvc.post("/games/$id/move") { param("col", "0") }
        mvc.post("/games/$id/move") { param("col", "0") }
        mvc.post("/games/$id/move") { param("col", "0") }

        val body = mvc.get("/games/$id").andReturn().response.contentAsString
        val state = StateCodec.decode(extractEmbeddedState(body))!!
        // Three discs alternating into column 0: rows N-1, N-2, N-3.
        val rows = state.config.rows
        assertThat(state.board[rows - 1, 0].name).isEqualTo("P1")
        assertThat(state.board[rows - 2, 0].name).isEqualTo("P2")
        assertThat(state.board[rows - 3, 0].name).isEqualTo("P1")
        assertThat(state.currentPlayer.name).isEqualTo("TWO")
    }

    @Test
    fun two_independent_game_ids_keep_independent_state() {
        val a = postCreateGame().removePrefix("/games/")
        val b = postCreateGame().removePrefix("/games/")
        assertThat(a).isNotEqualTo(b)

        mvc.post("/games/$a/move") { param("col", "0") }
        mvc.post("/games/$b/move") { param("col", "6") }

        val bodyA = mvc.get("/games/$a").andReturn().response.contentAsString
        val bodyB = mvc.get("/games/$b").andReturn().response.contentAsString
        val stateA = StateCodec.decode(extractEmbeddedState(bodyA))!!
        val stateB = StateCodec.decode(extractEmbeddedState(bodyB))!!

        assertThat(stateA.lastMove?.col).isEqualTo(0)
        assertThat(stateB.lastMove?.col).isEqualTo(6)
    }

    @Test
    fun post_games_id_move_surfaces_inline_error_and_does_not_advance_state() {
        // Create a 4-column game and fill column 0 in the repository.
        val location = postCreateGame(rows = 4, cols = 4, win = 4)
        val id = location.removePrefix("/games/")
        repeat(4) {
            mvc.post("/games/$id/move") { param("col", "0") }
        }
        // Snapshot state.
        val before = repository.get(GameId(id))!!

        // 5th attempt at col 0 must be rejected: the column is full.
        val body = mvc.post("/games/$id/move") {
            param("col", "0")
        }.andExpect {
            status().isOk
            header().string("Content-Type", "text/html;charset=UTF-8")
        }.andReturn().response.contentAsString

        assertThat(body)
            .contains("""class="panel error" role="alert"""")
            .contains("That column is full")

        // Repo state is unchanged.
        assertThat(repository.get(GameId(id))).isEqualTo(before)
    }

    @Test
    fun post_games_id_move_after_game_over_keeps_won_status() {
        val id = postCreateGame().removePrefix("/games/")
        // Player 1 wins along the bottom row of the default board.
        for (col in intArrayOf(0, 6, 1, 6, 2, 6, 3)) {
            mvc.post("/games/$id/move") { param("col", col.toString()) }
        }
        val body = mvc.post("/games/$id/move") { param("col", "5") }
            .andExpect { status().isOk }
            .andReturn().response.contentAsString
        assertThat(body)
            .contains("Player 1 wins!")
            .contains("The game is already over")
    }

    @Test
    fun post_games_id_move_returns_404_for_unknown_id() {
        mvc.post("/games/notreal01/move") {
            param("col", "0")
        }.andExpect { status().isNotFound }
    }

    @Test
    fun health_endpoint_is_alive() {
        mvc.get("/healthz").andReturn().also {
            assertThat(it.response.status).isEqualTo(200)
            assertThat(it.response.contentAsString).isEqualTo("ok")
        }
    }

    @Test
    fun static_css_asset_is_served() {
        val r = mvc.get("/static/connect4.css").andReturn()
        assertThat(r.response.status).isEqualTo(200)
        assertThat(r.response.contentAsString).contains("--c-board:")
    }

    @Test
    fun api_state_returns_decodable_default_payload() {
        val r = mvc.get("/api/state").andReturn()
        assertThat(r.response.status).isEqualTo(200)
        val decoded = StateCodec.decode(r.response.contentAsString)
        assertThat(decoded).isNotNull
        assertThat(decoded!!.config.rows).isEqualTo(6)
    }

    @Test
    fun about_page_uses_the_same_engine_with_a_different_view() {
        val body = mvc.get("/about").andReturn().response.contentAsString
        assertThat(body)
            .startsWith("<!DOCTYPE html>")
            .contains("<title>About, Compose HTML SSR</title>")
            .contains("Compose HTML SSR for Spring")
            .contains("""<a href="/">the Connect Four page</a>""")
            .doesNotContain("application/x-connect4-state")
            .doesNotContain("/static/connect4.js")
    }
}

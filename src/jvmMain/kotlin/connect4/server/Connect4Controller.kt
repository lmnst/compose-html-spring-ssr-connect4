package connect4.server

import connect4.game.ConnectFourEngine
import connect4.game.GameConfig
import connect4.repo.GameId
import connect4.state.StateCodec
import connect4.view.ConnectFourView
import connect4.view.PageRenderer
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Server-authoritative SSR controller. Game state lives in the
 * [GameService] and is keyed by [GameId]. The controller never
 * accepts a state payload from the client: every move loads the
 * canonical state from the service, applies the move, and stores
 * the result.
 *
 * Routes:
 *  - `GET /` and `GET /index.html`: redirect (303) to a freshly
 *    created game so the browser address bar always reflects a real
 *    server-side game.
 *  - `POST /games`: create a new game from form params `rows`, `cols`,
 *    `win`. Redirects (303) to `GET /games/{id}`.
 *  - `GET /games/{id}`: render the SSR page for a game; 404 with a
 *    friendly body if the id is unknown or malformed.
 *  - `POST /games/{id}/move`: apply a move (form param `col`).
 *    Redirects (303) on success (post/redirect/get pattern), or
 *    re-renders the same page with an inline error when the engine
 *    rejects the move.
 *  - `GET /healthz`, `GET /api/state`: small diagnostics.
 */
@RestController
@RequestMapping
class Connect4Controller(
    private val pageRenderer: PageRenderer,
    private val gameService: GameService,
) {

    @GetMapping(value = ["/", "/index.html"])
    fun root(
        @RequestParam(defaultValue = "6") rows: Int,
        @RequestParam(defaultValue = "7") cols: Int,
        @RequestParam(name = "win", defaultValue = "4") winLength: Int,
    ): ResponseEntity<Void> {
        val config = sanitizeConfig(rows, cols, winLength)
        val created = gameService.create(config)
        return seeOther("/games/${created.id.value}")
    }

    @PostMapping(value = ["/games"])
    fun createGame(
        @RequestParam(defaultValue = "6") rows: Int,
        @RequestParam(defaultValue = "7") cols: Int,
        @RequestParam(name = "win", defaultValue = "4") winLength: Int,
    ): ResponseEntity<Void> {
        val config = sanitizeConfig(rows, cols, winLength)
        val created = gameService.create(config)
        return seeOther("/games/${created.id.value}")
    }

    @GetMapping(value = ["/games/{id}"], produces = [MediaType.TEXT_HTML_VALUE])
    fun showGame(
        @PathVariable("id") id: String,
        @RequestParam(name = "error", required = false) error: String?,
    ): ResponseEntity<String> {
        val gameId = GameId.from(id) ?: return notFound(id)
        val state = gameService.load(gameId) ?: return notFound(id)
        val model = ConnectFourView.Model(
            state = state,
            moveAction = "/games/${gameId.value}/move",
            error = error?.takeIf { it.isNotBlank() },
            gameId = gameId.value,
        )
        return htmlResponse(pageRenderer.render(model))
    }

    @PostMapping(value = ["/games/{id}/move"])
    fun applyMove(
        @PathVariable("id") id: String,
        @RequestParam("col") col: Int,
    ): ResponseEntity<*> {
        val gameId = GameId.from(id) ?: return notFound(id)
        return when (val outcome = gameService.applyMove(gameId, col)) {
            MoveOutcome.GameNotFound -> notFound(id)
            is MoveOutcome.Applied -> seeOther("/games/${gameId.value}")
            is MoveOutcome.Rejected -> htmlResponse(
                pageRenderer.render(
                    ConnectFourView.Model(
                        state = outcome.unchanged,
                        moveAction = "/games/${gameId.value}/move",
                        error = outcome.reason,
                        gameId = gameId.value,
                    ),
                ),
            )
        }
    }

    @GetMapping("/healthz", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun health(): String = "ok"

    @GetMapping("/api/state", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun encodedDefaultState(): String =
        StateCodec.encode(ConnectFourEngine.newGame(GameConfig.DEFAULT))

    private fun sanitizeConfig(rows: Int, cols: Int, winLength: Int): GameConfig =
        if (GameConfig.validate(rows, cols, winLength) == null) {
            GameConfig(rows, cols, winLength)
        } else {
            GameConfig.DEFAULT
        }

    private fun seeOther(location: String): ResponseEntity<Void> =
        ResponseEntity.status(HttpStatus.SEE_OTHER)
            .header(HttpHeaders.LOCATION, location)
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .build()

    private fun htmlResponse(html: String): ResponseEntity<String> =
        ResponseEntity.ok()
            .header("Content-Type", "text/html;charset=UTF-8")
            .header("Cache-Control", "no-store")
            .body(html)

    private fun notFound(id: String): ResponseEntity<String> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .header("Content-Type", "text/html;charset=UTF-8")
            .body(notFoundBody(id))

    private fun notFoundBody(id: String): String =
        "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>Game not found</title></head>" +
            "<body><h1>404 Game not found</h1>" +
            "<p>No game exists for id <code>${escape(id)}</code>.</p>" +
            "<p><a href=\"/\">Start a new game</a></p>" +
            "</body></html>"

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

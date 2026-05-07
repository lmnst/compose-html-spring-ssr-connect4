package connect4.server

import connect4.ssr.DocumentLayout
import connect4.ssr.DocumentRenderer
import connect4.view.WelcomeModel
import connect4.view.WelcomePageView
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Demonstrates that the SSR engine is not Connect-Four-shaped. The same
 * generic [DocumentRenderer] that powers the Connect Four page also
 * powers this informational page. The model type, layout title, and
 * `View` differ; the rendering pipeline does not.
 *
 * This route is server-only: there is no client bundle for it (so no
 * `scriptSrc`), proving that the engine functions as a pure SSR layer
 * without any client-side hydration.
 */
@RestController
class AboutController(
    private val aboutRenderer: AboutRenderer,
) {

    @GetMapping("/about", produces = [MediaType.TEXT_HTML_VALUE])
    fun page(): ResponseEntity<String> {
        val model = WelcomeModel(
            heading = "Compose HTML SSR for Spring",
            intro = "An experimental SSR engine: pure-Kotlin views render to HTML on the JVM and to a live DOM via Compose HTML in the browser.",
            bullets = listOf(
                "The engine has no Connect Four code in it. It renders any View<T>.",
                "Views are pure functions. They run on JVM and JS without modification.",
                "Initial state is embedded as a script payload for client-side hydration.",
                "The Connect Four demo additionally supports server-authoritative play with no JavaScript.",
            ),
        )
        return ResponseEntity.ok()
            .header("Content-Type", "text/html;charset=UTF-8")
            .header("Cache-Control", "no-store")
            .body(aboutRenderer.render(model))
    }
}

/**
 * Spring component holding the configured renderer for the about page.
 * Keeps the controller free of layout configuration and lets tests
 * substitute a different renderer if needed.
 */
@Component
class AboutRenderer {
    private val delegate: DocumentRenderer<WelcomeModel> = DocumentRenderer(
        view = WelcomePageView,
        layout = DocumentLayout(
            title = "About, Compose HTML SSR",
            styleHref = "/static/connect4.css",
            scriptSrc = null,
            extraMeta = listOf("description" to "An SSR engine for Compose HTML, served by Spring Boot."),
        ),
        stateEmbedding = null,
    )

    fun render(model: WelcomeModel): String = delegate.render(model)
}

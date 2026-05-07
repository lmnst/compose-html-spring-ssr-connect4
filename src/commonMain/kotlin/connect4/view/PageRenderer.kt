package connect4.view

import connect4.ssr.DocumentLayout
import connect4.ssr.DocumentRenderer
import connect4.ssr.StateEmbedding
import connect4.state.StateCodec

/**
 * Connect-Four-typed wrapper around the generic [DocumentRenderer]. Lets
 * the Spring controller depend on a small, project-specific type while
 * the underlying engine stays reusable for any [connect4.ssr.View].
 *
 * The renderer is stateless and thread-safe.
 */
class PageRenderer(
    bodyView: ConnectFourView,
    styleHref: String,
    scriptSrc: String,
    initialStateElementId: String = "connect4-initial-state",
    rootElementId: String = "root",
    pretty: Boolean = false,
) {
    private val delegate: DocumentRenderer<ConnectFourView.Model> = DocumentRenderer(
        view = bodyView,
        layout = DocumentLayout(
            title = "Connect Four, Compose HTML SSR",
            styleHref = styleHref,
            scriptSrc = scriptSrc,
            rootElementId = rootElementId,
        ),
        stateEmbedding = ConnectFourStateEmbedding(initialStateElementId),
        pretty = pretty,
    )

    /** Render an SSR page for the provided [model]. */
    fun render(model: ConnectFourView.Model): String = delegate.render(model)

    private class ConnectFourStateEmbedding(
        private val elementId: String,
    ) : StateEmbedding<ConnectFourView.Model> {
        override fun encode(model: ConnectFourView.Model): StateEmbedding.EmbeddedState =
            StateEmbedding.EmbeddedState(
                elementId = elementId,
                mediaType = "application/x-connect4-state",
                payload = StateCodec.encode(model.state),
            )
    }
}

package connect4.ssr

/**
 * Static configuration for an SSR document. Carries metadata that the
 * renderer drops into `<head>` and the URL of the script bundle that
 * (optionally) hydrates the page on the client.
 *
 * Instances are reused across requests and must be safe to share. None
 * of the fields are mutated after construction.
 */
data class DocumentLayout(
    /** Browser tab title. Inserted as escaped text. */
    val title: String,
    /** Optional `<link rel="stylesheet">` href. Empty means no stylesheet tag. */
    val styleHref: String? = null,
    /** Optional script `src` for the client bundle. Empty means no script tag. */
    val scriptSrc: String? = null,
    /** `<html lang>` value. */
    val language: String = "en",
    /**
     * The id of the element that wraps the body view. The Compose HTML
     * client mounts at this id, so server-side and client-side trees share
     * the same root.
     */
    val rootElementId: String = "root",
    /**
     * Extra `<meta>` tags rendered into `<head>` in the order given. Each
     * pair is (name or property, content). The renderer escapes both.
     */
    val extraMeta: List<Pair<String, String>> = emptyList(),
)

/**
 * Optional pluggable contract for embedding initial state into the SSR
 * page. The renderer uses this to drop a `<script type="...">` block next
 * to the root element. Implementations stringify the model into a payload
 * the client can read at startup.
 *
 * The renderer guarantees the payload is emitted inside a script raw-text
 * context, so `</script>` substrings cannot break out (see
 * [HtmlRenderer]). Implementations therefore do not need to escape HTML
 * themselves.
 */
fun interface StateEmbedding<in T> {
    /** Returns the encoded payload, or null to skip the embed for this model. */
    fun encode(model: T): EmbeddedState?

    data class EmbeddedState(
        val elementId: String,
        val mediaType: String,
        val payload: String,
    )
}

/**
 * Generic SSR document renderer. Wraps any [View] of [T] in a complete
 * HTML5 document defined by [layout] and (optionally) embeds initial
 * state via [stateEmbedding] for client-side hydration.
 *
 * The renderer is stateless and thread-safe.
 */
class DocumentRenderer<T>(
    private val view: View<T>,
    private val layout: DocumentLayout,
    private val stateEmbedding: StateEmbedding<T>? = null,
    pretty: Boolean = false,
) {
    private val htmlRenderer = HtmlRenderer(pretty = pretty)

    fun render(model: T): String =
        htmlRenderer.renderDocument(buildDocument(model))

    private fun buildDocument(model: T): HtmlNode = html {
        element("html", attrs = mapOf("lang" to layout.language)) {
            head {
                element("meta", attrs = mapOf("charset" to "UTF-8"))
                element(
                    "meta",
                    attrs = mapOf(
                        "name" to "viewport",
                        "content" to "width=device-width, initial-scale=1.0",
                    ),
                )
                for ((name, content) in layout.extraMeta) {
                    element("meta", attrs = mapOf("name" to name, "content" to content))
                }
                title { +layout.title }
                if (layout.styleHref != null) {
                    element(
                        "link",
                        attrs = mapOf("rel" to "stylesheet", "href" to layout.styleHref),
                    )
                }
            }
            body {
                element(
                    "div",
                    attrs = mapOf("id" to layout.rootElementId, "data-ssr" to "1"),
                ) {
                    add(view.render(model))
                }
                stateEmbedding?.encode(model)?.let { embed ->
                    script(type = embed.mediaType, id = embed.elementId, content = embed.payload)
                }
                if (layout.scriptSrc != null) {
                    element(
                        "script",
                        attrs = mapOf("src" to layout.scriptSrc, "defer" to "defer"),
                    )
                }
            }
        }
    }
}

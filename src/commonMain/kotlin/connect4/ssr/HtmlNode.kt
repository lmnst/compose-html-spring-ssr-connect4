package connect4.ssr

/**
 * Pure data representation of an HTML tree. Built by the [Html] DSL on any
 * platform and rendered to a string by [HtmlRenderer]. Carries no behavior
 * (no event handlers, no Compose runtime), so the same tree describes both
 * what the server emits in its first response and what the client expects
 * to find in the DOM at hydration time.
 */
sealed interface HtmlNode {

    /** A normal HTML element: `<tag attr="...">children</tag>`. */
    data class Element(
        val tag: String,
        val attrs: Map<String, String> = emptyMap(),
        val children: List<HtmlNode> = emptyList(),
    ) : HtmlNode {
        init {
            require(tag.isNotBlank()) { "tag must not be blank" }
            require(tag.all { it.isLetterOrDigit() || it == '-' }) {
                "tag '$tag' contains characters that are not safe to emit raw"
            }
        }
    }

    /**
     * Text content. Always escaped by [HtmlRenderer]. There is no raw-HTML
     * node by design: anything you can put in the tree is safe to emit.
     */
    data class Text(val value: String) : HtmlNode

    /**
     * A run of sibling nodes with no surrounding element. Useful when a
     * builder body produces several siblings and the caller expects a
     * single node.
     */
    data class Fragment(val children: List<HtmlNode>) : HtmlNode
}

package connect4.ssr

/**
 * A pure function from a model `T` to an [HtmlNode] tree. Views are platform
 * independent: the same [View] is rendered to a string on the JVM (server
 * side) and is referenced by the Compose HTML client to keep server and
 * client output structurally aligned.
 *
 * Implementations must be deterministic and free of side effects.
 */
fun interface View<T> {
    fun render(model: T): HtmlNode
}

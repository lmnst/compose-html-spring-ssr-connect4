package connect4.ssr

/**
 * A small HTML builder DSL that produces an [HtmlNode] tree. The goal is to
 * read like JetBrains' kotlinx.html or the Compose HTML DSL while staying
 * platform-agnostic and free of runtime dependencies.
 *
 * Example:
 *
 * ```
 * val node = html {
 *     div(class_ = "panel") {
 *         h1 { +"Title" }
 *         button(attrs = mapOf("data-col" to "3")) { +"Drop" }
 *     }
 * }
 * ```
 */

/** Marker that prevents nested builders from leaking each other's receivers. */
@DslMarker
annotation class HtmlDsl

/** Entry point: returns the single root [HtmlNode] produced by [block]. */
fun html(block: HtmlScope.() -> Unit): HtmlNode {
    val scope = HtmlScope()
    scope.block()
    return when (scope.nodes.size) {
        0 -> HtmlNode.Fragment(emptyList())
        1 -> scope.nodes.single()
        else -> HtmlNode.Fragment(scope.nodes.toList())
    }
}

@HtmlDsl
class HtmlScope {
    internal val nodes = mutableListOf<HtmlNode>()

    /** Append a child node directly. */
    fun add(node: HtmlNode) {
        nodes += node
    }

    /** Append literal (escaped) text content. */
    operator fun String.unaryPlus() {
        nodes += HtmlNode.Text(this)
    }

    /** Append an element with a free-form tag name. Generic escape hatch. */
    fun element(
        tag: String,
        class_: String? = null,
        id: String? = null,
        attrs: Map<String, String> = emptyMap(),
        block: HtmlScope.() -> Unit = {},
    ): HtmlNode.Element {
        val merged = LinkedHashMap<String, String>(attrs.size + 2)
        if (class_ != null) merged["class"] = class_
        if (id != null) merged["id"] = id
        merged.putAll(attrs)
        val child = HtmlScope().also(block)
        val el = HtmlNode.Element(tag, merged, child.nodes.toList())
        nodes += el
        return el
    }

    fun head(block: HtmlScope.() -> Unit = {}) =
        element("head", block = block)

    fun body(block: HtmlScope.() -> Unit = {}) =
        element("body", block = block)

    fun title(block: HtmlScope.() -> Unit = {}) =
        element("title", block = block)

    fun noscript(class_: String? = null, block: HtmlScope.() -> Unit = {}) =
        element("noscript", class_, null, emptyMap(), block)

    fun div(class_: String? = null, id: String? = null, attrs: Map<String, String> = emptyMap(), block: HtmlScope.() -> Unit = {}) =
        element("div", class_, id, attrs, block)

    fun span(class_: String? = null, attrs: Map<String, String> = emptyMap(), block: HtmlScope.() -> Unit = {}) =
        element("span", class_, null, attrs, block)

    fun h1(class_: String? = null, block: HtmlScope.() -> Unit = {}) =
        element("h1", class_, null, emptyMap(), block)

    fun h2(class_: String? = null, block: HtmlScope.() -> Unit = {}) =
        element("h2", class_, null, emptyMap(), block)

    fun p(class_: String? = null, block: HtmlScope.() -> Unit = {}) =
        element("p", class_, null, emptyMap(), block)

    fun a(href: String, class_: String? = null, block: HtmlScope.() -> Unit = {}) =
        element("a", class_, null, mapOf("href" to href), block)

    fun ul(class_: String? = null, block: HtmlScope.() -> Unit = {}) =
        element("ul", class_, null, emptyMap(), block)

    fun li(class_: String? = null, block: HtmlScope.() -> Unit = {}) =
        element("li", class_, null, emptyMap(), block)

    fun button(class_: String? = null, attrs: Map<String, String> = emptyMap(), block: HtmlScope.() -> Unit = {}) =
        element("button", class_, null, attrs, block)

    /**
     * Render a `<form>` element. Both [action] and [method] are required so
     * the rendered HTML is unambiguous about where the form posts.
     */
    fun form(
        action: String,
        method: String = "post",
        class_: String? = null,
        attrs: Map<String, String> = emptyMap(),
        block: HtmlScope.() -> Unit = {},
    ): HtmlNode.Element {
        val merged = LinkedHashMap<String, String>(attrs.size + 3)
        merged["action"] = action
        merged["method"] = method
        if (class_ != null) merged["class"] = class_
        merged.putAll(attrs)
        val child = HtmlScope().also(block)
        val el = HtmlNode.Element("form", merged, child.nodes.toList())
        nodes += el
        return el
    }

    /**
     * Render an `<input>`. `type` defaults to `hidden`, the most common case
     * inside SSR forms (e.g. carrying a server-side identifier alongside the
     * user-facing fields).
     */
    fun input(
        name: String,
        value: String,
        type: String = "hidden",
        attrs: Map<String, String> = emptyMap(),
    ): HtmlNode.Element {
        val merged = LinkedHashMap<String, String>(attrs.size + 3)
        merged["type"] = type
        merged["name"] = name
        merged["value"] = value
        merged.putAll(attrs)
        return element("input", attrs = merged)
    }

    fun script(type: String? = null, id: String? = null, content: String) {
        val attrs = LinkedHashMap<String, String>()
        if (type != null) attrs["type"] = type
        if (id != null) attrs["id"] = id
        nodes += HtmlNode.Element("script", attrs, listOf(HtmlNode.Text(content)))
    }
}

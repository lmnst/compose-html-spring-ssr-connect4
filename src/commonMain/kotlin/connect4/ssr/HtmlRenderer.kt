package connect4.ssr

/**
 * Renders an [HtmlNode] tree to a string of well-formed HTML. The output is
 * byte-for-byte deterministic: attribute order is preserved from the builder,
 * indentation is two spaces when [pretty] is true, and there is no extra
 * whitespace inside text nodes.
 *
 * Safety:
 *  - Text node content is HTML-escaped.
 *  - Attribute values are HTML-escaped (double-quote and ampersand at minimum).
 *  - Inside `<script>` blocks the renderer escapes the closing `</` token so
 *    that a JSON payload containing the literal string `</script>` cannot
 *    break out of the script context.
 */
class HtmlRenderer(
    private val pretty: Boolean = false,
) {
    fun render(node: HtmlNode): String = buildString { write(node, depth = 0) }

    /**
     * Convenience: render a full HTML5 document. The renderer prepends
     * `<!DOCTYPE html>` so the result is a complete page when [root] is
     * an `<html>` element.
     */
    fun renderDocument(root: HtmlNode): String =
        "<!DOCTYPE html>" + (if (pretty) "\n" else "") + render(root)

    private fun StringBuilder.write(node: HtmlNode, depth: Int) {
        when (node) {
            is HtmlNode.Text -> append(escapeText(node.value))
            is HtmlNode.Fragment -> {
                node.children.forEachIndexed { idx, child ->
                    if (pretty && idx > 0) append('\n').appendIndent(depth)
                    write(child, depth)
                }
            }
            is HtmlNode.Element -> writeElement(node, depth)
        }
    }

    private fun StringBuilder.writeElement(el: HtmlNode.Element, depth: Int) {
        append('<').append(el.tag)
        for ((k, v) in el.attrs) {
            append(' ').append(k).append("=\"").append(escapeAttr(v)).append('"')
        }
        if (el.tag in VOID_ELEMENTS) {
            append(" />")
            return
        }
        append('>')

        val isRawText = el.tag.equals("script", ignoreCase = true) ||
            el.tag.equals("style", ignoreCase = true)
        if (isRawText) {
            for (child in el.children) {
                if (child is HtmlNode.Text) append(escapeRaw(child.value))
                else write(child, depth + 1)
            }
            append("</").append(el.tag).append('>')
            return
        }

        val onlyText = el.children.all { it is HtmlNode.Text }
        if (pretty && el.children.isNotEmpty() && !onlyText) {
            for (child in el.children) {
                append('\n').appendIndent(depth + 1)
                write(child, depth + 1)
            }
            append('\n').appendIndent(depth)
        } else {
            for (child in el.children) write(child, depth + 1)
        }
        append("</").append(el.tag).append('>')
    }

    private fun StringBuilder.appendIndent(depth: Int): StringBuilder {
        repeat(depth) { append("  ") }
        return this
    }

    private fun escapeText(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            else -> append(c)
        }
    }

    private fun escapeAttr(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '"' -> append("&quot;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            else -> append(c)
        }
    }

    /**
     * Inside `<script>` and `<style>` raw-text contexts, HTML escaping is
     * inappropriate (it would break JSON). The only sequence that can end
     * the context is a literal closing tag. Replacing `</` with `<\/`
     * preserves JSON-validity while preventing a payload from breaking out.
     */
    private fun escapeRaw(s: String): String = s.replace("</", "<\\/")

    companion object {
        /** HTML5 void elements: written as `<tag />` with no closing tag. */
        val VOID_ELEMENTS: Set<String> = setOf(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr",
        )
    }
}

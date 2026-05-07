package connect4.ssr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HtmlRendererTest {

    private val r = HtmlRenderer()

    @Test
    fun renders_empty_div() {
        val node = html { div() }
        assertEquals("<div></div>", r.render(node))
    }

    @Test
    fun renders_attributes_in_builder_order() {
        val node = html {
            element("div", attrs = linkedMapOf("data-a" to "1", "data-b" to "2", "data-c" to "3"))
        }
        assertEquals("""<div data-a="1" data-b="2" data-c="3"></div>""", r.render(node))
    }

    @Test
    fun class_and_id_appear_before_extra_attrs() {
        val node = html {
            div(class_ = "panel", id = "root", attrs = mapOf("data-x" to "y"))
        }
        assertEquals("""<div class="panel" id="root" data-x="y"></div>""", r.render(node))
    }

    @Test
    fun text_is_html_escaped() {
        val node = html { p { +"<script>alert('xss')</script> & friends" } }
        assertEquals(
            "<p>&lt;script&gt;alert('xss')&lt;/script&gt; &amp; friends</p>",
            r.render(node),
        )
    }

    @Test
    fun attribute_values_escape_quotes_and_ampersands() {
        val node = html {
            element("a", attrs = mapOf("href" to """https://x.test/?a=1&b="two"""", "title" to "<hi>"))
        }
        val out = r.render(node)
        assertTrue(out.contains("""href="https://x.test/?a=1&amp;b=&quot;two&quot;""""))
        assertTrue(out.contains("""title="&lt;hi&gt;""""))
    }

    @Test
    fun void_elements_emit_self_closing_form() {
        val node = html { element("br"); element("img", attrs = mapOf("src" to "logo.png")) }
        assertEquals("""<br /><img src="logo.png" />""", r.render(node))
    }

    @Test
    fun void_element_ignores_children_in_construction_only_via_renderer_contract() {
        // The DSL allows misuse, but the renderer treats voids as childless.
        val el = HtmlNode.Element("br", children = listOf(HtmlNode.Text("ignored")))
        assertEquals("<br />", r.render(el))
    }

    @Test
    fun script_block_escapes_closing_tag_to_prevent_breakout() {
        val payload = """{"text":"</script><b>boom</b>"}"""
        val node = html { script(type = "application/json", id = "init", content = payload) }
        val out = r.render(node)
        assertFalse(out.contains("</script><b>"), "raw </script> must not appear inside a script block")
        assertTrue(out.contains("""<\/script>"""))
        assertTrue(out.endsWith("</script>"))
    }

    @Test
    fun style_block_also_escapes_closing_tag() {
        val css = "body::before{content:'</style>';}"
        val node = html { element("style") { +css } }
        val out = r.render(node)
        // The single </style> at the end is the real one; no second </style> should appear.
        val occurrences = out.split("</style>").size - 1
        assertEquals(1, occurrences)
    }

    @Test
    fun nested_children_render_in_order() {
        val node = html {
            div(class_ = "a") {
                span(class_ = "b") { +"x" }
                span(class_ = "c") { +"y" }
            }
        }
        assertEquals(
            """<div class="a"><span class="b">x</span><span class="c">y</span></div>""",
            r.render(node),
        )
    }

    @Test
    fun fragment_emits_siblings_with_no_wrapping_element() {
        val node = html {
            +"hello "
            span { +"world" }
        }
        assertEquals("hello <span>world</span>", r.render(node))
    }

    @Test
    fun render_document_prepends_doctype() {
        val node = html { element("html") { element("body") { +"hi" } } }
        assertEquals("<!DOCTYPE html><html><body>hi</body></html>", r.render(node).let { "<!DOCTYPE html>$it" })
        assertEquals("<!DOCTYPE html><html><body>hi</body></html>", r.renderDocument(node))
    }

    @Test
    fun pretty_renderer_indents_block_children() {
        val pretty = HtmlRenderer(pretty = true)
        val node = html {
            div(class_ = "outer") {
                div(class_ = "inner") { +"text" }
            }
        }
        val out = pretty.render(node)
        assertTrue(out.contains("\n  <div class=\"inner\">text</div>"))
    }

    @Test
    fun rejects_unsafe_tag_names() {
        assertFails { HtmlNode.Element("div onclick=alert(1)") }
        assertFails { HtmlNode.Element("") }
    }
}

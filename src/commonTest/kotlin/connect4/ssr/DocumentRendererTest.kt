package connect4.ssr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentRendererTest {

    private data class Greeting(val name: String, val pieces: List<String>)

    private val greetingView: View<Greeting> = View { g ->
        html {
            div(class_ = "greet") {
                h1 { +"Hello, ${g.name}" }
                ul {
                    for (p in g.pieces) li { +p }
                }
            }
        }
    }

    private val baseLayout = DocumentLayout(
        title = "Greet",
        styleHref = "/static/style.css",
        scriptSrc = "/static/app.js",
    )

    @Test
    fun renders_a_complete_html5_document() {
        val r = DocumentRenderer(greetingView, baseLayout)
        val out = r.render(Greeting("World", listOf("a", "b")))
        assertTrue(out.startsWith("<!DOCTYPE html>"), out.take(40))
        assertTrue(out.contains("<html lang=\"en\">"))
        assertTrue(out.contains("<title>Greet</title>"))
        assertTrue(out.contains("Hello, World"))
        assertTrue(out.contains("<li>a</li><li>b</li>"))
    }

    @Test
    fun model_changes_propagate_through_the_same_renderer() {
        val r = DocumentRenderer(greetingView, baseLayout)
        val a = r.render(Greeting("Alice", listOf("x")))
        val b = r.render(Greeting("Bob", listOf("y", "z")))
        assertTrue(a.contains("Hello, Alice"))
        assertTrue(b.contains("Hello, Bob"))
        assertTrue(b.contains("<li>y</li><li>z</li>"))
        assertFalse(a.contains("Bob"))
    }

    @Test
    fun extra_meta_tags_appear_in_head_in_order() {
        val layout = baseLayout.copy(
            extraMeta = listOf("description" to "demo", "robots" to "noindex"),
        )
        val r = DocumentRenderer(greetingView, layout)
        val out = r.render(Greeting("X", emptyList()))
        val descIdx = out.indexOf("""name="description"""")
        val robotsIdx = out.indexOf("""name="robots"""")
        val titleIdx = out.indexOf("<title>")
        assertTrue(descIdx in 1..<robotsIdx && robotsIdx < titleIdx)
    }

    @Test
    fun script_and_style_tags_are_omitted_when_layout_does_not_provide_them() {
        val layout = baseLayout.copy(styleHref = null, scriptSrc = null)
        val r = DocumentRenderer(greetingView, layout)
        val out = r.render(Greeting("X", emptyList()))
        assertFalse(out.contains("<link"))
        assertFalse(out.contains("<script src="))
    }

    @Test
    fun state_embedding_emits_one_script_with_the_payload_when_provided() {
        val embed = StateEmbedding<Greeting> { g ->
            StateEmbedding.EmbeddedState(
                elementId = "greet-state",
                mediaType = "application/x-greet",
                payload = "name=${g.name}",
            )
        }
        val r = DocumentRenderer(greetingView, baseLayout, embed)
        val out = r.render(Greeting("Carol", emptyList()))
        val match = Regex(
            """<script type="application/x-greet" id="greet-state">([^<]+)</script>""",
        ).find(out)
        assertTrue(match != null && match.groupValues[1] == "name=Carol", out)
    }

    @Test
    fun state_embedding_returning_null_skips_the_embed_for_that_request() {
        val embed = StateEmbedding<Greeting> { g ->
            if (g.name.isEmpty()) null else StateEmbedding.EmbeddedState("id", "text/plain", g.name)
        }
        val r = DocumentRenderer(greetingView, baseLayout, embed)
        val withEmbed = r.render(Greeting("Z", emptyList()))
        val withoutEmbed = r.render(Greeting("", emptyList()))
        assertTrue(withEmbed.contains("""id="id""""))
        assertFalse(withoutEmbed.contains("""id="id""""))
    }

    @Test
    fun root_element_id_is_configurable() {
        val layout = baseLayout.copy(rootElementId = "mount")
        val r = DocumentRenderer(greetingView, layout)
        val out = r.render(Greeting("X", emptyList()))
        assertTrue(out.contains("""id="mount" data-ssr="1""""))
    }

    @Test
    fun body_view_renders_inside_root_mount_element() {
        val r = DocumentRenderer(greetingView, baseLayout)
        val out = r.render(Greeting("X", emptyList()))
        val rootIdx = out.indexOf("""id="root"""")
        val viewIdx = out.indexOf("""class="greet"""")
        assertTrue(rootIdx > 0 && viewIdx > rootIdx)
    }

    @Test
    fun pretty_renderer_produces_indented_output_otherwise_minified() {
        val pretty = DocumentRenderer(greetingView, baseLayout, pretty = true)
        val flat = DocumentRenderer(greetingView, baseLayout, pretty = false)
        val a = pretty.render(Greeting("X", listOf("p")))
        val b = flat.render(Greeting("X", listOf("p")))
        assertTrue(a.contains("\n  <head>") || a.contains("\n  <body>"))
        assertFalse(b.contains("\n  <head>"))
    }

    @Test
    fun language_attribute_is_configurable() {
        val r = DocumentRenderer(greetingView, baseLayout.copy(language = "fr"))
        val out = r.render(Greeting("X", emptyList()))
        assertTrue(out.contains("<html lang=\"fr\">"))
    }

    @Test
    fun title_text_is_html_escaped() {
        val r = DocumentRenderer(greetingView, baseLayout.copy(title = "<x>&y"))
        val out = r.render(Greeting("X", emptyList()))
        assertTrue(out.contains("<title>&lt;x&gt;&amp;y</title>"))
    }
}

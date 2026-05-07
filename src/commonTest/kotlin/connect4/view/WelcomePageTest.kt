package connect4.view

import connect4.ssr.HtmlRenderer
import kotlin.test.Test
import kotlin.test.assertTrue

class WelcomePageTest {

    private val renderer = HtmlRenderer()

    @Test
    fun renders_heading_intro_and_bullets() {
        val model = WelcomeModel(
            heading = "Welcome",
            intro = "An SSR engine.",
            bullets = listOf("First point.", "Second point."),
        )
        val out = renderer.render(WelcomePageView.render(model))
        assertTrue(out.contains("<h1>Welcome</h1>"))
        assertTrue(out.contains("<p class=\"subtitle\">An SSR engine.</p>"))
        assertTrue(out.contains("<li>First point.</li>"))
        assertTrue(out.contains("<li>Second point.</li>"))
    }

    @Test
    fun links_back_to_the_connect_four_page() {
        val out = renderer.render(
            WelcomePageView.render(WelcomeModel("h", "i", emptyList())),
        )
        assertTrue(out.contains("""<a href="/">the Connect Four page</a>"""))
    }

    @Test
    fun text_content_is_html_escaped() {
        val out = renderer.render(
            WelcomePageView.render(
                WelcomeModel(
                    heading = "<x>&y",
                    intro = "<i>",
                    bullets = listOf("a < b"),
                ),
            ),
        )
        assertTrue(out.contains("<h1>&lt;x&gt;&amp;y</h1>"))
        assertTrue(out.contains("&lt;i&gt;"))
        assertTrue(out.contains("a &lt; b"))
    }
}

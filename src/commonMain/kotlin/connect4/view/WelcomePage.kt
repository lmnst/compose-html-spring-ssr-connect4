package connect4.view

import connect4.ssr.HtmlNode
import connect4.ssr.View
import connect4.ssr.html

/**
 * A non-game [View] that exists to demonstrate the SSR engine is not
 * Connect-Four-shaped. Renders a simple "what is this and how does it
 * work" body that the Spring controller serves at `/about`.
 *
 * The view's model is [WelcomeModel], so the same renderer can produce
 * different body content as the model changes (e.g. a different bullet
 * list per request).
 */
data class WelcomeModel(
    val heading: String,
    val intro: String,
    val bullets: List<String>,
)

object WelcomePageView : View<WelcomeModel> {
    override fun render(model: WelcomeModel): HtmlNode = html {
        div(class_ = "app") {
            div(class_ = "header") {
                h1 { +model.heading }
                p(class_ = "subtitle") { +model.intro }
            }
            div(class_ = "panel") {
                ul(class_ = "welcome-list") {
                    for (b in model.bullets) {
                        li { +b }
                    }
                }
            }
            div(class_ = "panel") {
                p {
                    +"Try the demo at "
                    a(href = "/") { +"the Connect Four page" }
                    +"."
                }
            }
        }
    }
}

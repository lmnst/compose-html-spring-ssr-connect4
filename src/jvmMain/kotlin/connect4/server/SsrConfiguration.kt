package connect4.server

import connect4.view.ConnectFourView
import connect4.view.PageRenderer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Registers the page renderer as a Spring bean. The renderer is stateless
 * and thread-safe: it captures the asset URLs and delegates each request
 * to the pure [ConnectFourView] in `commonMain`.
 */
@Configuration
class SsrConfiguration {

    @Bean
    fun pageRenderer(): PageRenderer = PageRenderer(
        bodyView = ConnectFourView,
        styleHref = "/static/connect4.css",
        scriptSrc = "/static/connect4.js",
    )
}

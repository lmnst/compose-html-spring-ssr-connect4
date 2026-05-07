package connect4.server

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Routes static asset URLs to the classpath:/static/ resources folder.
 * Spring Boot's default mapping exposes that folder at the root, but we
 * deliberately use a /static prefix in the page so the page source
 * clearly distinguishes asset URLs from controller URLs.
 */
@Configuration(proxyBeanMethods = false)
class WebConfig : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/static/**")
            .addResourceLocations("classpath:/static/")
            .setCachePeriod(0)
    }
}

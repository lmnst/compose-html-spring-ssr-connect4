package connect4.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Entry point for the Compose HTML SSR demo. The Spring application is
 * intentionally minimal, its only responsibility is to host the SSR
 * controller, the static asset for the Compose HTML JS bundle, and the
 * health endpoint.
 */
@SpringBootApplication
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

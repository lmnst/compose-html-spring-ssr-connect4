# Connect Four, Compose HTML SSR for Spring

```kotlin
fun interface View<T> { fun render(model: T): HtmlNode }

object ConnectFourView : View<ConnectFourView.Model> {
    override fun render(model: Model): HtmlNode = html {
        div(class_ = "app") { /* status, board, form */ }
    }
}
```

A Kotlin Multiplatform Connect Four built around a small generic SSR
engine that any `View<T>` can plug into. Spring serves the rendered
HTML; the JVM and the browser run the same view function over the
same model; and the server holds the canonical state of every active
game in a typed repository keyed by an opaque, URL-safe id.

> **The invariant.** The server holds the canonical state of every
> game in its repository, keyed by id. Moves are applied by the same
> pure engine the client runs, but the engine consults
> repository-owned state, never a state field on the request body.
> A no-JS player can tamper with form fields all day; they cannot
> forge a board, fast-forward turns, or resurrect a finished game.

## Run it

```bash
./gradlew bootRun
```

```bash
$ curl -i http://localhost:8080/
HTTP/1.1 303 See Other
Location: /games/m9k3qr1xs7
Cache-Control: no-store

$ curl -s http://localhost:8080/games/m9k3qr1xs7 | head -1
<!DOCTYPE html><html lang="en"><head><meta charset="UTF-8"> ...
```

Open `http://localhost:8080` in a browser. The address bar redirects
to `/games/{id}` for a freshly created game. With JavaScript
disabled, every column click submits a normal form POST and the
server redirects back to a GET on the same id. With JavaScript on,
the Compose HTML client mounts at `#root` and takes over for
interactive turns. `/about` is a non-game page rendered through the
same engine, with no client bundle, to prove the engine is not
Connect-Four-shaped.

## Anatomy of a request

Three pieces of code make the invariant load-bearing rather than
aspirational.

### Server-authoritative repository

`InMemoryGameRepository` keeps every active game's `GameState` in a
`ConcurrentHashMap`, keyed by a `GameId` of 10 random characters
drawn from `[a-z0-9]` (so 36^10 ~= 3.7e15 ids). Mutations go through
`ConcurrentHashMap.compute`, so a refresh-and-submit race against
the same id serializes at the map entry; different ids never block
each other. The id is the only handle the client ever has on a
game; the controller validates it through `GameId.from`, returning
404 with a friendly body for unknown or malformed ids. There are no
sessions and no cookies. The form's hidden field carries the same
id that already appears in the URL, so the server does not need to
trust it for routing.

### The post/redirect/get loop without JavaScript

The board is wrapped in a real `<form action="/games/{id}/move"
method="post">` and each column is a `<button name="col" value="N">`.
With JavaScript disabled, every click is a plain HTML form
submission. `Connect4Controller.applyMove` reads the column out of
the form, calls `GameService.applyMove(id, col)`, and dispatches on
a sealed `MoveOutcome`:

- `Applied` returns `303 See Other` with `Location: /games/{id}`.
  The browser follows up with a GET, so refreshing the page never
  re-submits the move.
- `Rejected` (full column, game already over) re-renders the same
  page with an inline error and a 200 response. The repository
  state is untouched.
- `GameNotFound` returns 404.

`GameService.applyMove` is the only place a move runs. It loads the
canonical state from the repository inside an atomic `compute`,
calls `ConnectFourEngine.move`, and stores the result. The engine
is pure Kotlin and lives in `commonMain`, so the same function runs
in the browser without modification.

### Hydration

`DocumentRenderer<T>` writes the body view inside
`<div id="root" data-ssr="1">` and emits a sibling `<script
type="application/x-connect4-state">` carrying the encoded
`GameState`. On startup the Compose HTML client reads that script
through `StateCodec.decode`, then mounts a Compose tree at `#root`
that produces the same DOM the server emitted, but now driven by
Compose state instead of HTML form submissions. From mount, column
clicks update local Compose state; a hard refresh returns to the
server's view of the world.

The codec is dependency-free Kotlin in `commonMain` and is exercised
on **both** targets: a `commonTest` suite and a JS-target
`StateCodecJsTest` (so the SSR-embedded payload provably decodes the
same way in compiled JS as it does on the JVM).

## The generic SSR engine

The engine is **not** Connect-Four-shaped. The Connect Four demo is
a small bundle of types layered on top of it.

```kotlin
// Generic, reusable, in commonMain/connect4/ssr
sealed interface HtmlNode { /* Element, Text, Fragment */ }
fun html(block: HtmlScope.() -> Unit): HtmlNode    // type-safe DSL
class HtmlRenderer(pretty: Boolean = false)        // emits well-formed HTML

fun interface View<T> { fun render(model: T): HtmlNode }
data class DocumentLayout(val title: String, /* assets, lang, meta */)
fun interface StateEmbedding<in T> { fun encode(model: T): EmbeddedState? }
class DocumentRenderer<T>(
    view: View<T>,
    layout: DocumentLayout,
    stateEmbedding: StateEmbedding<T>? = null,
)

// Connect-Four-specific, in commonMain/connect4/view
object ConnectFourView : View<ConnectFourView.Model>
object WelcomePageView : View<WelcomeModel>        // proves genericity
class PageRenderer(...)                            // typed wrapper over DocumentRenderer
```

| Type | Role |
|---|---|
| `HtmlNode` | Sealed AST: `Element`, `Text`, `Fragment`. Tag names validated at construction time. |
| `HtmlRenderer` | Depth-first emitter. Escapes text and attribute values. Replaces `</` with `<\/` inside `<script>` and `<style>` raw-text contexts so a payload cannot break out. |
| `View<T>` | Pure function `T -> HtmlNode`. Platform-agnostic, deterministic, no platform deps. |
| `DocumentRenderer<T>` | Wraps any `View<T>` in a complete HTML5 document defined by `DocumentLayout`. Optionally plugs in a `StateEmbedding<T>`. |
| `StateEmbedding<T>` | Optional state embed: produces a `<script type="...">` block placed next to the root mount. |

`AboutController` exercises the engine over a different model.
`WelcomePageView` produces a non-game informational page through the
same `DocumentRenderer` pipeline, with no client bundle and no state
embedding.

## Endpoints

| Method | Path | What it does |
|---|---|---|
| GET | `/` | Create a new game and `303` to `/games/{id}`. Optional `rows`, `cols`, `win` query params; invalid combinations fall back to the default 6x7-with-4 config. |
| POST | `/games` | Form-create a new game with the same params. Same redirect. |
| GET | `/games/{id}` | Render the SSR page for a game. 404 with a friendly body if the id is unknown or malformed. |
| POST | `/games/{id}/move` | Apply a move (form param `col`). `303` to `/games/{id}` on success; 200 with an inline error on rejection; 404 if the id is gone. |
| GET | `/about` | A non-game SSR page rendered through the same generic engine, with no JS bundle. |
| GET | `/healthz` | `ok`. |
| GET | `/api/state` | The encoded default initial state, useful for diagnostics and tests. |

`/static/connect4.js` and `/static/connect4.css` are the Compose HTML
client bundle and stylesheet, copied into the JVM jar at build time
and served by Spring's default static-resource handler.

## Tests

| Layer | Tests | Run with | What it covers |
|---|---:|---|---|
| Pure engine | 13 | `./gradlew jvmTest` | All four win axes, draw, full-column rejection, invalid column, configurable win length, post-game lockout, gravity, fresh-game invariants. |
| SSR engine | 25 | `./gradlew jvmTest` | HTML escaping, attribute order, void elements, script and style raw-text safety, fragments, doctype, pretty-printing, generic `View<T>` over an arbitrary model, optional embedding, configurable root id and language. |
| State codec | 9 | `./gradlew jvmTest` | Roundtrips for fresh, mid-game, and won states; unknown version, truncated payload, corrupt cells, wrong cell count, invalid player and status, HTML safety. |
| Views | 22 | `./gradlew jvmTest` | Default rendering, ARIA labels, full-column disabled buttons, win-line classes, draw text, error panel, page renderer, welcome page, HTML escaping. |
| Repository | 13 | `./gradlew jvmTest` | `GameId` validation, atomic update, isolation between ids, concurrent writes preserving the gravity invariant. |
| Service | 6 | `./gradlew jvmTest` | Create/load/applyMove orchestration through the sealed `MoveOutcome`. |
| Spring controller (MockMvc) | 17 | `./gradlew jvmTest` | All routes through real Spring wiring, redirects, 404s, inline errors, two-id isolation, post-game lockout, static assets, `/about`. |
| State codec on JS | 6 | `./gradlew jsBrowserTest` | Roundtrip on the compiled JS target so the SSR-embedded payload decodes identically in the browser. |

105 JVM tests across 8 suites. The JS target additionally re-runs
the engine, SSR, codec, and view suites in the browser, plus
`StateCodecJsTest` for cross-target codec parity.

The Spring tests are real `@SpringBootTest` MockMvc tests, not unit
tests of the controller in isolation. The service layer is tested
directly against the real repository, not a mock; the concurrency
test uses an actual thread pool to verify the atomic-update
invariant.

## Build

```bash
./gradlew clean check                      # compile and run all tests
./gradlew bootRun                          # run the SSR server on :8080
./gradlew jsBrowserDevelopmentRun          # CSR-only dev harness for the JS UI
./gradlew jsBrowserProductionWebpack       # build the production JS bundle only
./gradlew jvmJar                           # JVM jar including the JS bundle as static resources
```

Requires JDK 17 or newer. The Gradle 8 wrapper is included; no
system Gradle install is needed.

The Spring Boot Gradle plugin is **not** applied. Its `bootJar` task
does not understand Kotlin Multiplatform variants, so `bootRun` here
is a small `JavaExec` task that runs `connect4.server.ApplicationKt`
from the JVM jar plus the JVM runtime classpath. The Compose HTML
production bundle (`connect4.js`, `connect4.css`) is synced into the
JVM resources at build time, so `/static/connect4.js` is served by
Spring's default static-resource handler with no extra wiring.

## Project layout

```
src/commonMain/kotlin/connect4/
    game/      Pure Connect-N engine. No platform deps.
    ssr/       HtmlNode AST, builder DSL, HtmlRenderer,
               View<T>, DocumentRenderer<T>, StateEmbedding<T>.
    state/     Versioned codec used by both server and client.
    repo/      GameRepository contract and GameId value type.
    view/      ConnectFourView (game), WelcomePageView,
               PageRenderer (Connect-Four-typed wrapper).

src/jvmMain/kotlin/connect4/
    server/    Spring Boot Application, controllers, GameService,
               InMemoryGameRepository, WebConfig, SsrConfiguration.

src/jsMain/kotlin/connect4/
    Main.kt    Hydration entry point.
    ui/        Compose HTML composables and persistence glue.
```

`commonMain` has zero declared dependencies. The Compose compiler is
configured to target the JS platform only
(`composeCompiler.targetKotlinPlatforms`), so neither the Compose
runtime nor any browser API leaks into the shared engine.

## Limitations

- The repository is in-memory and process-local. Restarting the
  server clears the registry; horizontally scaled deployments would
  need a shared store. The `GameRepository` contract is designed so
  swapping in Redis or Postgres is a one-class change.
- The configuration form (rows, cols, win) appears only after
  JavaScript hydration. SSR first paint is always a playable board
  for the requested config; changing the config mid-session
  requires the client.
- The Compose HTML client, once mounted, plays locally against the
  embedded initial state. Synchronizing client moves back to the
  server (so a refresh resumes from the client's position) is a
  deliberate non-goal: the no-JS path already proves the round-trip
  works against server-owned state, and adding a write path
  duplicates that without strengthening the invariant.
- The `connect4.ssr` HTML DSL ships only the elements the project
  uses (`div`, `span`, `h1`, `h2`, `p`, `a`, `ul`, `li`, `button`,
  `head`, `body`, `title`, `noscript`, `form`, `input`, `script`,
  plus a generic `element` escape hatch). It is not a full HTML5
  DSL.

## Reading on

[`ARCHITECTURE.md`](ARCHITECTURE.md) for the request-flow diagrams,
the dependency boundaries between source sets, the hydration rule,
and notes on why the alternative path (running Compose composables
on the JVM) was not taken.

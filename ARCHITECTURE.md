# Architecture

The [README](README.md) covers what the project is, how to run it,
and the load-bearing invariant. [docs/DESIGN.md](docs/DESIGN.md)
covers the points where another plausible answer existed and why
this one was chosen. This document covers the controller-level
request flow, the dependency boundaries, and the SSR engine's
internals at the level a maintainer would want.

## Module layout

The project is a single Gradle module with a Kotlin Multiplatform
build. Source sets are the platform boundary.

```
commonMain
    connect4.game     Pure Connect Four engine.
    connect4.ssr      HtmlNode AST, builder DSL, HtmlRenderer,
                      View<T>, DocumentRenderer<T>, StateEmbedding<T>.
    connect4.state    StateCodec (versioned, dependency-free).
    connect4.repo     GameId, GameRepository, UpdateOutcome.
    connect4.view     ConnectFourView (form-driven game body),
                      WelcomePageView (non-game example),
                      PageRenderer (Connect-Four-typed wrapper).

jvmMain
    connect4.server   Spring Boot Application,
                      Connect4Controller, AboutController,
                      GameService, InMemoryGameRepository, WebConfig.

jsMain
    connect4          Hydration entry point (Main.kt).
    connect4.ui       Compose HTML composables, persistence glue.

commonTest
    Engine, SSR core, codec, repo contract, both views.

jvmTest
    Spring Boot integration tests (MockMvc), repository tests,
    service tests.

jsTest
    Cross-target tests for the shared codec.
```

### Dependency boundary rules

`commonMain` is pure Kotlin. It must not see any of:

- `org.jetbrains.compose`, `androidx.compose`
- `org.w3c`, `kotlinx.browser`
- `org.springframework`

This is enforced structurally. The Compose compiler is configured
to target only the JS platform
(`composeCompiler.targetKotlinPlatforms` in `build.gradle.kts`),
Spring lives on the `jvmMain` source set only, and there are no
dependencies declared on `commonMain.dependencies`.

`jvmMain` depends on Spring Boot starters and the common code. It
does not depend on `jsMain`. The Compose HTML client lives
entirely under `jsMain` and never runs on the JVM.

`jsMain` consumes the same SSR codec as `jvmMain` for state
hydration. Page rendering on the client is done by Compose HTML
composables, not by `HtmlRenderer`. The two code paths agree on
data attributes, class names, and DOM structure so the rendered
HTML and the hydrated DOM are interchangeable.

## Request flows

### First visit and creating a game

```
Browser  ----- GET / ----->  Spring (Connect4Controller.root)
                             |
                             v
                             GameService.create(config)
                                 InMemoryGameRepository.create(state)
                                     -> GameId
                             |
                             v
Browser  <-- 303 See Other Location: /games/{id}
         ----- GET /games/{id} ----->
                             |
                             v
                             GameService.load(id) -> GameState
                             |
                             v
                             ConnectFourView.Model(
                                 state = state,
                                 moveAction = "/games/{id}/move",
                                 gameId = "{id}",
                             )
                             |
                             v
                             PageRenderer.render(model)
                                +--- DocumentRenderer.render
                                       +--- ConnectFourView.render -> HtmlNode
                                       +--- StateEmbedding.encode    -> EmbeddedState
                                       +--- HtmlRenderer.renderDocument -> String
                             |
                             v
Browser  <-- text/html, fully rendered, board playable already.
```

The `303 See Other` redirect on `GET /` is deliberate: the address
bar always reflects a real server-side game id, and refreshing the
page never creates a new game by accident. Refreshing
`GET /games/{id}` returns the **same** game.

### Server-authoritative move (no JavaScript)

```
Browser  ----- POST /games/{id}/move ----->  Spring (Connect4Controller.applyMove)
            col=N                             |
                                              v
                                              GameService.applyMove(id, col)
                                                  InMemoryGameRepository.update(id) {
                                                      ConnectFourEngine.move(state, col)
                                                  }
                                              |
                                              v
                                              MoveOutcome:
                                                  Applied -> 303 to /games/{id}
                                                  Rejected -> render same page
                                                              with inline error
                                                  GameNotFound -> 404
Browser  <-- follows 303 with GET /games/{id} for the new state.
```

The only data the client supplies is the column it clicked. The
server cannot be tricked into accepting a forged board,
fast-forwarding a turn, or resurrecting a finished game by
tampering with hidden fields, because there is no hidden state
field. The id in the form path comes from the URL, which the
server already trusts to identify the game.
[docs/STORY-SERVER-AUTHORITATIVE.md](docs/STORY-SERVER-AUTHORITATIVE.md)
walks through the refactor that arrived at this design.

### Hydration

The Compose HTML client mounts at `#root`. On mount, Compose
builds its own tree under that element, replacing whatever the
SSR controller emitted. From that point on the form is no longer
in the DOM, and column clicks are handled by Compose's
in-process state. The page URL still names the server-side game
id, so a hard refresh returns to the server's view of the world.

This split is intentional. The server is authoritative; the JS
client is an interaction enhancement on top. Synchronizing
client moves back to the server is a deliberate non-goal of
this submission, since the no-JS path already proves the
round-trip works against server-owned state.

## SSR engine internals

### `HtmlNode`

A small sealed hierarchy:

- `Element(tag, attrs, children)` with tag-name validation
- `Text(value)`
- `Fragment(children)`

There is no raw-HTML node by design. Every text payload that
enters the tree is escaped on render, so a future caller cannot
bypass the escape and emit arbitrary HTML.

### `Html` DSL

A type-safe builder with `@DslMarker` to prevent accidental
cross-receiver leaks. The DSL ships only what the project needs:
`div`, `span`, `h1`, `h2`, `p`, `a`, `ul`, `li`, `button`,
`head`, `body`, `title`, `noscript`, `form`, `input`, `script`,
plus a generic `element` escape hatch.

### `HtmlRenderer`

A `StringBuilder`-based depth-first walk. Behavior:

- Attribute order is preserved (the DSL uses `LinkedHashMap`).
- Text content is escaped: `&`, `<`, `>`.
- Attribute values are escaped: `&`, `"`, `<`, `>`.
- Void elements (`br`, `img`, `link`, `meta`, `input`, ...) emit
  `<tag />` with no closing tag.
- Inside `<script>` and `<style>` raw-text contexts, the renderer
  replaces `</` with `<\/`. Tests verify that a `</script>`
  substring inside the embedded state cannot break out.

### `DocumentRenderer<T>`

The reusable, view-agnostic renderer. `Connect4Controller` only
sees `PageRenderer` (a Connect-Four-typed wrapper);
`AboutController` uses the generic `DocumentRenderer<WelcomeModel>`
directly, with no state embedding. Both share the same code
path.

### `View<T>`

A functional interface `fun render(model: T): HtmlNode`. The
Connect Four implementation is `connect4.view.ConnectFourView`
over `ConnectFourView.Model` (game state, move action URL,
optional inline error, optional game id). The welcome
implementation is `connect4.view.WelcomePageView` over
`WelcomeModel`.

## Repository concurrency

Identifier generation: `SecureRandomGameIdGenerator` produces 10
char ids drawn from `[a-z0-9]`. Repository `create` retries up to
eight times on the astronomically unlikely chance of a
collision.

Concurrency: `InMemoryGameRepository.update` runs the caller's
block inside `ConcurrentHashMap.compute`, so read-modify-write
for a single id is atomic. Two refresh-and-submit races against
the same game serialize at the map entry. Different ids never
block each other. The concurrency test in
`InMemoryGameRepositoryTest` exercises the path with an actual
thread pool and asserts the gravity invariant on the resulting
board.

## Spring integration

The server module is intentionally thin:

- `Application.kt` registers `@SpringBootApplication` and calls
  `runApplication`.
- `SsrConfiguration.kt` registers the `PageRenderer` bean wired
  with the static asset URLs the application uses.
- `WebConfig.kt` registers a resource handler that exposes
  `classpath:/static/` at the URL prefix `/static/**`.
- `InMemoryGameRepository.kt` is the only `GameRepository`
  implementation, registered as a `@Component`.
- `GameService.kt` is the `@Service` orchestrating `create`,
  `load`, `applyMove`. It exposes a sealed `MoveOutcome` so the
  controller cannot forget to render the rejected case.
- `Connect4Controller.kt` handles root redirects, `POST /games`,
  `GET /games/{id}`, `POST /games/{id}/move`, `/healthz`,
  `/api/state`. Validates ids through `GameId.from`; unknown or
  malformed ids return 404 with a friendly body.
- `AboutController.kt` is a second controller that renders
  `WelcomePageView` through the generic engine. Its existence
  proves the engine is not Connect-Four-shaped.

The Spring Boot Gradle plugin is **not** applied; see
[docs/DESIGN.md](docs/DESIGN.md#spring-boot-gradle-plugin-or-a-javaexec-task).
The JVM source set declares the Spring starters as ordinary
dependencies, and a small `JavaExec` task named `bootRun` runs
the application with the JVM jar plus the JVM runtime classpath.
The Compose HTML JS distribution (`connect4.js`, `connect4.css`)
is synced into the JVM resources at build time, so
`/static/connect4.js` and `/static/connect4.css` are served by
Spring's static-resource handler with no extra wiring.

## State codec

`StateCodec` produces a single-line, pipe-delimited payload with
the shape `v1|rows|cols|win|currentPlayer|status|cells|lastMove`.
The format is versioned (`v1`) and dependency-free. It is used
for:

- The page renderer encodes state into the embedded script block
  for the JS client to read at startup.
- The client persists state to `localStorage` using the same
  encoding.
- The server's `/api/state` endpoint returns the encoded default
  state for diagnostics and tests.

The codec is **not** used for state round-tripping over `/move`:
the server is authoritative, so `POST /games/{id}/move` only
takes the column the user clicked. The codec remains the bridge
for hydration and for inspecting state out-of-band.

`decode` returns `null` on any unparseable input. The client
treats a null result as "no saved state, start fresh" and clears
the storage slot. The codec is exercised on **both** targets
through `commonTest` and a JS-specific `StateCodecJsTest`, so
the SSR-embedded payload provably decodes the same way in the
browser as on the server.

## Hydration rule

The client never overrides the server's view of the world unless
it has a saved game with the same configuration. The rule is in
`Persistence.resume`:

```
saved == null                        -> ignore (clear corrupt slot)
saved.config != ssr.config           -> ignore (different page intent)
otherwise                            -> resume saved
```

This avoids the common refresh-pop-back surprise where a user
opens a 5x5 game, expects a fresh 5x5 board, and instead sees a
half-played 6x7 game from a previous tab.

# Design notes

The [README](../README.md) explains what this project is and how to
use it. The [ARCHITECTURE](../ARCHITECTURE.md) document explains how
the pieces fit. This document explains the points where another
plausible answer existed and why this one was chosen.

## Design forks

### Compose composables on the JVM, or a generic SSR engine?

The task title commits to three things at once: Compose HTML, a
server-side rendering engine, and Spring. The most literal reading
runs Compose composables on the JVM and serializes the resulting
tree to HTML.

That path is currently blocked. The Compose runtime is tightly
coupled to the platform's snapshot machinery; today the public
distributions for Compose HTML target JS and Wasm, not the JVM.
Building a JVM Compose runtime that produces HTML would mean
porting the snapshot system or shimming around it. Either is more
than a single submission's scope and neither is the actual job.

The chosen path is a small generic SSR engine written in pure
Kotlin: an `HtmlNode` AST, a builder DSL, an `HtmlRenderer` that
walks the tree to a string, a `View<T>` interface
(`fun render(model: T): HtmlNode`), and a `DocumentRenderer<T>`
that wraps any `View<T>` in a complete HTML5 document. The engine
has no Connect Four code in it. The Connect Four demo is then a
small bundle of types layered on top: a `ConnectFourView`, a
typed `PageRenderer`, and a `Connect4Controller` that calls them.

The proof that the engine is reusable is `WelcomePageView`. It
renders a non-game informational page through the same
`DocumentRenderer`, with no client bundle and no state embedding,
served at `/about` by `AboutController`. Same code path; different
model; different output.

### State in the form, or state in the repository?

An early prototype embedded the encoded `GameState` in a hidden
`<input name="state">` next to the column buttons. The form posted
the state plus the column to `/move`; the controller decoded the
state, applied the move, encoded the new state, and re-rendered.
Stateless server, classic SSR.

That design has a problem: the server trusts a client-supplied
representation of server state. A no-JS player can change the
hidden field with the browser's developer tools. They can forge
a board, fast-forward turns, play as the opponent, or resurrect
a finished game by replaying an earlier encoded state. The codec
validates structural integrity (no broken cells, no impossible
status), but it cannot tell a real game in progress from a
fabricated one.

The fix is to make the server authoritative. Game state lives in
an `InMemoryGameRepository` keyed by an opaque `GameId`. The id
is the only thing the client ever holds. Moves go to
`/games/{id}/move` and carry only the column; the engine consults
the repository for the state, applies the move, and stores the
result atomically. There is no hidden state input to tamper with
because there is no state on the form. The controller's test
suite asserts this explicitly:

```kotlin
assertThat(body).doesNotContain("""name="state"""")
```

The lesson generalizes. Any server that accepts a client-supplied
representation of its own state is auditing the field forever.
Accepting only a server-issued handle (the id) and looking up the
state behind it removes the audit problem because there is nothing
to audit.

### `kotlinx.html`, or a custom HTML DSL?

`kotlinx.html` is the obvious dependency. It is widely used, well
maintained, and has the full HTML5 surface.

The chosen path is a small in-house DSL of about 150 lines covering
exactly the elements the project uses (`div`, `span`, `h1`, `h2`,
`p`, `a`, `ul`, `li`, `button`, `head`, `body`, `title`, `noscript`,
`form`, `input`, `script`, plus a generic `element` escape hatch).
Reasons:

1. `commonMain` declares no dependencies. Any third-party library on
   `commonMain` becomes a transitive dependency for both the JVM
   server and the JS client. The DSL keeps the shared codebase
   strictly Kotlin standard library.
2. Exact control over `<script>` raw-text escaping. The renderer
   replaces `</` with `<\/` inside `<script>` and `<style>` raw-text
   contexts so a payload containing the literal substring
   `</script>` cannot break out. The `HtmlRendererTest` suite pins
   this behavior; a fresh in-house renderer is the simplest place
   to enforce it.
3. The DSL doubles as the engine's surface. Other consumers of the
   SSR engine see one API instead of two (the engine's plus
   `kotlinx.html`'s).

The cost is real: the DSL is not a full HTML5 surface, and adding
elements requires an in-repo change. For an engine that ships a
known set of views, the trade is worth it.

### Spring Boot Gradle plugin, or a `JavaExec` task?

Applying `org.springframework.boot` produces a `bootJar` task
that builds an executable jar by walking the JVM source set's
classpath. With Kotlin Multiplatform on the project, the JVM
target produces variants (`jvmRuntimeClasspath`, `jvmJar`,
`jvmApiElements`, ...) that the plugin's task does not understand.
The result is a `bootJar` task that fails to assemble, or
assembles and runs against the wrong classpath, depending on the
plugin version.

The chosen path is to not apply the plugin. Spring Boot starters
are declared as ordinary `jvmMain` dependencies; a small
`JavaExec` task named `bootRun` runs `connect4.server.ApplicationKt`
from the JVM jar plus the JVM runtime classpath:

```kotlin
tasks.register<JavaExec>("bootRun") {
    dependsOn("jvmMainClasses", syncJsBundleToStatic, "jvmProcessResources")
    mainClass.set("connect4.server.ApplicationKt")
    classpath = files(
        tasks.named("jvmJar"),
        configurations.named("jvmRuntimeClasspath"),
    )
    standardInput = System.`in`
}
```

This sidesteps variant resolution entirely. `./gradlew bootRun`
works; tests run; the production JS bundle is synced into the JVM
resources so the server jar serves it at `/static/connect4.js`.

The lesson: when a plugin built for "single JVM project" assumes a
classpath shape the multiplatform target does not produce, the
shortest working route is to do what the plugin would have done by
hand using the primitives (`JavaExec`, `Sync`, `Jar`) instead of
fighting variant resolution.

### Compose compiler scope: everywhere, or JS only?

The Compose Gradle plugin defaults to running the Compose compiler
on every Kotlin compilation. On a multiplatform build that means
`commonMain`, `jvmMain`, and `jsMain` all see the Compose
annotation processor and the runtime that backs `@Composable`
calls.

That undermines the engine's design. `commonMain` is supposed to be
pure Kotlin: the Connect Four engine, the SSR engine, the codec,
and the views all need to be JVM-portable so the server can call
them. Compose runtime on `commonMain` would make those classes
depend on the snapshot machinery and break the portability claim.

The configuration:

```kotlin
composeCompiler {
    targetKotlinPlatforms.set(listOf(KotlinPlatformType.js))
}
```

The Compose compiler runs only against the JS target. `commonMain`,
`jvmMain`, and the tests on those source sets compile against
plain Kotlin. The Compose runtime is a `jsMain` dependency, never a
common one. The dependency boundary rules in
[ARCHITECTURE.md](../ARCHITECTURE.md#dependency-boundary-rules)
enforce the same property structurally: nothing on `commonMain`
declares Compose, and the Compose plugin's compiler refuses to
process those source sets.

## What was deliberately left out

- **Persistent storage.** The repository is in-memory and
  process-local. The contract was kept platform-free in
  `commonMain` so a Redis or Postgres implementation is a
  one-class change, but the change is out of scope for this
  submission.
- **A full HTML5 DSL.** The engine ships exactly the elements the
  views use. Adding new elements is a one-line change to `Html.kt`.
- **Client-to-server move sync once Compose has hydrated.** The
  server-authoritative path is the no-JS form path. After
  hydration the Compose client plays locally against the embedded
  initial state. A "POST move and update local state" loop would
  duplicate the round-trip without strengthening the invariant the
  no-JS path already proves.
- **Authentication and authorization.** Game ids are unguessable
  (10 random characters from `[a-z0-9]`, 36^10 ~= 3.7e15) but they
  are not capabilities. Anyone with a link to a game can play in
  it. A real deployment would gate writes behind a session.
- **A persistent move log.** Game state is the current board, not
  the move history. The codec roundtrip preserves the current
  state exactly; replaying a game from start is not supported. A
  move log would be a small extension to `GameState` and the codec.

# Story: how the server stopped trusting the form

A short note about a refactor that started as a security cleanup and
ended up changing how I think about SSR. The setting is a Connect
Four built as a Spring server-rendered app, but the lesson applies
to anything that round-trips state through a form.

## The setup

The first version was the textbook stateless SSR design.

```
[server]                                  [browser, no JS]
  GameState                                 cookie?  no
  ConnectFourEngine.move                    session? no
  HtmlRenderer                              hidden state input on the form
```

The page rendered a board plus a `<form>` whose submit posted the
column the player clicked. The current `GameState` was encoded into
a `<input type="hidden" name="state">` so the browser sent it back
on the next request:

```html
<form action="/move" method="post">
  <input type="hidden" name="state" value="v1|6|7|4|ONE|O|.....|" />
  <button name="col" value="0">drop in column 1</button>
  <button name="col" value="1">drop in column 2</button>
  ...
</form>
```

The controller looked roughly like this:

```kotlin
@PostMapping("/move")
fun applyMove(
    @RequestParam("state") encoded: String,
    @RequestParam("col") col: Int,
): ResponseEntity<String> {
    val state = StateCodec.decode(encoded)
        ?: return badRequest()
    return when (val r = ConnectFourEngine.move(state, col)) {
        is MoveResult.Success  -> renderPage(r.newState)
        is MoveResult.Rejected -> renderPage(state, error = r.reason)
    }
}
```

It worked. The board rendered. Wins were detected. Drops fell. With
JavaScript off, the page played fine through pure form submissions.
There was nothing on the server but the renderer; even the move
endpoint was effectively a pure function from `(state, col)` to a
response.

## The thing I noticed

Mid-way through writing the controller test suite I added a test
for "tampering": what happens if the player edits the hidden state
input before submitting?

```kotlin
@Test
fun tampered_state_is_validated_by_the_codec() {
    val tampered = "v1|6|7|4|TWO|O|" + "1".repeat(42) + "|"  // P1 has filled the board
    mvc.post("/move") {
        param("state", tampered)
        param("col", "0")
    }.andExpect {
        status().isOk
        // ... what should happen here?
    }
}
```

I expected the codec to reject it. The codec **did** roundtrip-check
the payload (it validates cell counts, checks the version, refuses
unknown statuses), but the tampered string above is structurally
valid. Forty-two `1`s is a legal cell layout. The status `O` says
the game is ongoing. The codec sees a perfectly normal state where
Player 1 owns every square. The engine sees the same. The next move
gets applied to that state and the controller renders it.

The test passed: the move was applied. That was the bug.

The codec is not the right layer for this check. It can tell you
that a payload parses cleanly. It cannot tell you that the payload
represents a real game in progress. To make that distinction you
need a record of which games actually exist.

This was a design problem, not a coding bug. The architecture had
quietly built in the assumption "the server is whatever the client
posts, plus this move."

## What I tried first

The reflexive fix is to harden the codec. Sign the encoded state
with an HMAC. Embed a server-only secret. On submit, verify the
signature; reject mismatches.

This works. It is also the wrong shape of fix. It adds a secret
the server now has to manage, a key rotation story, a way for a
leaked secret to forge any state, and code on every request that
exists only to detect tampering. Worse, it does not change the
underlying design: the server is still trusting a client-supplied
representation of its own state. The HMAC is just an attendance
check.

The right shape of fix is structural: don't put state on the form
at all. The server already has the state in memory while it
renders the page; the browser never needs to know it.

## The refactor

The new design uses an opaque, server-issued identifier and an
explicit repository.

```
[server]                                  [browser, no JS]
  InMemoryGameRepository                    cookie?  no
    GameId -> GameState                     session? no
  ConnectFourEngine.move                    no state on the form
  HtmlRenderer                              just the column the player clicked
```

The form action carries the id; the only field the form sends is
the column.

```html
<form action="/games/m9k3qr1xs7/move" method="post">
  <button name="col" value="0">drop in column 1</button>
  <button name="col" value="1">drop in column 2</button>
  ...
</form>
```

The controller is now keyed by the id:

```kotlin
@PostMapping("/games/{id}/move")
fun applyMove(
    @PathVariable("id") id: String,
    @RequestParam("col") col: Int,
): ResponseEntity<*> {
    val gameId = GameId.from(id) ?: return notFound(id)
    return when (val outcome = gameService.applyMove(gameId, col)) {
        MoveOutcome.GameNotFound  -> notFound(id)
        is MoveOutcome.Applied    -> seeOther("/games/${gameId.value}")
        is MoveOutcome.Rejected   -> renderPage(outcome.unchanged, outcome.reason)
    }
}
```

The repository owns the canonical state. `GameService.applyMove`
runs the engine inside an atomic `compute` on the repository entry,
so even concurrent submits against the same id serialize without
losing writes:

```kotlin
fun applyMove(id: GameId, col: Int): MoveOutcome {
    var rejection: MoveResult.Rejected? = null
    var stateBeforeMove: GameState? = null
    val outcome = repository.update(id) { current ->
        stateBeforeMove = current
        when (val r = ConnectFourEngine.move(current, col)) {
            is MoveResult.Success  -> r.newState
            is MoveResult.Rejected -> { rejection = r; current }
        }
    }
    return when (outcome) {
        UpdateOutcome.NotFound      -> MoveOutcome.GameNotFound
        is UpdateOutcome.Updated    -> rejection?.let {
            MoveOutcome.Rejected(stateBeforeMove!!, it.reason)
        } ?: MoveOutcome.Applied(outcome.newState)
    }
}
```

The id is unguessable enough that the URL itself is the access
control: 10 random characters from `[a-z0-9]` is 36^10 ~= 3.7e15
strings. Two random ids collide with probability around 1e-9 even
after a billion games; the repository retries up to eight times on
the off chance.

The tampering test became a non-issue because there was nothing left
to tamper with on the form. The new test asserts the absence:

```kotlin
@Test
fun board_form_does_not_carry_state() {
    val body = mvc.get("/games/$id").andReturn().response.contentAsString
    assertThat(body).doesNotContain("""name="state"""")
}
```

## What I learned

The interesting thing is that this is not a security lesson. It is
a design lesson the security problem made visible.

When a server accepts a client-supplied representation of its own
state, the server has to audit that representation forever. Every
time the state shape changes, every time a new field is added,
every time a status is introduced, the audit code has to keep up.
The HMAC fix does not change this; it only narrows the audit to
"was this server the one that issued the payload." The audit code
still has to assume the worst about what the payload claims.

When the server holds the state itself and hands the client an
opaque handle, the audit problem evaporates. The handle is
meaningless without the server's table of state. A tampered
handle is either valid (and refers to a game the player is
allowed to play) or invalid (and returns 404). There is no third
option. The server's state changes do not require the client's
representation to keep up.

The same shape applies to most "stateless" web designs that smuggle
state through the request. Hidden form inputs, signed cookies,
JWTs that contain anything richer than identity claims, encoded
session blobs. They all work; they all create an audit obligation
that grows with the state's surface area. An opaque handle plus
a server table is almost always the cleaner shape.

Writing a no-JS path forces the question to the surface, because
there is nowhere else for the state to go. With JavaScript on, you
can hide the round-trip behind an XHR and never notice. The form
is what made me look.

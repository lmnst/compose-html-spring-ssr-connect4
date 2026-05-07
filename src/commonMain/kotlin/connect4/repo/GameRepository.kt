package connect4.repo

import connect4.game.GameState

/**
 * Server-side store of game records keyed by [GameId]. The repository
 * owns the canonical state of every active game, the engine consults
 * the repository before applying moves, and the controller renders
 * whatever the repository currently holds.
 *
 * Implementations must be thread-safe: a single game may receive
 * concurrent requests from the same browser (refresh + form submit)
 * or from multiple tabs.
 *
 * The interface lives in `commonMain` to keep the contract
 * dependency-free, even though the concrete implementation lives in
 * `jvmMain`.
 */
interface GameRepository {

    /** Insert a fresh [state] under a new id and return the id. */
    fun create(state: GameState): GameId

    /** Returns the current state for [id], or null if no such game. */
    fun get(id: GameId): GameState?

    /**
     * Atomically replace the state for [id] with the result of [block].
     *
     * Returns:
     *  - [UpdateOutcome.Updated] when the game existed and [block] returned a
     *    new state. The returned state is the post-update value.
     *  - [UpdateOutcome.NotFound] when there is no game for [id]. [block] is
     *    not invoked.
     *
     * Implementations apply [block] under whatever locking is needed
     * to make read-modify-write atomic with respect to other callers
     * of [update] for the same id.
     */
    fun update(id: GameId, block: (GameState) -> GameState): UpdateOutcome
}

sealed interface UpdateOutcome {
    data class Updated(val newState: GameState) : UpdateOutcome
    data object NotFound : UpdateOutcome
}

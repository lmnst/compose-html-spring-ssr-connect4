package connect4.server

import connect4.game.ConnectFourEngine
import connect4.game.GameConfig
import connect4.game.GameState
import connect4.game.MoveResult
import connect4.repo.GameId
import connect4.repo.GameRepository
import connect4.repo.UpdateOutcome
import org.springframework.stereotype.Service

/**
 * Application-layer orchestration around the pure
 * [ConnectFourEngine] and the [GameRepository]. Holds no state of its
 * own. The controller asks the service to create and mutate games and
 * the service is responsible for keeping the repository consistent.
 */
@Service
class GameService(
    private val repository: GameRepository,
) {

    /** Create a new game for [config] and return its id and initial state. */
    fun create(config: GameConfig): CreatedGame {
        val state = ConnectFourEngine.newGame(config)
        val id = repository.create(state)
        return CreatedGame(id, state)
    }

    /** Returns the current state for [id], or null if no such game. */
    fun load(id: GameId): GameState? = repository.get(id)

    /**
     * Apply [col] to the game [id] under the repository's per-id lock.
     * The engine sees the canonical state from the repository, never a
     * client-supplied one, so callers cannot tamper with hidden form
     * fields to fast-forward or resurrect a finished game.
     */
    fun applyMove(id: GameId, col: Int): MoveOutcome {
        var rejection: MoveResult.Rejected? = null
        var stateBeforeMove: GameState? = null
        val outcome = repository.update(id) { current ->
            stateBeforeMove = current
            when (val result = ConnectFourEngine.move(current, col)) {
                is MoveResult.Success -> result.newState
                is MoveResult.Rejected -> {
                    rejection = result
                    current
                }
            }
        }
        return when (outcome) {
            UpdateOutcome.NotFound -> MoveOutcome.GameNotFound
            is UpdateOutcome.Updated -> rejection?.let {
                MoveOutcome.Rejected(stateBeforeMove!!, it.reason)
            } ?: MoveOutcome.Applied(outcome.newState)
        }
    }

    data class CreatedGame(val id: GameId, val state: GameState)
}

/**
 * Result of a server-authoritative move attempt. Modeled as a sealed
 * type so the controller cannot forget to render the rejected case.
 */
sealed interface MoveOutcome {
    data class Applied(val newState: GameState) : MoveOutcome
    data class Rejected(val unchanged: GameState, val reason: String) : MoveOutcome
    data object GameNotFound : MoveOutcome
}

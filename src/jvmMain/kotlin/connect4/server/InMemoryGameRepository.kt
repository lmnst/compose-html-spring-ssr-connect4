package connect4.server

import connect4.game.GameState
import connect4.repo.GameId
import connect4.repo.GameRepository
import connect4.repo.UpdateOutcome
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-local game registry backed by [ConcurrentHashMap]. Each
 * mutation goes through `compute`, so read-modify-write is atomic with
 * respect to other concurrent writers for the same id.
 *
 * Records live until the JVM stops; restarting the server clears the
 * registry. This is intentional for a single-process demo: a real
 * deployment would swap the implementation for one backed by Redis,
 * Postgres, or similar without changing the [GameRepository] contract.
 */
@Component
class InMemoryGameRepository(
    private val idGenerator: GameIdGenerator = SecureRandomGameIdGenerator(),
) : GameRepository {

    private val store: ConcurrentHashMap<GameId, GameState> = ConcurrentHashMap()

    override fun create(state: GameState): GameId {
        // Retry on the astronomically unlikely chance of a collision.
        repeat(MAX_ID_GENERATION_ATTEMPTS) {
            val id = idGenerator.next()
            if (store.putIfAbsent(id, state) == null) return id
        }
        error("could not allocate a unique game id after $MAX_ID_GENERATION_ATTEMPTS attempts")
    }

    override fun get(id: GameId): GameState? = store[id]

    override fun update(id: GameId, block: (GameState) -> GameState): UpdateOutcome {
        var outcome: UpdateOutcome = UpdateOutcome.NotFound
        store.compute(id) { _, existing ->
            if (existing == null) {
                outcome = UpdateOutcome.NotFound
                null
            } else {
                val next = block(existing)
                outcome = UpdateOutcome.Updated(next)
                next
            }
        }
        return outcome
    }

    /** Exposed for tests. */
    internal fun size(): Int = store.size

    private companion object {
        const val MAX_ID_GENERATION_ATTEMPTS = 8
    }
}

/** Strategy for producing fresh, URL-safe game ids. */
fun interface GameIdGenerator {
    fun next(): GameId
}

class SecureRandomGameIdGenerator(
    private val length: Int = DEFAULT_LENGTH,
    private val random: SecureRandom = SecureRandom(),
) : GameIdGenerator {
    init {
        require(length in GameId.MIN_LENGTH..GameId.MAX_LENGTH) {
            "id length $length outside [${GameId.MIN_LENGTH}, ${GameId.MAX_LENGTH}]"
        }
    }

    override fun next(): GameId {
        val charset = GameId.CHARSET
        val sb = StringBuilder(length)
        repeat(length) { sb.append(charset[random.nextInt(charset.length)]) }
        return GameId(sb.toString())
    }

    companion object {
        const val DEFAULT_LENGTH = 10
    }
}

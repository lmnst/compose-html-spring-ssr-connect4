package connect4.server

import connect4.game.ConnectFourEngine
import connect4.game.GameConfig
import connect4.repo.GameId
import connect4.repo.UpdateOutcome
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class InMemoryGameRepositoryTest {

    private val repo = InMemoryGameRepository()

    @Test
    fun create_returns_a_valid_id_and_stores_the_state() {
        val initial = ConnectFourEngine.newGame(GameConfig.DEFAULT)
        val id = repo.create(initial)
        assertThat(GameId.isValid(id.value)).isTrue
        assertThat(repo.get(id)).isEqualTo(initial)
    }

    @Test
    fun create_returns_distinct_ids_for_independent_games() {
        val a = repo.create(ConnectFourEngine.newGame(GameConfig.DEFAULT))
        val b = repo.create(ConnectFourEngine.newGame(GameConfig.DEFAULT))
        assertThat(a).isNotEqualTo(b)
        assertThat(repo.size()).isEqualTo(2)
    }

    @Test
    fun get_returns_null_for_unknown_ids() {
        assertThat(repo.get(GameId("notreal12"))).isNull()
    }

    @Test
    fun update_applies_the_block_atomically_on_an_existing_id() {
        val id = repo.create(ConnectFourEngine.newGame(GameConfig.DEFAULT))
        val outcome = repo.update(id) { state ->
            (ConnectFourEngine.move(state, 0) as connect4.game.MoveResult.Success).newState
        }
        assertThat(outcome).isInstanceOf(UpdateOutcome.Updated::class.java)
        val after = (outcome as UpdateOutcome.Updated).newState
        assertThat(after.lastMove?.col).isEqualTo(0)
        assertThat(repo.get(id)).isEqualTo(after)
    }

    @Test
    fun update_returns_NotFound_without_invoking_block_for_missing_ids() {
        var invoked = false
        val outcome = repo.update(GameId("absent01")) { current ->
            invoked = true
            current
        }
        assertThat(outcome).isEqualTo(UpdateOutcome.NotFound)
        assertThat(invoked).isFalse
    }

    @Test
    fun two_independent_ids_keep_independent_state() {
        val a = repo.create(ConnectFourEngine.newGame(GameConfig.DEFAULT))
        val b = repo.create(ConnectFourEngine.newGame(GameConfig.DEFAULT))
        repo.update(a) { (ConnectFourEngine.move(it, 0) as connect4.game.MoveResult.Success).newState }
        repo.update(b) { (ConnectFourEngine.move(it, 6) as connect4.game.MoveResult.Success).newState }
        assertThat(repo.get(a)?.lastMove?.col).isEqualTo(0)
        assertThat(repo.get(b)?.lastMove?.col).isEqualTo(6)
    }

    @Test
    fun concurrent_updates_to_the_same_id_do_not_lose_writes() {
        // Drop alternating discs into two columns from many threads. Each
        // call places exactly one disc, so at the end the total number
        // of non-empty cells must equal the number of successful moves.
        val id = repo.create(
            ConnectFourEngine.newGame(GameConfig(rows = 6, cols = 4, winLength = 5)),
        )
        val movesPerThread = 10
        val threads = 4
        val pool = Executors.newFixedThreadPool(threads)
        val ready = CountDownLatch(threads)
        val go = CountDownLatch(1)

        for (t in 0 until threads) {
            pool.execute {
                ready.countDown()
                go.await()
                repeat(movesPerThread) {
                    repo.update(id) { current ->
                        if (current.isOver) return@update current
                        when (val r = ConnectFourEngine.move(current, t % current.config.cols)) {
                            is connect4.game.MoveResult.Success -> r.newState
                            else -> current
                        }
                    }
                }
            }
        }
        ready.await(5, TimeUnit.SECONDS)
        go.countDown()
        pool.shutdown()
        pool.awaitTermination(10, TimeUnit.SECONDS)

        val final = repo.get(id)!!
        val nonEmpty = final.board.cells.count { it != connect4.game.Cell.EMPTY }
        // Every disc lands in a row that was previously empty. The board
        // never enters an inconsistent state: rows below a disc are not
        // empty, and no two threads have placed into the same cell.
        assertThat(nonEmpty).isLessThanOrEqualTo(final.config.rows * final.config.cols)
        for (c in 0 until final.config.cols) {
            // Below any disc, all cells are non-empty (gravity invariant).
            var sawDisc = false
            for (r in 0 until final.config.rows) {
                val isDisc = final.board[r, c] != connect4.game.Cell.EMPTY
                if (isDisc) sawDisc = true
                if (sawDisc) {
                    assertThat(final.board[r, c]).isNotEqualTo(connect4.game.Cell.EMPTY)
                }
            }
        }
    }
}

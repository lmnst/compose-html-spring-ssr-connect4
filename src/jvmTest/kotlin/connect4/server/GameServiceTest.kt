package connect4.server

import connect4.game.ConnectFourEngine
import connect4.game.GameConfig
import connect4.game.GameStatus
import connect4.game.MoveResult
import connect4.repo.GameId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GameServiceTest {

    private val repo = InMemoryGameRepository()
    private val service = GameService(repo)

    @Test
    fun create_persists_a_new_game_and_returns_its_id_and_state() {
        val created = service.create(GameConfig.DEFAULT)
        assertThat(created.state.config).isEqualTo(GameConfig.DEFAULT)
        assertThat(repo.get(created.id)).isEqualTo(created.state)
    }

    @Test
    fun load_returns_the_stored_state() {
        val created = service.create(GameConfig.DEFAULT)
        assertThat(service.load(created.id)).isEqualTo(created.state)
    }

    @Test
    fun applyMove_updates_state_and_alternates_player_for_a_legal_move() {
        val created = service.create(GameConfig.DEFAULT)
        val outcome = service.applyMove(created.id, col = 3)
        assertThat(outcome).isInstanceOf(MoveOutcome.Applied::class.java)
        val applied = outcome as MoveOutcome.Applied
        assertThat(applied.newState.currentPlayer.name).isEqualTo("TWO")
        assertThat(service.load(created.id)).isEqualTo(applied.newState)
    }

    @Test
    fun applyMove_rejection_leaves_state_unchanged() {
        // Build a 4x4 game and fill column 0 in the repository directly.
        var state = ConnectFourEngine.newGame(GameConfig(4, 4, 4))
        repeat(4) { state = (ConnectFourEngine.move(state, 0) as MoveResult.Success).newState }
        val id = repo.create(state)
        val before = service.load(id)
        val outcome = service.applyMove(id, col = 0)
        assertThat(outcome).isInstanceOf(MoveOutcome.Rejected::class.java)
        val rej = outcome as MoveOutcome.Rejected
        assertThat(rej.reason).isEqualTo("That column is full")
        assertThat(rej.unchanged).isEqualTo(before)
        assertThat(service.load(id)).isEqualTo(before)
    }

    @Test
    fun applyMove_rejection_after_game_over_keeps_won_status() {
        // Player 1 wins along the bottom row of the default board.
        val created = service.create(GameConfig.DEFAULT)
        for (col in intArrayOf(0, 6, 1, 6, 2, 6, 3)) {
            service.applyMove(created.id, col)
        }
        val won = service.load(created.id)!!
        assertThat(won.status).isInstanceOf(GameStatus.Won::class.java)

        val outcome = service.applyMove(created.id, col = 5)
        assertThat(outcome).isInstanceOf(MoveOutcome.Rejected::class.java)
        assertThat((outcome as MoveOutcome.Rejected).reason).isEqualTo("The game is already over")
        assertThat(service.load(created.id)).isEqualTo(won)
    }

    @Test
    fun applyMove_returns_GameNotFound_for_unknown_id() {
        val outcome = service.applyMove(GameId("missing01"), col = 0)
        assertThat(outcome).isEqualTo(MoveOutcome.GameNotFound)
    }
}

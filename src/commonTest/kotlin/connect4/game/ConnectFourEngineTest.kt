package connect4.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class ConnectFourEngineTest {

    private fun newGame(rows: Int = 6, cols: Int = 7, winLength: Int = 4): GameState =
        ConnectFourEngine.newGame(GameConfig(rows, cols, winLength))

    private fun GameState.play(vararg cols: Int): GameState =
        cols.fold(this) { acc, col ->
            val r = ConnectFourEngine.move(acc, col)
            assertIs<MoveResult.Success>(r, "expected move into $col to succeed, got $r")
            r.newState
        }

    @Test
    fun horizontal_win_for_player_one() {
        // P1 plays cols 0..3 along the bottom row, P2 plays col 6 in between.
        val state = newGame().play(0, 6, 1, 6, 2, 6, 3)
        val status = state.status
        assertIs<GameStatus.Won>(status)
        assertEquals(Player.ONE, status.winner)
        assertEquals(4, status.line.size)
        assertTrue(status.line.all { it.row == state.board.rows - 1 })
    }

    @Test
    fun vertical_win_for_player_two() {
        val state = newGame().play(0, 3, 0, 3, 0, 3, 1, 3)
        val status = state.status
        assertIs<GameStatus.Won>(status)
        assertEquals(Player.TWO, status.winner)
        assertEquals(4, status.line.size)
        assertTrue(status.line.all { it.col == 3 })
    }

    @Test
    fun diagonal_down_right_win() {
        // Pre-position 3 P1 discs on the down-right diagonal (2,0),(3,1),(4,2)
        // and drop the 4th into column 3, landing at (5,3).
        val board = buildBoardFromAscii(
            """
            .......
            .......
            1......
            .1.....
            ..1....
            .......
            """.trimIndent(),
        )
        val state = GameState(
            config = GameConfig(6, 7, 4),
            board = board,
            currentPlayer = Player.ONE,
            status = GameStatus.Ongoing,
        )
        val result = ConnectFourEngine.move(state, 3)
        assertIs<MoveResult.Success>(result)
        val status = result.newState.status
        assertIs<GameStatus.Won>(status)
        assertEquals(Player.ONE, status.winner)
        val coords = status.line.map { it.row to it.col }.toSet()
        assertEquals(setOf(2 to 0, 3 to 1, 4 to 2, 5 to 3), coords)
    }

    @Test
    fun diagonal_up_right_win() {
        // Pre-position 3 P1 discs on the up-right diagonal at (5,0),(4,1),(3,2)
        // and drop the 4th into column 3. Column 3 has its bottom three rows pre-
        // filled (with filler P2 discs) so gravity lands the new piece on (2,3).
        val board = buildBoardFromAscii(
            """
            .......
            .......
            .......
            ..12...
            .1.2...
            1..2...
            """.trimIndent(),
        )
        val state = GameState(
            config = GameConfig(6, 7, 4),
            board = board,
            currentPlayer = Player.ONE,
            status = GameStatus.Ongoing,
        )
        val result = ConnectFourEngine.move(state, 3)
        assertIs<MoveResult.Success>(result)
        val status = result.newState.status
        assertIs<GameStatus.Won>(status)
        assertEquals(Player.ONE, status.winner)
        val coords = status.line.map { it.row to it.col }.toSet()
        assertEquals(setOf(5 to 0, 4 to 1, 3 to 2, 2 to 3), coords)
    }

    @Test
    fun draw_when_board_fills_without_win() {
        // 4x5 board, winLength 5. Vertical and diagonal wins are impossible (max line
        // length 4). The interleaved fill plan also avoids any 5-in-a-row horizontally.
        var s = newGame(rows = 4, cols = 5, winLength = 5)
        val plan = intArrayOf(
            0, 1, 0, 1,
            2, 3, 2, 3,
            1, 0, 1, 0,
            3, 2, 3, 2,
            4, 4, 4, 4,
        )
        for (c in plan) {
            val r = ConnectFourEngine.move(s, c)
            assertIs<MoveResult.Success>(r, "move into $c failed: $r")
            s = r.newState
        }
        assertTrue(s.board.isFull())
        assertSame(GameStatus.Draw, s.status)
    }

    @Test
    fun column_full_is_rejected_and_player_unchanged() {
        // 4 alternating drops fill column 0 without forming a vertical 4-in-a-row.
        var s = newGame(rows = 4, cols = 4, winLength = 4)
        repeat(4) {
            val r = ConnectFourEngine.move(s, 0)
            assertIs<MoveResult.Success>(r)
            s = r.newState
        }
        assertSame(GameStatus.Ongoing, s.status)
        val before = s.currentPlayer
        val rejected = ConnectFourEngine.move(s, 0)
        assertSame(MoveResult.ColumnFull, rejected)
        assertEquals(before, s.currentPlayer)
    }

    @Test
    fun invalid_column_is_rejected() {
        val s = newGame()
        assertIs<MoveResult.InvalidColumn>(ConnectFourEngine.move(s, -1))
        assertIs<MoveResult.InvalidColumn>(ConnectFourEngine.move(s, s.config.cols))
    }

    @Test
    fun configurable_win_length_connect_5() {
        val s = newGame(rows = 6, cols = 9, winLength = 5)
            .play(0, 8, 1, 8, 2, 8, 3, 8, 4)
        val status = s.status
        assertIs<GameStatus.Won>(status)
        assertEquals(Player.ONE, status.winner)
        assertEquals(5, status.line.size)
    }

    @Test
    fun connect_5_does_not_trigger_with_only_4() {
        val s = newGame(rows = 6, cols = 9, winLength = 5)
            .play(0, 8, 1, 8, 2, 8, 3)
        assertSame(GameStatus.Ongoing, s.status, "4-in-a-row must not win at winLength=5")
    }

    @Test
    fun invalid_config_is_rejected() {
        assertFails { GameConfig(rows = 0, cols = 7, winLength = 4) }
        assertFails { GameConfig(rows = 6, cols = -1, winLength = 4) }
        assertFails { GameConfig(rows = 6, cols = 7, winLength = 0) }
        // winLength larger than both dims is impossible to satisfy.
        assertFails { GameConfig(rows = 4, cols = 4, winLength = 5) }

        // validate() agrees and yields UI-friendly messages:
        assertNotNull(GameConfig.validate(rows = 2, cols = 7, winLength = 4))
        assertNotNull(GameConfig.validate(rows = 7, cols = 7, winLength = 11))
        assertNotNull(GameConfig.validate(rows = 4, cols = 4, winLength = 5))
        assertNull(GameConfig.validate(rows = 6, cols = 7, winLength = 4))
        assertNull(GameConfig.validate(rows = 10, cols = 10, winLength = 5))
    }

    @Test
    fun moves_after_game_over_are_rejected() {
        val won = newGame().play(0, 6, 1, 6, 2, 6, 3)
        assertIs<GameStatus.Won>(won.status)
        assertSame(MoveResult.GameAlreadyOver, ConnectFourEngine.move(won, 0))
        assertSame(MoveResult.GameAlreadyOver, ConnectFourEngine.move(won, 5))
    }

    @Test
    fun gravity_drops_to_lowest_empty_row() {
        var s = newGame()
        val r1 = ConnectFourEngine.move(s, 3)
        assertIs<MoveResult.Success>(r1)
        assertEquals(s.board.rows - 1, r1.newState.lastMove?.row)
        s = r1.newState
        val r2 = ConnectFourEngine.move(s, 3)
        assertIs<MoveResult.Success>(r2)
        assertEquals(s.board.rows - 2, r2.newState.lastMove?.row)
    }

    @Test
    fun new_game_starts_with_player_one_and_empty_board() {
        val s = newGame(rows = 10, cols = 10, winLength = 5)
        assertEquals(Player.ONE, s.currentPlayer)
        assertSame(GameStatus.Ongoing, s.status)
        assertTrue(s.board.cells.all { it == Cell.EMPTY })
        assertNull(s.lastMove)
    }

    /** ASCII board: '.' empty, '1' P1, '2' P2. Row 0 is the topmost line. */
    private fun buildBoardFromAscii(diagram: String): Board {
        val rows = diagram.lines().filter { it.isNotEmpty() }
        val height = rows.size
        val width = rows[0].length
        val cells = ArrayList<Cell>(height * width)
        for ((r, line) in rows.withIndex()) {
            if (line.length != width) fail("row $r has width ${line.length}, expected $width")
            for (ch in line) {
                cells += when (ch) {
                    '.' -> Cell.EMPTY
                    '1' -> Cell.P1
                    '2' -> Cell.P2
                    else -> fail("unexpected board char '$ch'")
                }
            }
        }
        return Board(height, width, cells)
    }
}

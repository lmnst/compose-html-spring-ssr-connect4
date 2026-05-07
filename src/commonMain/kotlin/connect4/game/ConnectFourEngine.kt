package connect4.game

/**
 * Pure-Kotlin Connect-N engine. No browser, Compose, Spring, or storage
 * dependencies, the engine is fully unit-testable on the JVM.
 *
 * Conventions:
 *  - Row 0 is the top of the board, row `rows - 1` is the bottom.
 *  - "Gravity" places a piece in the lowest empty row of the chosen column.
 *  - State is updated immutably; each successful move returns a new [GameState].
 */
object ConnectFourEngine {

    fun newGame(config: GameConfig): GameState = GameState(
        config = config,
        board = Board.empty(config.rows, config.cols),
        currentPlayer = Player.ONE,
        status = GameStatus.Ongoing,
        lastMove = null,
    )

    /**
     * Apply a move in [col] for the current player. Returns:
     *  - [MoveResult.Success] with the next state, or
     *  - a [MoveResult.Rejected] subtype if the move is illegal.
     *
     * Rejected moves never change the current player.
     */
    fun move(state: GameState, col: Int): MoveResult {
        if (state.isOver) return MoveResult.GameAlreadyOver
        if (col !in 0 until state.config.cols) return MoveResult.InvalidColumn(col)
        val row = state.board.lowestEmptyRow(col) ?: return MoveResult.ColumnFull

        val cell = Cell.of(state.currentPlayer)
        val newBoard = state.board.with(row, col, cell)

        val winLine = findWinLine(newBoard, row, col, state.currentPlayer, state.config.winLength)
        val newStatus: GameStatus = when {
            winLine != null -> GameStatus.Won(state.currentPlayer, winLine)
            newBoard.isFull() -> GameStatus.Draw
            else -> GameStatus.Ongoing
        }
        val nextPlayer = if (newStatus is GameStatus.Ongoing) {
            state.currentPlayer.opponent
        } else {
            state.currentPlayer
        }

        return MoveResult.Success(
            state.copy(
                board = newBoard,
                currentPlayer = nextPlayer,
                status = newStatus,
                lastMove = Position(row, col),
            ),
        )
    }

    /**
     * Looks for a winning line of `winLength` containing the just-placed piece at
     * (row, col). Checks the four axes:
     *   horizontal, vertical, diagonal down-right, diagonal up-right.
     * Returns the winning segment (length == winLength) or null.
     */
    private fun findWinLine(
        board: Board,
        row: Int,
        col: Int,
        player: Player,
        winLength: Int,
    ): List<Position>? {
        val target = Cell.of(player)
        val axes = listOf(
            0 to 1,    // horizontal
            1 to 0,    // vertical
            1 to 1,    // diagonal down-right
            -1 to 1,   // diagonal up-right
        )
        for ((dr, dc) in axes) {
            val line = collectAxisLine(board, row, col, dr, dc, target, winLength)
            if (line != null) return line
        }
        return null
    }

    /**
     * From (row, col), walks both directions of the (dr, dc) axis collecting
     * contiguous cells matching [target]. If the resulting run has length >=
     * [winLength], returns the first [winLength] cells of that run.
     */
    private fun collectAxisLine(
        board: Board,
        row: Int,
        col: Int,
        dr: Int,
        dc: Int,
        target: Cell,
        winLength: Int,
    ): List<Position>? {
        val line = ArrayDeque<Position>()
        line.addLast(Position(row, col))

        var r = row + dr
        var c = col + dc
        while (r in 0 until board.rows && c in 0 until board.cols && board[r, c] == target) {
            line.addLast(Position(r, c))
            r += dr
            c += dc
        }

        r = row - dr
        c = col - dc
        while (r in 0 until board.rows && c in 0 until board.cols && board[r, c] == target) {
            line.addFirst(Position(r, c))
            r -= dr
            c -= dc
        }

        return if (line.size >= winLength) line.toList().take(winLength) else null
    }
}

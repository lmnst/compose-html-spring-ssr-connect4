package connect4.game

sealed interface GameStatus {
    data object Ongoing : GameStatus
    data object Draw : GameStatus
    data class Won(
        val winner: Player,
        /** Coordinates (row, col) of the winning line, in order. */
        val line: List<Position>,
    ) : GameStatus
}

data class Position(val row: Int, val col: Int)

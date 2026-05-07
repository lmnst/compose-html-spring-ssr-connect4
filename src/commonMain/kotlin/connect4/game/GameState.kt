package connect4.game

data class GameState(
    val config: GameConfig,
    val board: Board,
    val currentPlayer: Player,
    val status: GameStatus,
    /** Last move played, used for animations and persistence. */
    val lastMove: Position? = null,
) {
    val isOver: Boolean get() = status !is GameStatus.Ongoing
}

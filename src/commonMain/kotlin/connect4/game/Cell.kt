package connect4.game

enum class Cell {
    EMPTY, P1, P2;

    val owner: Player?
        get() = when (this) {
            EMPTY -> null
            P1 -> Player.ONE
            P2 -> Player.TWO
        }

    companion object {
        fun of(player: Player): Cell = if (player == Player.ONE) P1 else P2
    }
}

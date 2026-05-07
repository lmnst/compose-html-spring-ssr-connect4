package connect4.game

enum class Player {
    ONE, TWO;

    val opponent: Player
        get() = if (this == ONE) TWO else ONE
}

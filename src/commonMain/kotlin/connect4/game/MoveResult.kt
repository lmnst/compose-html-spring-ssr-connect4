package connect4.game

sealed interface MoveResult {
    data class Success(val newState: GameState) : MoveResult

    sealed interface Rejected : MoveResult {
        val reason: String
    }

    data object ColumnFull : Rejected {
        override val reason: String get() = "That column is full"
    }

    data object GameAlreadyOver : Rejected {
        override val reason: String get() = "The game is already over"
    }

    data class InvalidColumn(val col: Int) : Rejected {
        override val reason: String get() = "Column $col is out of range"
    }
}

package connect4.game

/**
 * Board dimensions and the win-length required to score a line.
 *
 * Hard invariants are enforced in [init]; the looser [validate] returns a human
 * readable message and is intended for UI input checking before construction.
 */
data class GameConfig(
    val rows: Int,
    val cols: Int,
    val winLength: Int,
) {
    init {
        require(rows > 0) { "rows must be positive (was $rows)" }
        require(cols > 0) { "cols must be positive (was $cols)" }
        require(winLength > 0) { "winLength must be positive (was $winLength)" }
        require(winLength <= rows || winLength <= cols) {
            "winLength ($winLength) must fit within at least one board dimension (${rows}x$cols)"
        }
    }

    companion object {
        const val MIN_DIMENSION = 4
        const val MAX_DIMENSION = 30
        const val MIN_WIN_LENGTH = 3
        const val MAX_WIN_LENGTH = 10

        val DEFAULT: GameConfig = GameConfig(rows = 6, cols = 7, winLength = 4)

        /**
         * Returns null if the inputs form a sensible UI configuration, otherwise a
         * user-facing error message. Stricter than [GameConfig.init], used to
         * clamp UI input to reasonable ranges before constructing a [GameConfig].
         */
        fun validate(rows: Int, cols: Int, winLength: Int): String? {
            if (rows !in MIN_DIMENSION..MAX_DIMENSION) {
                return "Rows must be between $MIN_DIMENSION and $MAX_DIMENSION"
            }
            if (cols !in MIN_DIMENSION..MAX_DIMENSION) {
                return "Columns must be between $MIN_DIMENSION and $MAX_DIMENSION"
            }
            if (winLength !in MIN_WIN_LENGTH..MAX_WIN_LENGTH) {
                return "Win length must be between $MIN_WIN_LENGTH and $MAX_WIN_LENGTH"
            }
            if (winLength > rows && winLength > cols) {
                return "Win length cannot exceed both board dimensions"
            }
            return null
        }
    }
}

package connect4.state

import connect4.game.Board
import connect4.game.Cell
import connect4.game.GameConfig
import connect4.game.GameState
import connect4.game.GameStatus
import connect4.game.Player
import connect4.game.Position

/**
 * Versioned, dependency-free codec for [GameState]. The format is a single
 * line of pipe-separated fields:
 *
 * `v1|rows|cols|win|currentPlayer|status|cells|lastMove`
 *
 * - `cells`     : flat row-major string, '.' empty, '1' P1, '2' P2.
 * - `status`    : `O`, `D`, or `W:WINNER:r.c,r.c,...`.
 * - `lastMove`  : empty or `r.c`.
 *
 * The codec is pure-Kotlin and has no platform dependencies, so the same
 * encoded payload is produced by the server and consumed by the client.
 */
object StateCodec {

    private const val VERSION = "v1"

    fun encode(state: GameState): String {
        val cells = buildString(state.board.cells.size) {
            for (cell in state.board.cells) {
                append(
                    when (cell) {
                        Cell.EMPTY -> '.'
                        Cell.P1 -> '1'
                        Cell.P2 -> '2'
                    },
                )
            }
        }
        val statusStr = when (val s = state.status) {
            GameStatus.Ongoing -> "O"
            GameStatus.Draw -> "D"
            is GameStatus.Won -> buildString {
                append("W:").append(s.winner.name).append(':')
                s.line.joinTo(this, ",") { "${it.row}.${it.col}" }
            }
        }
        val lastMove = state.lastMove?.let { "${it.row}.${it.col}" } ?: ""
        return listOf(
            VERSION,
            state.config.rows.toString(),
            state.config.cols.toString(),
            state.config.winLength.toString(),
            state.currentPlayer.name,
            statusStr,
            cells,
            lastMove,
        ).joinToString("|")
    }

    /**
     * Returns null when [raw] is unparseable or carries an unknown version.
     * Callers fall back to a fresh game on null. This codec never throws.
     */
    fun decode(raw: String): GameState? = runCatching { strictDecode(raw) }.getOrNull()

    private fun strictDecode(raw: String): GameState {
        val parts = raw.split("|")
        require(parts.size == 8 && parts[0] == VERSION) { "unrecognized payload" }
        val rows = parts[1].toInt()
        val cols = parts[2].toInt()
        val winLength = parts[3].toInt()
        val config = GameConfig(rows, cols, winLength)
        val player = Player.valueOf(parts[4])
        val status = decodeStatus(parts[5])
        val cellsStr = parts[6]
        require(cellsStr.length == rows * cols) { "cell payload length mismatch" }
        val cells = cellsStr.map { ch ->
            when (ch) {
                '.' -> Cell.EMPTY
                '1' -> Cell.P1
                '2' -> Cell.P2
                else -> error("bad cell char '$ch'")
            }
        }
        val lastMove = parts[7].takeIf { it.isNotEmpty() }?.let { piece ->
            val (r, c) = piece.split(".").map(String::toInt)
            Position(r, c)
        }
        return GameState(config, Board(rows, cols, cells), player, status, lastMove)
    }

    private fun decodeStatus(s: String): GameStatus = when {
        s == "O" -> GameStatus.Ongoing
        s == "D" -> GameStatus.Draw
        s.startsWith("W:") -> {
            val rest = s.removePrefix("W:")
            val sep = rest.indexOf(':')
            require(sep >= 0) { "bad win status" }
            val winner = Player.valueOf(rest.substring(0, sep))
            val lineStr = rest.substring(sep + 1)
            val line = if (lineStr.isEmpty()) emptyList() else lineStr.split(",").map {
                val (r, c) = it.split(".").map(String::toInt)
                Position(r, c)
            }
            GameStatus.Won(winner, line)
        }
        else -> error("bad status '$s'")
    }
}

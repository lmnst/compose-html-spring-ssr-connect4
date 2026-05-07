package connect4.repo

/**
 * Opaque identifier for a server-side game record. URL-safe by
 * construction: only lowercase letters and digits, length-bounded so
 * the id fits in path segments and form actions without escaping.
 *
 * Created via [from] for caller-supplied strings (returns null on
 * invalid input). The repository generates fresh ids itself.
 */
data class GameId(val value: String) {
    init {
        require(isValid(value)) { "invalid game id: '$value'" }
    }

    override fun toString(): String = value

    companion object {
        const val MIN_LENGTH = 4
        const val MAX_LENGTH = 32

        /** Returns null when [raw] does not satisfy the id contract. */
        fun from(raw: String): GameId? = if (isValid(raw)) GameId(raw) else null

        /** True when [raw] is a non-empty, length-bounded URL-safe id. */
        fun isValid(raw: String): Boolean =
            raw.length in MIN_LENGTH..MAX_LENGTH && raw.all { it in CHARSET }

        /** The legal id charset, exposed for id generators. */
        val CHARSET: String = ('a'..'z').joinToString("") + ('0'..'9').joinToString("")
    }
}

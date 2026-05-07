package connect4.repo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GameIdTest {

    @Test
    fun valid_ids_are_accepted() {
        assertNotNull(GameId.from("abcd"))
        assertNotNull(GameId.from("a1b2"))
        assertNotNull(GameId.from("zzz9"))
        assertNotNull(GameId.from("a".repeat(GameId.MAX_LENGTH)))
    }

    @Test
    fun ids_too_short_or_too_long_are_rejected() {
        assertNull(GameId.from(""))
        assertNull(GameId.from("ab"))
        assertNull(GameId.from("abc"))
        assertNull(GameId.from("a".repeat(GameId.MAX_LENGTH + 1)))
    }

    @Test
    fun ids_with_invalid_characters_are_rejected() {
        assertNull(GameId.from("ABCD"))      // uppercase
        assertNull(GameId.from("ab-cd"))     // dash
        assertNull(GameId.from("ab/cd"))     // path separator
        assertNull(GameId.from("ab cd"))     // whitespace
        assertNull(GameId.from("ab.cd"))     // dot
        assertNull(GameId.from("abécd")) // non-ASCII
    }

    @Test
    fun constructor_throws_on_invalid_input() {
        assertFails { GameId("") }
        assertFails { GameId("UPPER") }
        assertFails { GameId("a/b") }
    }

    @Test
    fun toString_returns_the_underlying_string() {
        assertEquals("abcd1234", GameId("abcd1234").toString())
    }

    @Test
    fun equality_is_based_on_value() {
        assertEquals(GameId("abcd1234"), GameId("abcd1234"))
    }
}

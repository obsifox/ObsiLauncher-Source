package studio.obsifox.launcher.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StringsTest {
    @Test
    fun persianHasEveryEnglishKey() {
        val missing = StringTables.en.keys - StringTables.fa.keys
        val extra = StringTables.fa.keys - StringTables.en.keys
        assertTrue(missing.isEmpty(), "missing Persian strings: $missing")
        assertTrue(extra.isEmpty(), "Persian keys without English text: $extra")
    }

    @Test
    fun formatPlaceholdersMatchBetweenLanguages() {
        val re = Regex("%[sd]")
        for ((k, en) in StringTables.en) {
            val fa = StringTables.fa.getValue(k)
            assertEquals(re.findAll(en).map { it.value }.toList(), re.findAll(fa).map { it.value }.toList(), "placeholder mismatch in '$k'")
        }
    }

    @Test
    fun formattingWorks() {
        val fa = Strings(Lang.FA)
        assertEquals("2 ساعت و 5 دقیقه", fa.duration(2 * 3600 + 5 * 60L))
        assertEquals("5 min", Strings(Lang.EN).duration(300))
        assertEquals("missing_key", Strings(Lang.EN)["missing_key"])
    }

    @Test
    fun languageResolution() {
        assertEquals(Lang.FA, Lang.resolve("fa"))
        assertEquals(Lang.EN, Lang.resolve("en"))
    }
}

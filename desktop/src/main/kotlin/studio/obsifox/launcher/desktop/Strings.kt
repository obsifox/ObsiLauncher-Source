package studio.obsifox.launcher.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import studio.obsifox.launcher.core.util.ProgressUpdate
import java.util.Locale

enum class Lang(val code: String, val label: String, val rtl: Boolean) {
    EN("en", "English", false),
    FA("fa", "فارسی", true);

    companion object {
        /** @param setting "auto" | "en" | "fa" */
        fun resolve(setting: String): Lang = when (setting) {
            "en" -> EN
            "fa" -> FA
            else -> if (Locale.getDefault().language == "fa") FA else EN
        }
    }
}

class Strings(val lang: Lang) {
    private val table: Map<String, String> = if (lang == Lang.FA) StringTables.fa else StringTables.en

    operator fun get(key: String): String = table[key] ?: StringTables.en[key] ?: key
    fun fmt(key: String, vararg args: Any?): String = try { String.format(Locale.ROOT, get(key), *args) } catch (_: Exception) { get(key) }

    fun duration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return when {
            h > 0 -> fmt("dur_hm", h, m)
            else -> fmt("dur_m", m)
        }
    }

    /** Human readable text for a progress update coming from the core. */
    fun stage(u: ProgressUpdate): String {
        val counts = if (u.totalFiles > 0) " ${u.doneFiles}/${u.totalFiles}" else ""
        val pct = if (u.totalBytes > 0) " (${(u.doneBytes * 100 / u.totalBytes).toInt()}%)" else ""
        return when {
            u.stage == "libraries" -> get("stage_libraries") + counts
            u.stage == "assets" -> get("stage_assets") + counts + pct
            u.stage == "asset-index" -> get("stage_assets")
            u.stage == "java" -> get("stage_java") + counts
            u.stage == "loader-installer" -> get("stage_loader")
            u.stage == "modpack" -> get("stage_modpack")
            u.stage == "modpack-files" -> get("stage_modpack_files") + counts + pct
            u.stage.startsWith("content:") -> fmt("stage_download", u.stage.removePrefix("content:")) + pct
            u.stage.startsWith("Resolving ") -> get("stage_resolving")
            else -> u.stage
        }
    }
}

val LocalStrings = staticCompositionLocalOf { Strings(Lang.EN) }

@Composable
fun t(key: String): String = LocalStrings.current[key]

@Composable
fun tf(key: String, vararg args: Any?): String = LocalStrings.current.fmt(key, *args)

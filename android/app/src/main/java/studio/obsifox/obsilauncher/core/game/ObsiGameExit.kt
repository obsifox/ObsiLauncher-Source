package studio.obsifox.obsilauncher.core.game

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import studio.obsifox.obsilauncher.core.Paths
import java.io.File

/**
 * Native-called game exit hook (invoked from the vendored exithook / stdio_is
 * "nominal_exit" path when the game JVM's exit() is intercepted).
 *
 * Persists the outcome so the launcher shell — which is re-created after the
 * controlled process restart — can show the "Game Crashed" dialog with the
 * extracted error, exactly like the reference mock (Copy Exit Code / close).
 *
 * GPL-3.0-or-later — ObsiLauncher.
 */
object ObsiGameExit {

    data class Exit(
        val code: Int,
        val isSignal: Boolean,
        val excerpt: String,
        val instanceId: String? = null,
        val versionId: String? = null,
    )

    private val _lastExit = MutableStateFlow<Exit?>(null)
    val lastExit: StateFlow<Exit?> = _lastExit

    @JvmStatic
    fun onGameExit(context: Context, code: Int, isSignal: Boolean) {
        runCatching {
            val log = Paths.gameLog(context)
            val excerpt = CrashParser.extract(log)
            val exit = Exit(code, isSignal, excerpt, GameManager.lastInstanceId, GameManager.lastVersionId)
            // persist: the process restarts right after this; the shell reads the marker
            Paths.exitMarker(context).writeText(
                "${code}|${if (isSignal) 1 else 0}|${GameManager.lastInstanceId ?: ""}|${GameManager.lastVersionId ?: ""}\n$excerpt"
            )
            _lastExit.value = exit
        }
    }

    /** Read + clear the persisted exit marker (called when the shell is recreated). */
    fun consumeMarker(context: Context): Exit? {
        val f = Paths.exitMarker(context)
        if (!f.isFile) return null
        return runCatching {
            val text = f.readText()
            f.delete()
            val firstLine = text.lineSequence().firstOrNull().orEmpty()
            val parts = firstLine.split("|")
            val code = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val isSignal = parts.getOrNull(1) == "1"
            val excerpt = text.substringAfter('\n', "")
            Exit(code, isSignal, excerpt, parts.getOrNull(2)?.takeIf { it.isNotBlank() }, parts.getOrNull(3)?.takeIf { it.isNotBlank() })
        }.getOrNull()
    }

    /** v1.13.0 — re-publish a consumed marker so the shell shows the dialog. */
    fun post(exit: Exit) {
        _lastExit.value = exit
    }

    /** v1.13.0 — clear the current crash report (dialog closed). */
    fun dismiss() {
        _lastExit.value = null
    }
}

/**
 * Pulls the human-readable crash reason out of the game log: the Minecraft
 * "The game crashed whilst …" line plus the top exception frames, or the
 * last exception-looking block as a fallback.
 */
object CrashParser {
    private val DESC = Regex("The game crashed whilst (.+)")
    private val EXC = Regex("^(?:Caused by:\\s+)?([\\w.$]+(?:Exception|Error|Throwable)[\\w.$]*)[:\\s]?.*")

    fun extract(logFile: File): String {
        val text = if (logFile.isFile) runCatching { logFile.readText() }.getOrDefault("") else ""
        if (text.isBlank()) return "The game closed without reporting an error."
        val lines = text.lines()
        val out = StringBuilder()

        // 1) Minecraft-style crash description line
        val descLine = lines.lastOrNull { DESC.containsMatchIn(it) }
        if (descLine != null) out.appendLine(descLine.trim()).appendLine()

        // 2) first meaningful exception chain (up to 8 lines)
        var count = 0
        var started = false
        for (line in lines) {
            val l = line.trim()
            val isExc = EXC.containsMatchIn(l) && !l.startsWith("at ")
            if (isExc) {
                started = true
                if (count == 0) out.appendLine("Error:")
                if (count > 0) out.appendLine()
                out.appendLine(l)
                count++
                if (count >= 3) break
                continue
            }
            if (started && l.startsWith("at ") && count < 6) {
                out.appendLine("\t" + l)
            } else if (started && l.startsWith("... ")) {
                out.appendLine("\t" + l)
                break
            }
        }
        if (out.isBlank()) {
            // fallback: last 12 non-empty log lines
            val tail = lines.filter { it.isNotBlank() }.takeLast(12)
            return tail.joinToString("\n").ifBlank { "An unexpected critical error was encountered" }
        }
        return out.toString().trim()
    }
}

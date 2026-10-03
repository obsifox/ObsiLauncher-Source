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
    @JvmOverloads
    fun onGameExit(context: Context, code: Int, isSignal: Boolean, directExcerpt: String? = null) {
        runCatching {
            // v1.13.1 — a direct excerpt (boot failure, empty JVM output) always
            // wins: the log FILE can still be empty because the stdio pipe
            // drains asynchronously on the logger thread.
            val excerpt = directExcerpt ?: CrashParser.extract(Paths.gameLog(context), context)
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
 * "The game crashed whilst …" line plus the top exception frames, the JVM
 * bootstrap errors (bad args / broken runtime), or the newest Minecraft
 * crash-report file as a last resort.
 */
object CrashParser {
    private val DESC = Regex("The game crashed whilst (.+)")
    private val EXC = Regex("^(?:Caused by:\\s+)?([\\w.$]+(?:Exception|Error|Throwable)[\\w.$]*)[:\\s]?.*")

    // v1.13.1 — JVM / bootstrap-level failures that never reach Minecraft's
    // crash handler but ARE printed to stderr by the launcher or the JVM
    private val BOOT = listOf(
        "Error: Could not create the Java Virtual Machine",
        "Error occurred during initialization of VM",
        "Error: Could not find or load main class",
        "Unrecognized option:",
        "Could not reserve enough space",
        "Could not find or load main class",
        "JLI lib = NULL",
        "JLI_Launch = NULL",
        "Fatal error",
        "hs_err",
    )

    fun extract(logFile: File, context: android.content.Context? = null): String {
        val text = if (logFile.isFile) runCatching { logFile.readText() }.getOrDefault("") else ""
        if (text.isBlank()) return "The game closed without reporting an error."
        val lines = text.lines()
        val out = StringBuilder()

        // 1) Minecraft-style crash description line
        val descLine = lines.lastOrNull { DESC.containsMatchIn(it) }
        if (descLine != null) out.appendLine(descLine.trim()).appendLine()

        // 2) JVM / bootstrap-level errors (v1.13.1)
        for (pattern in BOOT) {
            lines.firstOrNull { it.contains(pattern) }?.let {
                out.appendLine(it.trim())
                break
            }
        }

        // 3) first meaningful exception chain (up to 8 lines)
        var count = 0
        var started = false
        for (line in lines) {
            val l = line.trim()
            val isExc = EXC.containsMatchIn(l) && !l.startsWith("at ")
            if (isExc) {
                started = true
                if (count == 0 && descLine == null) out.appendLine("Error:")
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
            // fallback: newest Minecraft crash report file, else the log tail
            if (context != null) newestCrashReport(context)?.let { return it }
            val tail = lines.filter { it.isNotBlank() }.takeLast(15)
            return tail.joinToString("\n").ifBlank { "An unexpected critical error was encountered" }
        }
        return out.toString().trim()
    }

    /** v1.13.1 — Minecraft writes full reports to <game dir>/crash-reports/. */
    private fun newestCrashReport(context: android.content.Context): String? {
        val versionId = GameManager.lastVersionId ?: return null
        val reports = runCatching {
            val vdir = studio.obsifox.obsilauncher.core.Paths.versionDir(context, versionId)
            val gameDir = File(vdir, "game").takeIf { it.isDirectory } ?: vdir
            File(gameDir, "crash-reports")
                .takeIf { it.isDirectory }
                ?.listFiles { f -> f.name.endsWith(".txt") }
                ?.sortedBy { it.lastModified() }
        }.getOrNull().orEmpty()
        val newest = reports.lastOrNull() ?: return null
        return runCatching {
            "Report saved to crash-reports/${newest.name}\n\n" +
                newest.readLines().filter { it.isNotBlank() }.take(12).joinToString("\n")
        }.getOrNull()
    }
}

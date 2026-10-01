package studio.obsifox.launcher.core.launch

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import studio.obsifox.launcher.core.instance.Instance
import java.io.BufferedWriter
import java.nio.file.Files
import java.nio.file.Path

/** A running game process: streams its output, reports its exit code, can be killed. */
class GameSession(
    val instance: Instance,
    val command: LaunchCommand,
    private val process: Process,
    logFile: Path?,
    scope: CoroutineScope,
) {
    val startedAt: Long = System.currentTimeMillis()
    val pid: Long = process.pid()

    private val buffer = ArrayDeque<String>()
    private val lock = Any()
    @Volatile private var dirty = false
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val _running = MutableStateFlow(true)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    val exitCode = CompletableDeferred<Int>()

    init {
        val writer: BufferedWriter? = try {
            if (logFile != null) {
                Files.createDirectories(logFile.parent)
                Files.newBufferedWriter(logFile)
            } else null
        } catch (_: Exception) { null }

        val reader = Thread({
            try {
                val formatter = Log4jXmlFormatter()
                process.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { raw ->
                    for (line in formatter.feed(raw)) {
                        synchronized(lock) {
                            buffer.addLast(line)
                            if (buffer.size > MAX_LINES) buffer.removeFirst()
                            dirty = true
                        }
                        try { writer?.apply { write(line); newLine() } } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { writer?.close() } catch (_: Exception) {}
            }
        }, "game-output-${instance.id}").apply { isDaemon = true; start() }

        scope.launch {
            while (isActive && _running.value) {
                publish()
                delay(150)
            }
        }
        process.onExit().thenAccept { p ->
            try { reader.join(1500) } catch (_: InterruptedException) {}
            publish()
            _running.value = false
            exitCode.complete(p.exitValue())
        }
    }

    private fun publish() {
        if (!dirty) return
        val snapshot = synchronized(lock) { dirty = false; buffer.toList() }
        _lines.value = snapshot
    }

    /** Polite termination first, then force-kill after 4 seconds. */
    fun stop() {
        process.destroy()
        Thread {
            try {
                if (!process.waitFor(4, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly()
            } catch (_: InterruptedException) {}
        }.apply { isDaemon = true; start() }
    }

    /** Blocking variant for CLIs/tests: SIGTERM, wait [graceMs], then SIGKILL. */
    fun stopAndWait(graceMs: Long = 4000) {
        process.destroy()
        if (!process.waitFor(graceMs, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
        }
    }

    companion object {
        const val MAX_LINES = 4000
    }
}

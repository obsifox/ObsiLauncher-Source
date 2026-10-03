package studio.obsifox.obsilauncher.core.game

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.obsifox.obsilauncher.core.ObsiSettings
import studio.obsifox.obsilauncher.core.Paths
import studio.obsifox.obsilauncher.core.accounts.Account
import studio.obsifox.obsilauncher.core.cosmetics.SkinManager
import studio.obsifox.obsilauncher.core.instance.Instance
import studio.obsifox.obsilauncher.core.jni.ObsiBridge
import studio.obsifox.obsilauncher.core.runtime.Pack
import java.io.File
import java.io.FileInputStream

enum class GameState { NOT_RUNNING, PREPARING, RUNNING, EXITED }

/** Owns the single running game process and its console log. */
class GameManager(private val settings: ObsiSettings) {

    /** playtime hooks so the home screen can show play-time / last-played. */
    interface SessionEvents {
        fun onSessionStart(instanceId: String)
        fun onSessionEnd(instanceId: String, seconds: Long)
    }

    var sessionEvents: SessionEvents? = null

    data class Running(val instanceId: String, val versionId: String, val pid: Int)

    val state = MutableStateFlow(GameState.NOT_RUNNING)
    val running = MutableStateFlow<Running?>(null)
    val exitCode = MutableStateFlow<Int?>(null)
    val log = MutableStateFlow("")

    @Volatile
    private var logThreadActive = false

    fun launch(context: Context, instance: Instance, account: Account, pack: Pack) {
        if (state.value == GameState.RUNNING || state.value == GameState.PREPARING) return
        state.value = GameState.PREPARING
        exitCode.value = null
        log.value = ""
        var startedAt = 0L
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                // local-only custom skin/cape goes into the game dir before start
                val gameDir = Paths.versionDir(context, instance.versionId)
                val skinWarning = SkinManager.applyToGameDir(context, account, gameDir)
                skinWarning?.let { appendLog("!! ObsiLauncher: $it\n") }

                val jvmArgs = instance.javaArgs.ifEmpty { settings.javaArgsValue }
                val req = LaunchPipeline.Request(
                    instance = instance,
                    account = account,
                    pack = pack,
                    memoryMb = instance.memoryMb.takeIf { it > 0 } ?: settings.memoryMbValue,
                    extraJvmArgs = jvmArgs.split(" ").map { it.trim() }.filter { it.isNotEmpty() },
                )
                ObsiBridge.chdir(gameDir.absolutePath)
                val pid = LaunchPipeline.spawn(context, req)
                if (pid <= 0) {
                    state.value = GameState.EXITED
                    exitCode.value = -1
                    appendLog("!! ObsiLauncher: fork/exec failed — check the runtime pack (${pack.launcherSo.path})\n")
                    return@launch
                }
                running.value = Running(instance.id, instance.versionId, pid)
                state.value = GameState.RUNNING
                startedAt = System.currentTimeMillis()
                sessionEvents?.onSessionStart(instance.id)
                startLogReader()
                val code = ObsiBridge.waitPid(pid)
                sessionEvents?.onSessionEnd(instance.id, (System.currentTimeMillis() - startedAt) / 1000)
                exitCode.value = code
                state.value = GameState.EXITED
                running.value = null
            } catch (e: Exception) {
                if (startedAt > 0) {
                    sessionEvents?.onSessionEnd(instance.id, (System.currentTimeMillis() - startedAt) / 1000)
                }
                appendLog("!! ${e.message}\n")
                state.value = GameState.EXITED
                exitCode.value = -1
                running.value = null
            }
        }
    }

    fun stop() {
        val pid = running.value?.pid ?: return
        ObsiBridge.kill(pid)
    }

    private fun startLogReader() {
        if (logThreadActive) return
        logThreadActive = true
        Thread({
            val fd = ObsiBridge.takeLogFd()
            if (fd < 0) {
                logThreadActive = false
                return@Thread
            }
            try {
                // take ownership of the raw pipe fd and read the game log from it
                android.os.ParcelFileDescriptor.adoptFd(fd).use { pfd ->
                    FileInputStream(pfd.fileDescriptor).use { input ->
                        val buf = ByteArray(8 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            if (n > 0) appendLog(String(buf, 0, n))
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                logThreadActive = false
            }
        }, "obsi-log-reader").start()
    }

    private fun appendLog(text: String) {
        val current = log.value
        val next = (current + text)
        log.value = if (next.length > 300_000) next.substring(next.length - 300_000) else next
    }
}

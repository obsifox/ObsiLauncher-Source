package studio.obsifox.obsilauncher.core.game

import android.content.Context
import android.os.Process
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.Architecture
import net.kdt.pojavlaunch.Logger
import net.kdt.pojavlaunch.utils.JREUtils
import studio.obsifox.obsilauncher.App
import studio.obsifox.obsilauncher.core.ObsiSettings
import studio.obsifox.obsilauncher.core.Paths
import studio.obsifox.obsilauncher.core.accounts.Account
import studio.obsifox.obsilauncher.core.cosmetics.SkinManager
import studio.obsifox.obsilauncher.core.instance.Instance
import studio.obsifox.obsilauncher.core.runtime.ObsiComponents
import java.io.File

enum class GameState { NOT_RUNNING, PREPARING, RUNNING, EXITED }

/**
 * Owns the in-process game JVM session (ZalithLauncher/PojavLauncher model):
 * the JVM is booted on a dedicated thread via JLI_Launch, renders into the
 * game SurfaceView and its stdout/stderr are piped through the vendored
 * stdio_is into [Logger] → the launcher console.
 */
class GameManager(private val settings: ObsiSettings) {

    /** playtime hooks so the home screen can show play-time / last-played. */
    interface SessionEvents {
        fun onSessionStart(instanceId: String)
        fun onSessionEnd(instanceId: String, seconds: Long)
    }

    var sessionEvents: SessionEvents? = null

    data class Running(val instanceId: String, val versionId: String)

    val state = MutableStateFlow(GameState.NOT_RUNNING)
    val running = MutableStateFlow<Running?>(null)
    val exitCode = MutableStateFlow<Int?>(null)
    val log = MutableStateFlow("")

    @Volatile
    private var startedAt = 0L
    @Volatile
    private var activeInstance: Instance? = null

    companion object {
        @Volatile
        var lastInstanceId: String? = null
        @Volatile
        var lastVersionId: String? = null

        fun loadLibraries() {
            System.loadLibrary("pojavexec")
            System.loadLibrary("pojavexec_awt")
            System.loadLibrary("exithook")
        }
    }

    init {
        loadLibraries()
    }

    /**
     * Step 0 (v1.13.0): the shell's PLAY entry point — stores the account and
     * the runtime the pre-flight picked, validates the session and returns.
     * The UI then switches to the game screen whose SurfaceView hands its
     * Surface to [beginGame]. (The old fork/exec launch died before the JVM
     * ever opened — the ZalithLauncher in-process JLI_Launch model replaced it.)
     */
    fun launch(context: Context, instance: Instance, account: Account, runtimeName: String): Boolean {
        sessionAccount = account
        sessionRuntime = runtimeName
        return prepare(context, instance)
    }

    /**
     * Step 1: called from the shell when PLAY is pressed — validates and
     * switches the UI to the game screen (surface creation is next).
     */
    fun prepare(context: Context, instance: Instance): Boolean {
        if (state.value == GameState.RUNNING || state.value == GameState.PREPARING) return false
        state.value = GameState.PREPARING
        exitCode.value = null
        log.value = ""
        activeInstance = instance
        lastInstanceId = instance.id
        lastVersionId = instance.versionId
        return true
    }

    /**
     * Step 2: called by the game surface when its Surface is ready.
     * Boots the in-process JVM (blocks the calling thread until the game exits
     * or the exit hook restarts the process).
     */
    fun beginGame(context: Context, surface: Surface) {
        // guard: only the first surface after a prepare() may boot the JVM
        if (state.value != GameState.PREPARING) return
        val instance = activeInstance ?: run {
            state.value = GameState.NOT_RUNNING
            return
        }
        val app = context.applicationContext
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                // game log: latestlog.txt + console listener (vendored stdio_is)
                // v1.13.1 FIX (the exit-code -1 root cause): the native begin()
                // opens the log with O_TRUNC *without* O_CREAT, so the file MUST
                // exist first — Zalith does createNewFile() for exactly this
                // reason. Deleting it and calling begin() threw IOException
                // before the JVM ever booted, every single launch.
                val logFile = Paths.gameLog(app)
                logFile.delete()
                logFile.parentFile?.mkdirs()
                logFile.createNewFile()
                Logger.begin(logFile.absolutePath)
                Logger.setLogListener { text -> appendLog(text + "\n") }

                val account = sessionAccount ?: throw IllegalStateException("no account")
                val runtimeName = sessionRuntime
                    ?: ObsiComponents.installedRuntimeName(app, LaunchPipeline.javaMajor(app, instance.versionId))
                    ?: throw IllegalStateException("no Java runtime installed")

                // local-only custom skin/cape goes into the game dir before start
                val gameDir = Paths.versionDir(app, instance.versionId)
                val skinWarning = SkinManager.applyToGameDir(app, account, gameDir)
                skinWarning?.let { appendLog("!! ObsiLauncher: $it\n") }

                appendLog("--------- ObsiLauncher ${studio.obsifox.obsilauncher.BuildConfig.VERSION_NAME} ---------\n")
                appendLog("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (API ${android.os.Build.VERSION.SDK_INT})\n")
                appendLog("Arch: ${Architecture.archAsString(0)} — runtime: $runtimeName\n")
                appendLog("Version: ${instance.versionId}\n")

                val jreHome = ObsiComponents.runtimeHome(app, runtimeName).absolutePath

                // ---- env (Zalith setEnv) ----
                LaunchPipeline.env(app, LaunchPipeline.Request(
                    instance = instance,
                    account = account,
                    runtimeName = runtimeName,
                    memoryMb = instance.memoryMb.takeIf { it > 0 } ?: settings.memoryMbValue,
                    extraJvmArgs = instance.javaArgs.ifEmpty { settings.javaArgsValue }.split(" ").map { it.trim() }.filter { it.isNotEmpty() },
                ), jreHome).forEach { kv ->
                    val i = kv.indexOf('=')
                    runCatching {
                        android.system.Os.setenv(kv.substring(0, i), kv.substring(i + 1), true)
                    }
                }

                // ---- LD_LIBRARY_PATH + dlopen the JVM ----
                // Zalith relocateLibPath order: jli FIRST, then the JRE lib
                // dir, then the system/vendor dirs, our own libs last.
                val dirnameJre = LaunchPipeline.ToolsHome.dirNameHomeJre(jreHome)
                net.kdt.pojavlaunch.Tools.DIRNAME_HOME_JRE = dirnameJre // locateLibs must scan the SAME dir
                val server = File(jreHome, "$dirnameJre/server/libjvm.so")
                JREUtils.jvmLibraryPath = jreHome + "/" + dirnameJre + "/" + if (server.exists()) "server" else "client"
                // v1.13.1 — pre-boot self-check: the exact files JLI_Launch needs,
                // logged so a runtime-layout problem is visible in the crash dialog
                appendLog("Runtime home: $jreHome (libs: $dirnameJre)\n")
                val jliLib = File(jreHome, "$dirnameJre/jli/libjli.so")
                    .takeIf { it.isFile } ?: File(jreHome, "$dirnameJre/libjli.so")
                appendLog(
                    "libjli.so: ${if (jliLib.isFile) "ok" else "MISSING"} — " +
                        "libjvm.so: ${if (server.exists()) "ok" else "MISSING"}\n"
                )
                if (!jliLib.isFile || !server.exists()) {
                    throw IllegalStateException(
                        "runtime '$runtimeName' is broken (libjli/libjvm missing) — reinstall it from Settings → Components"
                    )
                }
                JREUtils.setLdLibPath(
                    jreHome + "/" + dirnameJre + "/jli:" +
                        JREUtils.jvmLibraryPath + ":" +
                        jreHome + "/" + dirnameJre + ":" +
                        "/system/lib64:/vendor/lib64:/vendor/lib64/hw:" +
                        LaunchPipeline.appNativeDir(app)
                )
                // freetype alias: some JRE builds ship libfreetype.so.6 only
                runCatching {
                    val libDir = File(jreHome, dirnameJre)
                    val dot6 = File(libDir, "libfreetype.so.6")
                    if (dot6.isFile && !File(libDir, "libfreetype.so").isFile) {
                        dot6.renameTo(File(libDir, "libfreetype.so"))
                    }
                }
                JREUtils.initJavaRuntime(jreHome)

                // renderer + openal (GL4ES from our own libs)
                JREUtils.dlopen(JREUtils.findInLdLibPath("libopenal.so"))
                JREUtils.dlopen(JREUtils.findInLdLibPath("libgl4es_114.so"))

                // exit plumbing: hook exit() so a dead game returns to the shell
                JREUtils.setupExitMethod(app)
                JREUtils.initializeGameExitHook()

                // surface → native bridge
                JREUtils.setupBridgeWindow(surface)

                val gameDirReal = File(gameDir, "game").takeIf { it.isDirectory } ?: gameDir
                JREUtils.chdir(gameDirReal.absolutePath)

                val req = LaunchPipeline.Request(
                    instance = instance,
                    account = account,
                    runtimeName = runtimeName,
                    memoryMb = instance.memoryMb.takeIf { it > 0 } ?: settings.memoryMbValue,
                    extraJvmArgs = instance.javaArgs.ifEmpty { settings.javaArgsValue }.split(" ").map { it.trim() }.filter { it.isNotEmpty() },
                )
                val args = LaunchPipeline.argv(app, req).toMutableList()
                args.add(0, "java")

                appendLog("Booting the game JVM in-process (runtime $runtimeName)…\n")
                state.value = GameState.RUNNING
                running.value = Running(instance.id, instance.versionId)
                startedAt = System.currentTimeMillis()
                sessionEvents?.onSessionStart(instance.id)

                // native logcat tags the vendored stdio_is/egl bridge print on
                // (Zalith parity: without these, native-side failures are invisible)
                JREUtils.startLogcatReader(arrayOf("jrelog", "LIBGL", "NativeInput", "pojavexec")) { line ->
                    appendLog(line)
                }

                val code = com.oracle.dalvik.VMLauncher.launchJVM(args.toTypedArray())
                appendLog("\nJava exit code: $code\n")
                sessionEvents?.onSessionEnd(instance.id, (System.currentTimeMillis() - startedAt) / 1000)
                exitCode.value = code
                state.value = GameState.EXITED
                running.value = null
                // v1.13.1 — give the stdio pipe's logger thread a moment to drain
                // the tail of the JVM output so the crash excerpt sees everything
                Thread.sleep(200)
                // excerpt source: the log FILE (JVM stdout/stderr). If it ended up
                // with no content (the failure never printed anything), fall back
                // to the in-memory console tail — it carries the header, the
                // runtime self-check and the logcat-side dlopen/JLI diagnostics.
                val fileText = Paths.gameLog(app).takeIf { it.isFile }?.readText().orEmpty()
                val directExcerpt = if (fileText.isBlank()) log.value.takeLast(1200).ifBlank { null } else null
                ObsiGameExit.onGameExit(app, code, false, directExcerpt = directExcerpt)
                // nominal_exit normally restarts the process before we get here;
                // if the JVM returned cleanly we restart manually for a clean shell
                Process.killProcess(Process.myPid())
            } catch (e: Throwable) {
                // v1.13.1 — the excerpt in the crash dialog comes from the log
                // FILE, and the pipe drains asynchronously: a boot failure could
                // therefore show a generic message instead of the real error.
                // Carry the exact reason directly into the crash report.
                val reason = "${e.javaClass.simpleName}: ${e.message ?: "unknown error"}"
                appendLog("!! ObsiLauncher launch error: $reason\n")
                e.printStackTrace()
                if (startedAt > 0) {
                    sessionEvents?.onSessionEnd(instance.id, (System.currentTimeMillis() - startedAt) / 1000)
                }
                state.value = GameState.EXITED
                exitCode.value = -1
                running.value = null
                // v1.13.0 — a boot failure is a crash too: persist the marker and
                // surface the crash dialog (the game JVM itself never opened)
                ObsiGameExit.onGameExit(
                    app, -1, false,
                    directExcerpt = "The game could not start.\n\nError: $reason\n\nIf this repeats, reinstall the runtime from Settings → Components."
                )
            }
        }
    }

    /** The account/runtime handed over by the shell before the UI switch. */
    @Volatile
    var sessionAccount: Account? = null
    @Volatile
    var sessionRuntime: String? = null

    /** User pressed "stop": persist a neutral marker and kill the process. */
    fun stop(context: Context) {
        if (state.value != GameState.RUNNING && state.value != GameState.PREPARING) return
        runCatching {
            Paths.exitMarker(context).writeText("0|0|${lastInstanceId ?: ""}|${lastVersionId ?: ""}\n")
        }
        Process.killProcess(Process.myPid())
    }

    private fun appendLog(text: String) {
        val next = log.value + text
        log.value = if (next.length > 300_000) next.substring(next.length - 300_000) else next
    }
}

package studio.obsifox.launcher.desktop

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import studio.obsifox.launcher.core.LauncherCore
import studio.obsifox.launcher.core.auth.Account
import studio.obsifox.launcher.core.instance.Instance
import studio.obsifox.launcher.core.instance.LoaderType
import studio.obsifox.launcher.core.launch.GameSession
import studio.obsifox.launcher.core.modrinth.MrVersion
import studio.obsifox.launcher.core.modrinth.Project
import studio.obsifox.launcher.core.modrinth.ProjectKind
import studio.obsifox.launcher.core.update.RemoteBuild
import studio.obsifox.launcher.core.update.UpdateStatus
import studio.obsifox.launcher.core.wallpaper.PackMeta
import studio.obsifox.launcher.core.wallpaper.Palette
import studio.obsifox.launcher.core.wallpaper.WallpaperCatalog
import studio.obsifox.launcher.core.wallpaper.WallpaperStore
import studio.obsifox.launcher.core.util.ProgressSink
import studio.obsifox.launcher.core.util.ProgressUpdate
import studio.obsifox.launcher.core.util.sha1Hex
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

sealed interface Screen {
    data object Home : Screen
    data object Instances : Screen
    data class InstanceDetail(val id: String, val tab: Int = 0) : Screen
    /** [instanceId] = pre-selected install target. */
    data class Browse(val instanceId: String? = null, val kind: ProjectKind = ProjectKind.MOD) : Screen
    data object Accounts : Screen
    data object Settings : Screen
    data object Console : Screen
}

enum class TaskStatus { RUNNING, DONE, FAILED, CANCELLED }

data class TaskUi(
    val id: Long,
    val title: String,
    val progress: ProgressUpdate? = null,
    val status: TaskStatus = TaskStatus.RUNNING,
    val error: String? = null,
)

data class CrashInfo(val instance: Instance, val exitCode: Int, val tail: List<String>, val logPath: Path?)

data class BuildInfo(val version: String, val commit: String) {
    companion object {
        fun load(): BuildInfo {
            val p = java.util.Properties()
            BuildInfo::class.java.getResourceAsStream("/obsi-build.properties")?.use { p.load(it) }
            return BuildInfo(p.getProperty("version", "dev"), p.getProperty("commit", "dev"))
        }
    }
}

/** The picture behind the launcher: the best-fitting variant of the artwork that belongs to the selected profile. */
data class Backdrop(
    val packId: String? = null,
    val title: String? = null,
    val file: Path? = null,
    val width: Int = 0,
    val height: Int = 0,
    val palette: Palette? = null,
    /** The artwork of the selected profile is still being downloaded. */
    val loading: Boolean = false,
)

/** Start screen ("Update Gate"): the launcher checks its own update channel before the home screen opens. */
sealed interface GateState {
    data object Checking : GateState
    data object Verifying : GateState
    data class Ready(val remote: RemoteBuild?) : GateState
    data class Available(val remote: RemoteBuild, val page: String) : GateState
    data object Offline : GateState
    data class Failed(val reason: String) : GateState
}

val LocalApp = staticCompositionLocalOf<AppController> { error("AppController not provided") }

/** All UI state and actions live here; composables only observe flows and call these functions. */
class AppController(val core: LauncherCore, val build: BuildInfo, autoStart: Boolean = true) {
    private val scope = core.scope

    val screen = MutableStateFlow<Screen>(Screen.Home)
    val tasks = MutableStateFlow<List<TaskUi>>(emptyList())
    val sessions = MutableStateFlow<Map<String, GameSession>>(emptyMap())
    val crash = MutableStateFlow<CrashInfo?>(null)
    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val toasts: SharedFlow<String> = _toasts

    private val _strings = MutableStateFlow(Strings(Lang.resolve(core.settings.value.language)))
    val strings: StateFlow<Strings> = _strings.asStateFlow()
    val s: Strings get() = _strings.value

    init {
        scope.launch { core.settings.flow.collect { _strings.value = Strings(Lang.resolve(it.language)) } }
    }

    // ------------------------------------------------------------------------------------------ update gate (start screen)

    val gate = MutableStateFlow<GateState>(GateState.Checking)
    val gatePassed = MutableStateFlow(false)

    /** The remote update check was skipped (no internet); the home screen keeps saying so. */
    val offlineMode = MutableStateFlow(false)
    private var gateJob: Job? = null

    fun runGate() {
        gateJob?.cancel()
        gateJob = scope.launch {
            gate.value = GateState.Checking
            val started = System.currentTimeMillis()
            val result = core.updates.check(build.commit)
            delay((900 - (System.currentTimeMillis() - started)).coerceAtLeast(0)) // let the phases breathe; the check itself is real
            if (result is UpdateStatus.Offline) { gate.value = GateState.Offline; return@launch }
            gate.value = GateState.Verifying
            val problem = withContext(Dispatchers.IO) { verifyInstallation() }
            delay(600)
            gate.value = when {
                problem != null -> GateState.Failed(problem)
                result is UpdateStatus.Available -> GateState.Available(result.remote, result.page)
                result is UpdateStatus.Failed -> GateState.Failed(result.reason)
                result is UpdateStatus.UpToDate -> GateState.Ready(result.remote)
                else -> GateState.Ready(null)
            }
        }
    }

    /** What "Verifying launcher files" really does: the data directory has to be usable. Returns a problem text or null. */
    private fun verifyInstallation(): String? = try {
        Files.createDirectories(core.paths.root)
        val probe = core.paths.root.resolve(".write-test")
        Files.writeString(probe, "ok")
        Files.deleteIfExists(probe)
        null
    } catch (e: Exception) {
        "data folder is not writable (${e.message})"
    }

    fun continueFromGate() {
        offlineMode.value = gate.value is GateState.Offline
        gatePassed.value = true
        if (!core.settings.value.firstRunComplete) wizardOpen.value = true
    }

    // ------------------------------------------------------------------------------------------ window / appearance

    val fullscreen = MutableStateFlow(false)
    val wizardOpen = MutableStateFlow(false)

    fun setLanguage(l: Lang) = core.settings.update { it.copy(language = l.code) }

    // ------------------------------------------------------------------------------------------ background art

    private val viewportPx = MutableStateFlow(1920 to 1080)
    val backdrop = MutableStateFlow(Backdrop())
    private val artRequested = ConcurrentHashMap<String, Long>()

    /** Called by the UI with the real window size in pixels; rounded so a drag-resize does not thrash the picker. */
    fun setViewport(w: Int, h: Int) {
        val b = ((w / 160) * 160).coerceAtLeast(640) to ((h / 160) * 160).coerceAtLeast(480)
        if (viewportPx.value != b) viewportPx.value = b
    }

    /** The pack that belongs to the selected profile, according to the current wallpaper mode. */
    suspend fun currentPack() = when (core.settings.value.wallpaperMode) {
        "latest" -> WallpaperCatalog.latest
        else -> selectedInstance()?.let { core.wallpapers.packFor(it.mcVersion) } ?: WallpaperCatalog.latest
    }

    private suspend fun resolveBackdrop(): Backdrop = withContext(Dispatchers.IO) {
        val st = core.settings.value
        val (vw, vh) = viewportPx.value
        val store = core.wallpapers
        fun of(m: PackMeta, loading: Boolean = false): Backdrop {
            val v = WallpaperStore.best(m, vw, vh)
            return Backdrop(m.id, m.title, store.file(m, v), v.width, v.height, m.palette, loading)
        }
        if (st.wallpaperMode == "custom") store.meta(WallpaperStore.CUSTOM_ID)?.let { return@withContext of(it) }
        val pack = currentPack()
        store.meta(pack.id)?.let { return@withContext of(it) }
        // not on disk yet: fetch it in the background (retrying at most once a minute) and show the newest art we do have meanwhile
        val now = System.currentTimeMillis()
        if (now - (artRequested[pack.id] ?: 0L) > 60_000L) {
            artRequested[pack.id] = now
            scope.launch { runCatching { store.ensure(pack) } }
        }
        val stand = store.meta(WallpaperCatalog.latest.id)
            ?: store.installed.value.asSequence().filter { it != WallpaperStore.CUSTOM_ID }.mapNotNull { store.meta(it) }.firstOrNull()
        stand?.let { of(it, loading = true) } ?: Backdrop(loading = true)
    }

    // ------------------------------------------------------------------------------------------ navigation / selection

    fun go(target: Screen) { screen.value = target }
    fun toast(msg: String) { _toasts.tryEmit(msg) }

    fun selectedAccount(): Account? {
        val st = core.settings.value
        return core.accounts.get(st.selectedAccountId) ?: core.accounts.all.firstOrNull()
    }

    fun selectedInstance(): Instance? {
        val st = core.settings.value
        return core.instances.get(st.selectedInstanceId) ?: core.instances.all.firstOrNull()
    }

    fun selectAccount(id: String) = core.settings.update { it.copy(selectedAccountId = id) }
    fun selectInstance(id: String) = core.settings.update { it.copy(selectedInstanceId = id) }

    // ------------------------------------------------------------------------------------------ tasks

    private val nextTaskId = AtomicLong(0)
    private val jobs = ConcurrentHashMap<Long, Job>()

    private fun updateTask(id: Long, f: (TaskUi) -> TaskUi) = tasks.update { l -> l.map { if (it.id == id) f(it) else it } }
    private fun removeTask(id: Long) { tasks.update { l -> l.filterNot { it.id == id } }; jobs.remove(id) }
    fun dismissTask(id: Long) = removeTask(id)
    fun cancelTask(id: Long) { jobs[id]?.cancel() }

    /** Runs [block] in the background with a progress entry in the task bar. */
    fun <T> task(title: String, onSuccess: suspend (T) -> Unit = {}, block: suspend (ProgressSink) -> T): Long {
        val id = nextTaskId.incrementAndGet()
        tasks.update { it + TaskUi(id, title) }
        jobs[id] = scope.launch {
            try {
                val result = block { u -> updateTask(id) { it.copy(progress = u) } }
                updateTask(id) { it.copy(status = TaskStatus.DONE) }
                onSuccess(result)
                delay(3000)
                removeTask(id)
            } catch (e: CancellationException) {
                updateTask(id) { it.copy(status = TaskStatus.CANCELLED) }
                delay(1200)
                removeTask(id)
                throw e
            } catch (e: Throwable) {
                updateTask(id) { it.copy(status = TaskStatus.FAILED, error = e.message ?: e.toString()) }
            }
        }
        return id
    }

    // ------------------------------------------------------------------------------------------ game

    fun isRunning(instanceId: String) = sessions.value[instanceId]?.running?.value == true

    /** Profiles that are being prepared right now (downloads) - prevents a second click from launching twice. */
    private val launching = MutableStateFlow<Set<String>>(emptySet())
    val launchingIds: StateFlow<Set<String>> = launching.asStateFlow()

    fun play(instance: Instance) {
        if (sessions.value.containsKey(instance.id)) { go(Screen.Console); return }
        val account = selectedAccount()
        if (account == null) {
            toast(s["no_account"])
            go(Screen.Accounts)
            return
        }
        synchronized(this) { if (instance.id in launching.value) return; launching.update { it + instance.id } }
        selectInstance(instance.id)
        task<Unit>(s.fmt("launching", instance.name)) { progress ->
            try {
                progress(ProgressUpdate("Resolving"))
                val prepared = core.prepare(instance.id, account, progress)
                val session = core.start(prepared)
                sessions.update { it + (instance.id to session) }
                if (core.settings.value.openConsoleOnLaunch) go(Screen.Console)
                scope.launch {
                    val code = session.exitCode.await()
                    sessions.update { it - instance.id }
                    if (code != 0 && !session.stopRequested) {
                        crash.value = CrashInfo(instance, code, session.lines.value.takeLast(40), core.paths.logs.resolve("${instance.id}-latest.log"))
                    }
                }
            } finally {
                launching.update { it - instance.id }
            }
        }
    }

    fun stop(instanceId: String) { sessions.value[instanceId]?.stop() }

    // ------------------------------------------------------------------------------------------ instances

    fun createInstance(name: String, mc: String, loader: LoaderType, loaderVersion: String?) {
        task<Unit>(s.fmt("creating", name)) { progress ->
            val inst = core.createInstance(name, mc, loader, loaderVersion, progress)
            selectInstance(inst.id)
        }
    }

    fun deleteInstance(id: String) {
        sessions.value[id]?.stop()
        scope.launch(Dispatchers.IO) {
            core.instances.delete(id)
            if (core.settings.value.selectedInstanceId == id) core.settings.update { it.copy(selectedInstanceId = null) }
        }
    }

    fun duplicateInstance(inst: Instance) {
        task<Unit>(s.fmt("creating", inst.name)) {
            core.instances.duplicate(inst.id, s.fmt("copy_suffix", inst.name))
        }
    }

    fun importMrpack(file: Path) {
        task<Unit>(s.fmt("installing", file.fileName.toString()), onSuccess = { toast(s.fmt("modpack_installed", file.fileName.toString())) }) { progress ->
            core.modpacks.installFromFile(file, null, null, progress)
        }
    }

    // ------------------------------------------------------------------------------------------ modrinth

    /** Installs a mod / pack / shader (or its newest compatible version when [version] is null) into [instance]. */
    fun installContent(instance: Instance, project: Project, version: MrVersion?) {
        task<List<String>>(s.fmt("installing", project.title), onSuccess = { names ->
            toast(if (names.size > 1) s.fmt("installed_with_deps", project.title, names.size - 1) else s.fmt("installed_ok", project.title))
        }) { progress ->
            val kind = ProjectKind.fromApi(project.projectType)
            val v = version ?: core.content.pickVersion(instance, project.id, kind)
                ?: throw IllegalStateException(s.fmt("no_compatible", "${instance.mcVersion} ${instance.loader.display}"))
            core.content.install(instance, project, v, progress)
        }
    }

    fun installModpack(project: Project, version: MrVersion) {
        task<Unit>(s.fmt("installing", project.title), onSuccess = { toast(s.fmt("modpack_installed", project.title)) }) { progress ->
            val inst = core.modpacks.install(project, version, progress)
            selectInstance(inst.id)
        }
    }

    // ------------------------------------------------------------------------------------------ images

    private val images = ConcurrentHashMap<String, ImageBitmap>()
    private val failed = ConcurrentHashMap.newKeySet<String>()

    fun cachedImage(url: String): ImageBitmap? = images[url]

    /** Loads (and disk-caches) a remote icon. Returns null when it cannot be fetched or decoded. */
    suspend fun image(url: String): ImageBitmap? {
        images[url]?.let { return it }
        if (url in failed) return null
        return withContext(Dispatchers.IO) {
            try {
                val file = core.paths.cache.resolve("icons").resolve(sha1Hex(url.toByteArray()))
                val bytes = if (Files.isRegularFile(file)) Files.readAllBytes(file) else {
                    core.http.getBytes(url, 4 * 1024 * 1024).also { Files.createDirectories(file.parent); Files.write(file, it) }
                }
                Image.makeFromEncoded(bytes).toComposeImageBitmap().also { images[url] = it }
            } catch (_: CancellationException) {
                null
            } catch (_: Throwable) {
                failed += url
                null
            }
        }
    }

    // must stay at the end of the class: it uses properties declared above
    init {
        scope.launch {
            // re-pick the background whenever the profile, the settings, the downloaded art or the window shape changes
            combine(core.settings.flow, core.instances.flow, core.wallpapers.changes, viewportPx) { _, _, _, _ -> Unit }
                .conflate()
                .collectLatest { backdrop.value = resolveBackdrop() }
        }
        if (autoStart) runGate()
    }
}

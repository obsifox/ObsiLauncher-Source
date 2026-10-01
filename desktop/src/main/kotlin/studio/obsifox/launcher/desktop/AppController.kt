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

data class BuildInfo(val version: String, val msClientId: String?, val commit: String) {
    companion object {
        fun load(): BuildInfo {
            val p = java.util.Properties()
            BuildInfo::class.java.getResourceAsStream("/obsi-build.properties")?.use { p.load(it) }
            return BuildInfo(p.getProperty("version", "dev"), p.getProperty("msClientId")?.takeIf { it.isNotBlank() }, p.getProperty("commit", "dev"))
        }
    }
}

val LocalApp = staticCompositionLocalOf<AppController> { error("AppController not provided") }

/** All UI state and actions live here; composables only observe flows and call these functions. */
class AppController(val core: LauncherCore, val build: BuildInfo) {
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

    fun play(instance: Instance) {
        if (sessions.value.containsKey(instance.id)) { go(Screen.Console); return }
        val account = selectedAccount()
        if (account == null) {
            toast(s["no_account"])
            go(Screen.Accounts)
            return
        }
        selectInstance(instance.id)
        task<Unit>(s.fmt("launching", instance.name)) { progress ->
            progress(ProgressUpdate("Resolving"))
            val prepared = core.prepare(instance.id, account, progress)
            val session = core.start(prepared)
            sessions.update { it + (instance.id to session) }
            if (core.settings.value.openConsoleOnLaunch) go(Screen.Console)
            scope.launch {
                val code = session.exitCode.await()
                sessions.update { it - instance.id }
                if (code != 0) {
                    crash.value = CrashInfo(instance, code, session.lines.value.takeLast(40), core.paths.logs.resolve("${instance.id}-latest.log"))
                }
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
}

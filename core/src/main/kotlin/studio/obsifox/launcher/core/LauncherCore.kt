package studio.obsifox.launcher.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import studio.obsifox.launcher.core.auth.Account
import studio.obsifox.launcher.core.auth.AccountStore
import studio.obsifox.launcher.core.auth.AuthService
import studio.obsifox.launcher.core.instance.Instance
import studio.obsifox.launcher.core.instance.InstanceStore
import studio.obsifox.launcher.core.instance.LoaderType
import studio.obsifox.launcher.core.instance.SettingsStore
import studio.obsifox.launcher.core.java.JavaRuntimeManager
import studio.obsifox.launcher.core.launch.GameSession
import studio.obsifox.launcher.core.launch.LaunchBuilder
import studio.obsifox.launcher.core.launch.LaunchCommand
import studio.obsifox.launcher.core.loaders.LoaderService
import studio.obsifox.launcher.core.modrinth.ContentManager
import studio.obsifox.launcher.core.modrinth.ModpackInstaller
import studio.obsifox.launcher.core.modrinth.ModrinthApi
import studio.obsifox.launcher.core.mojang.GameInstaller
import studio.obsifox.launcher.core.mojang.MojangApi
import studio.obsifox.launcher.core.net.Downloader
import studio.obsifox.launcher.core.net.Http
import studio.obsifox.launcher.core.util.LauncherException
import studio.obsifox.launcher.core.util.Platform
import studio.obsifox.launcher.core.util.ProgressSink
import studio.obsifox.launcher.core.util.ProgressUpdate
import java.nio.file.Files

class PreparedLaunch(val command: LaunchCommand, val account: Account, val instance: Instance)

/**
 * Wires every service together. UI code only talks to this class (plus the stores' StateFlows).
 *
 * @param defaultMsClientId Azure application id baked into the build (CI secret MS_CLIENT_ID); users can override it in Settings.
 */
class LauncherCore(
    val paths: LauncherPaths = LauncherPaths(Platform.defaultDataDir()),
    val version: String = "dev",
    private val defaultMsClientId: String? = null,
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    init { paths.ensure() }

    val settings = SettingsStore(paths)
    val http = Http("obsifox/ObsiLauncher/$version (https://github.com/obsifox/ObsiLauncher)", settings.value.net)
    val downloader = Downloader(http, settings.value.downloadThreads)
    val mojang = MojangApi(http, paths)
    val installer = GameInstaller(http, paths, mojang, downloader)
    val java = JavaRuntimeManager(http, paths, downloader)
    val loaders = LoaderService(http, paths, mojang, java, installer, downloader)
    val instances = InstanceStore(paths)
    val accounts = AccountStore(paths)
    val auth = AuthService(http, accounts) { settings.value.msClientId?.takeIf { it.isNotBlank() } ?: defaultMsClientId }
    val modrinth = ModrinthApi(http)
    val content = ContentManager(paths, modrinth, downloader)
    val modpacks = ModpackInstaller(paths, modrinth, downloader, loaders, instances)

    val microsoftConfigured: Boolean
        get() = !(settings.value.msClientId?.takeIf { it.isNotBlank() } ?: defaultMsClientId).isNullOrBlank()

    init { applySettings() }

    /** Call after changing proxy / mirror / thread settings. */
    fun applySettings() {
        val s = settings.value
        http.config = s.net
        downloader.parallelism = s.downloadThreads
    }

    suspend fun createInstance(name: String, mc: String, loader: LoaderType, loaderVersion: String?, progress: ProgressSink): Instance {
        val clean = name.trim().ifBlank { throw LauncherException("Instance name is empty") }
        val installed = loaders.install(loader, mc, loaderVersion, progress)
        val instance = Instance(
            id = instances.newId(clean), name = clean, mcVersion = mc, loader = loader,
            loaderVersion = installed.loaderVersion, launchVersionId = installed.versionId,
        )
        instances.save(instance)
        return instance
    }

    /** Makes sure everything is downloaded and builds the exact command line. Safe to call before every launch. */
    suspend fun prepare(instanceId: String, account: Account, progress: ProgressSink): PreparedLaunch {
        var instance = instances.get(instanceId) ?: throw LauncherException("Instance not found")
        if (instance.launchVersionId == null) {
            val installed = loaders.install(instance.loader, instance.mcVersion, instance.loaderVersion, progress)
            instance = instance.copy(launchVersionId = installed.versionId, loaderVersion = installed.loaderVersion ?: instance.loaderVersion)
            instances.save(instance)
        }
        val versionId = instance.launchVersionId!!
        val rv = installer.install(versionId, progress)
        val s = settings.value
        progress(ProgressUpdate("java"))
        val javaExe = java.resolveFor(instance.javaPath ?: s.javaPath, rv.javaComponent, rv.javaMajor, progress)
        val gameDir = paths.gameDir(instance.id)
        Files.createDirectories(gameDir)
        installer.mapAssetsToResources(rv, gameDir)
        val fresh = auth.ensureFresh(account)
        val command = LaunchBuilder.build(rv, paths, installer.classpath(rv), instance, fresh, s, javaExe, version)
        return PreparedLaunch(command, fresh, instance)
    }

    /** Spawns the game. Play time and "last played" are recorded when it exits. */
    fun start(prepared: PreparedLaunch): GameSession {
        val cmd = prepared.command
        val pb = ProcessBuilder(cmd.commandLine).directory(cmd.workDir.toFile()).redirectErrorStream(true)
        val process = try {
            pb.start()
        } catch (e: Exception) {
            throw LauncherException("Could not start Java: ${e.message}", e)
        }
        val session = GameSession(prepared.instance, cmd, process, paths.logs.resolve("${prepared.instance.id}-latest.log"), scope)
        scope.launch {
            val code = session.exitCode.await()
            val latest = instances.get(prepared.instance.id)
            if (latest != null) {
                val played = ((System.currentTimeMillis() - session.startedAt) / 1000).coerceAtLeast(0)
                instances.save(latest.copy(lastPlayedAt = session.startedAt, playTimeSeconds = latest.playTimeSeconds + played))
            }
            if (code != 0) Files.writeString(paths.logs.resolve("last-exit-code.txt"), "${prepared.instance.id}: $code")
        }
        return session
    }
}

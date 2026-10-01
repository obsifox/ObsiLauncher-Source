package studio.obsifox.launcher.core.modrinth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import studio.obsifox.launcher.core.LauncherPaths
import studio.obsifox.launcher.core.instance.Instance
import studio.obsifox.launcher.core.instance.InstanceStore
import studio.obsifox.launcher.core.instance.LoaderType
import studio.obsifox.launcher.core.instance.ModpackInfo
import studio.obsifox.launcher.core.loaders.LoaderService
import studio.obsifox.launcher.core.net.DownloadItem
import studio.obsifox.launcher.core.net.Downloader
import studio.obsifox.launcher.core.util.LauncherException
import studio.obsifox.launcher.core.util.ProgressSink
import studio.obsifox.launcher.core.util.ProgressUpdate
import studio.obsifox.launcher.core.util.RemoteJson
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

/** Installs Modrinth modpacks (.mrpack, format version 1) as a fresh instance. */
class ModpackInstaller(
    private val paths: LauncherPaths,
    private val api: ModrinthApi,
    private val downloader: Downloader,
    private val loaders: LoaderService,
    private val instances: InstanceStore,
) {
    suspend fun install(project: Project, version: MrVersion, progress: ProgressSink): Instance {
        val file = version.files.firstOrNull { it.filename.endsWith(".mrpack") } ?: version.primaryFile
            ?: throw LauncherException("This modpack version has no .mrpack file")
        val pack = paths.cache.resolve("modpacks").resolve(file.filename.substringAfterLast('/'))
        downloader.run(listOf(DownloadItem(listOf(file.url), pack, file.hashes["sha1"], file.size.takeIf { it > 0 })), "modpack", progress)
        return installFromFile(pack, ModpackInfo("modrinth", project.id, version.id, version.versionNumber, project.title), project.iconUrl, progress)
    }

    /** Also usable for a .mrpack the user already has on disk. */
    suspend fun installFromFile(mrpack: Path, info: ModpackInfo?, iconUrl: String?, progress: ProgressSink): Instance {
        val (index, zipPath) = withContext(Dispatchers.IO) {
            ZipFile(mrpack.toFile()).use { z ->
                val e = z.getEntry("modrinth.index.json") ?: throw LauncherException("Not a valid .mrpack (modrinth.index.json missing)")
                RemoteJson.decodeFromString(PackIndex.serializer(), z.getInputStream(e).bufferedReader().readText()) to mrpack
            }
        }
        val mc = index.dependencies["minecraft"] ?: throw LauncherException("Modpack does not declare a Minecraft version")
        val (loader, loaderVersion) = when {
            "fabric-loader" in index.dependencies -> LoaderType.FABRIC to index.dependencies["fabric-loader"]
            "quilt-loader" in index.dependencies -> LoaderType.QUILT to index.dependencies["quilt-loader"]
            "neoforge" in index.dependencies -> LoaderType.NEOFORGE to index.dependencies["neoforge"]
            "forge" in index.dependencies -> LoaderType.FORGE to index.dependencies["forge"]
            else -> LoaderType.VANILLA to null
        }
        val name = info?.name ?: index.name.ifBlank { "Modpack" }
        val id = instances.newId(name)
        val game = paths.gameDir(id)
        Files.createDirectories(game)

        progress(ProgressUpdate("Installing ${loader.display} for $mc"))
        val installedLoader = loaders.install(loader, mc, loaderVersion, progress)

        // pack files (mods, configs hosted on CDN)
        val items = index.files.filter { it.env?.client != "unsupported" }.map { f ->
            val dest = game.resolve(f.path).normalize()
            if (!dest.startsWith(game)) throw LauncherException("Unsafe path in modpack: ${f.path}")
            DownloadItem(f.downloads, dest, f.hashes["sha1"], f.fileSize.takeIf { it > 0 })
        }
        downloader.run(items, "modpack-files", progress)

        // overrides
        withContext(Dispatchers.IO) {
            ZipFile(zipPath.toFile()).use { z ->
                for (prefix in listOf("overrides/", "client-overrides/")) {
                    for (e in z.entries()) {
                        if (e.isDirectory || !e.name.startsWith(prefix)) continue
                        val target = game.resolve(e.name.removePrefix(prefix)).normalize()
                        if (!target.startsWith(game)) continue
                        Files.createDirectories(target.parent)
                        z.getInputStream(e).use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
                    }
                }
            }
        }

        val instance = Instance(
            id = id, name = name, mcVersion = mc, loader = loader, loaderVersion = installedLoader.loaderVersion ?: loaderVersion,
            launchVersionId = installedLoader.versionId, iconUrl = iconUrl, modpack = info,
        )
        instances.save(instance)
        return instance
    }
}

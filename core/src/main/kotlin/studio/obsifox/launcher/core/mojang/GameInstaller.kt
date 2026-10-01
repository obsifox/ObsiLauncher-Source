package studio.obsifox.launcher.core.mojang

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import studio.obsifox.launcher.core.LauncherPaths
import studio.obsifox.launcher.core.net.DownloadItem
import studio.obsifox.launcher.core.net.Downloader
import studio.obsifox.launcher.core.net.Http
import studio.obsifox.launcher.core.util.Platform
import studio.obsifox.launcher.core.util.ProgressSink
import studio.obsifox.launcher.core.util.ProgressUpdate
import studio.obsifox.launcher.core.util.RemoteJson
import studio.obsifox.launcher.core.util.sha1Hex
import studio.obsifox.launcher.core.util.writeTextAtomic
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

/** Downloads and verifies everything a version needs: client jar, libraries, natives, assets, log config. */
class GameInstaller(
    private val http: Http,
    private val paths: LauncherPaths,
    private val mojang: MojangApi,
    private val downloader: Downloader,
) {
    /** Builds the merged version (downloading vanilla parent JSONs when needed). */
    suspend fun resolve(id: String): ResolvedVersion {
        val chain = ArrayDeque<VersionJson>()
        var cur = mojang.versionJson(id)
        chain.addFirst(cur)
        var guard = 0
        while (cur.inheritsFrom != null && guard++ < 8) {
            cur = mojang.versionJson(cur.inheritsFrom!!)
            chain.addFirst(cur)
        }
        return VersionResolver.resolve(chain.toList())
    }

    suspend fun install(id: String, progress: ProgressSink): ResolvedVersion {
        progress(ProgressUpdate("Resolving $id"))
        val rv = resolve(id)

        // ---- stage 1: client jar, libraries, logging config
        val items = ArrayList<DownloadItem>()
        val rootJar = paths.versionJar(rv.rootId)
        rv.clientDownload?.let { c ->
            if (c.url != null) items += DownloadItem(listOf(c.url), rootJar, c.sha1, c.size)
        }
        for (lib in rv.libraries) {
            if (!Rules.allowed(lib.rules)) continue
            items += libraryItems(lib)
        }
        rv.logging?.file?.let { f ->
            if (f.url != null && f.id != null) items += DownloadItem(listOf(f.url), paths.assets.resolve("log_configs").resolve(f.id), f.sha1, f.size)
        }
        downloader.run(items, "libraries", progress)

        // ---- stage 2: assets
        installAssets(rv, progress)

        // ---- stage 3: natives and the launched jar
        withContext(Dispatchers.IO) {
            extractNatives(rv)
            if (rv.inherited) {
                val own = paths.versionJar(rv.id)
                if (Files.isRegularFile(rootJar) && (!Files.isRegularFile(own) || Files.size(own) != Files.size(rootJar))) {
                    Files.createDirectories(own.parent)
                    Files.copy(rootJar, own, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
        return rv
    }

    private suspend fun installAssets(rv: ResolvedVersion, progress: ProgressSink) {
        val ref = rv.assetIndex
        val indexFile = paths.assets.resolve("indexes").resolve("${ref.id}.json")
        if (ref.url != null) {
            downloader.run(listOf(DownloadItem(listOf(ref.url), indexFile, ref.sha1, ref.size)), "asset-index", progress)
        }
        if (!Files.isRegularFile(indexFile)) return
        val index = RemoteJson.decodeFromString<AssetIndex>(Files.readString(indexFile))
        val objects = paths.assets.resolve("objects")
        val items = index.objects.values.distinctBy { it.hash }.map { o ->
            val prefix = o.hash.substring(0, 2)
            DownloadItem(
                listOf("https://resources.download.minecraft.net/$prefix/${o.hash}"),
                objects.resolve(prefix).resolve(o.hash), o.hash, o.size,
            )
        }
        downloader.run(items, "assets", progress)

        if (index.virtual || index.mapToResources) {
            withContext(Dispatchers.IO) {
                val target = if (index.mapToResources) null else paths.assets.resolve("virtual").resolve(ref.id)
                if (target != null) {
                    for ((name, o) in index.objects) {
                        val src = objects.resolve(o.hash.substring(0, 2)).resolve(o.hash)
                        val dst = target.resolve(name)
                        if (!Files.isRegularFile(dst) || Files.size(dst) != o.size) {
                            Files.createDirectories(dst.parent)
                            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING)
                        }
                    }
                }
            }
        }
    }

    fun assetIndexOf(rv: ResolvedVersion): AssetIndex? {
        val f = paths.assets.resolve("indexes").resolve("${rv.assetIndex.id}.json")
        return if (Files.isRegularFile(f)) RemoteJson.decodeFromString<AssetIndex>(Files.readString(f)) else null
    }

    /** Very old versions (assets "pre-1.6") read sounds/music from `<gameDir>/resources`; mirror the objects there. */
    suspend fun mapAssetsToResources(rv: ResolvedVersion, gameDir: Path) = withContext(Dispatchers.IO) {
        val index = assetIndexOf(rv) ?: return@withContext
        if (!index.mapToResources) return@withContext
        val objects = paths.assets.resolve("objects")
        for ((name, o) in index.objects) {
            val src = objects.resolve(o.hash.substring(0, 2)).resolve(o.hash)
            val dst = gameDir.resolve("resources").resolve(name).normalize()
            if (!dst.startsWith(gameDir) || !Files.isRegularFile(src)) continue
            if (!Files.isRegularFile(dst) || Files.size(dst) != o.size) {
                Files.createDirectories(dst.parent)
                Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    // -------------------------------------------------------------------------------------------- libraries

    private fun classifierFor(lib: Library): String? =
        lib.natives?.get(Platform.os.mojang)?.replace("\${arch}", if (Platform.is64Bit) "64" else "32")

    private fun libraryItems(lib: Library): List<DownloadItem> {
        val out = ArrayList<DownloadItem>()
        val classifier = classifierFor(lib)
        val d = lib.downloads
        if (lib.natives != null) {
            // legacy natives: the classifier jar for this OS (extracted at launch) ...
            if (classifier != null) {
                val art = d?.classifiers?.get(classifier)
                if (art != null) {
                    artifactItem(art, Maven.path(lib.name, classifier))?.let { out.add(it) }
                } else {
                    out.add(mavenItem(lib, Maven.path(lib.name, classifier)))
                }
            }
            // ... plus the regular jar when the library has one (LWJGL 3.2.x in 1.13-1.17 ships both)
            d?.artifact?.let { a -> artifactItem(a, a.path ?: Maven.path(lib.name))?.let { out.add(it) } }
            return out
        }
        val art = d?.artifact
        if (art != null) {
            artifactItem(art, art.path ?: Maven.path(lib.name))?.let { out.add(it) }
        } else if (d == null || d.artifact == null && d.classifiers == null) {
            out.add(mavenItem(lib, Maven.path(lib.name)))
        }
        return out
    }

    private fun artifactItem(a: DownloadInfo, path: String): DownloadItem? {
        val dest = paths.libraries.resolve(path)
        // Installer-produced libraries (Forge "client" jars) have an empty url: they already exist on disk.
        if (a.url.isNullOrBlank()) return null
        return DownloadItem(listOf(a.url), dest, a.sha1, a.size)
    }

    private fun mavenItem(lib: Library, path: String): DownloadItem {
        val base = (lib.url ?: "https://libraries.minecraft.net/").let { if (it.endsWith("/")) it else "$it/" }
        return DownloadItem(listOf(base + path), paths.libraries.resolve(path), lib.sha1, lib.size)
    }

    /** Classpath entries (libraries valid for this OS, minus natives-only libraries), in order. */
    fun classpath(rv: ResolvedVersion): List<Path> = rv.libraries.mapNotNull { lib -> classpathPath(lib)?.let { paths.libraries.resolve(it) } }

    private fun extractNatives(rv: ResolvedVersion) {
        val jars = ArrayList<Pair<Path, List<String>>>()
        for (lib in rv.libraries) {
            if (!Rules.allowed(lib.rules) || lib.natives == null) continue
            val c = classifierFor(lib) ?: continue
            val path = lib.downloads?.classifiers?.get(c)?.path ?: Maven.path(lib.name, c)
            jars += paths.libraries.resolve(path) to (listOf("META-INF/") + (lib.extract?.exclude ?: emptyList()))
        }
        val dir = paths.nativesDir(rv.id)
        if (jars.isEmpty()) {
            Files.createDirectories(dir)
            return
        }
        val stamp = sha1Hex(jars.joinToString("|") { (p, _) -> p.toString() + ":" + (if (Files.exists(p)) Files.size(p) else -1) }.toByteArray())
        val stampFile = dir.resolve(".obsi-natives")
        if (Files.isRegularFile(stampFile) && Files.readString(stampFile) == stamp) return
        Files.createDirectories(dir)
        for ((jar, exclude) in jars) {
            if (!Files.isRegularFile(jar)) continue
            ZipFile(jar.toFile()).use { zip ->
                for (e in zip.entries()) {
                    if (e.isDirectory || exclude.any { e.name.startsWith(it) }) continue
                    val target = dir.resolve(e.name).normalize()
                    if (!target.startsWith(dir)) continue // zip-slip guard
                    Files.createDirectories(target.parent)
                    zip.getInputStream(e).use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
                }
            }
        }
        writeTextAtomic(stampFile, stamp)
    }

    companion object {
        /**
         * Path (relative to libraries/) of the jar a library contributes to the classpath, or null.
         * A library with a `natives` map and no `downloads.artifact` is natives-only (LWJGL 2 era) and stays off the classpath;
         * one that has both (LWJGL 3.2.x) contributes its main jar and its natives are extracted separately.
         */
        fun classpathPath(lib: Library): String? {
            if (!Rules.allowed(lib.rules)) return null
            val art = lib.downloads?.artifact
            if (lib.natives != null && art == null) return null
            return art?.path ?: Maven.path(lib.name)
        }
    }
}

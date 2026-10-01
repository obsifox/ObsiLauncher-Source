package studio.obsifox.launcher.core.mojang

import kotlinx.coroutines.CancellationException
import studio.obsifox.launcher.core.LauncherPaths
import studio.obsifox.launcher.core.net.Http
import studio.obsifox.launcher.core.util.LauncherException
import studio.obsifox.launcher.core.util.RemoteJson
import studio.obsifox.launcher.core.util.writeTextAtomic
import java.nio.file.Files

class MojangApi(private val http: Http, private val paths: LauncherPaths) {
    @Volatile
    private var manifestCache: VersionManifest? = null

    /** The version manifest; falls back to the last cached copy when offline. */
    suspend fun manifest(force: Boolean = false): VersionManifest {
        manifestCache?.takeIf { !force }?.let { return it }
        val file = paths.cache.resolve("version_manifest_v2.json")
        val result = try {
            val text = http.getString(MANIFEST_URL)
            val parsed = RemoteJson.decodeFromString<VersionManifest>(text)
            runCatching { writeTextAtomic(file, text) }
            parsed
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (Files.isRegularFile(file)) RemoteJson.decodeFromString<VersionManifest>(Files.readString(file)) else throw e
        }
        manifestCache = result
        return result
    }

    /** Reads `versions/<id>/<id>.json`, downloading it from the manifest if it is a vanilla version we don't have yet. */
    suspend fun versionJson(id: String): VersionJson {
        val local = paths.versionJson(id)
        if (Files.isRegularFile(local)) {
            return RemoteJson.decodeFromString<VersionJson>(Files.readString(local))
        }
        val entry = manifest().versions.firstOrNull { it.id == id }
            ?: throw LauncherException("Unknown Minecraft version: $id")
        val text = http.getString(entry.url)
        val parsed = RemoteJson.decodeFromString<VersionJson>(text)
        writeTextAtomic(local, text)
        return parsed
    }

    fun hasLocalVersion(id: String): Boolean = Files.isRegularFile(paths.versionJson(id))

    companion object {
        const val MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    }
}

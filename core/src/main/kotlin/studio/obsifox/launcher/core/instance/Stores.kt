package studio.obsifox.launcher.core.instance

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import studio.obsifox.launcher.core.LauncherPaths
import studio.obsifox.launcher.core.util.StateJson
import studio.obsifox.launcher.core.util.writeTextAtomic
import java.nio.file.Files
import java.nio.file.Path
import java.text.Normalizer
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

class SettingsStore(private val paths: LauncherPaths) {
    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<Settings> = _flow.asStateFlow()
    val value: Settings get() = _flow.value

    private fun load(): Settings = try {
        if (paths.settingsFile.exists()) StateJson.decodeFromString<Settings>(Files.readString(paths.settingsFile)) else Settings()
    } catch (_: Exception) {
        Settings()
    }

    @Synchronized
    fun update(block: (Settings) -> Settings) {
        _flow.update(block)
        writeTextAtomic(paths.settingsFile, StateJson.encodeToString(Settings.serializer(), _flow.value))
    }
}

class InstanceStore(private val paths: LauncherPaths) {
    private val _flow = MutableStateFlow(loadAll())
    val flow: StateFlow<List<Instance>> = _flow.asStateFlow()
    val all: List<Instance> get() = _flow.value

    private fun loadAll(): List<Instance> {
        if (!paths.instances.isDirectory()) return emptyList()
        return Files.list(paths.instances).use { s ->
            s.filter { it.isDirectory() && it.resolve("instance.json").exists() }.toList()
        }.mapNotNull { dir ->
            try {
                StateJson.decodeFromString<Instance>(Files.readString(dir.resolve("instance.json")))
            } catch (_: Exception) {
                null
            }
        }.sortedByDescending { maxOf(it.lastPlayedAt, it.createdAt) }
    }

    fun get(id: String?): Instance? = id?.let { i -> all.firstOrNull { it.id == i } }

    /** A filesystem-safe, unique folder id for [name]. */
    fun newId(name: String): String {
        val ascii = Normalizer.normalize(name, Normalizer.Form.NFKD).replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').lowercase()
        val base = ascii.ifBlank { "instance" }.take(32)
        var id = base
        var n = 2
        while (Files.exists(paths.instanceDir(id))) id = "$base-${n++}"
        return id
    }

    @Synchronized
    fun save(instance: Instance) {
        Files.createDirectories(paths.gameDir(instance.id))
        writeTextAtomic(paths.instanceFile(instance.id), StateJson.encodeToString(Instance.serializer(), instance))
        _flow.update { list ->
            val i = list.indexOfFirst { it.id == instance.id }
            if (i >= 0) list.toMutableList().also { it[i] = instance } else listOf(instance) + list
        }
    }

    @Synchronized
    fun delete(id: String) {
        val dir = paths.instanceDir(id)
        if (Files.exists(dir)) deleteRecursively(dir)
        _flow.update { list -> list.filterNot { it.id == id } }
    }

    @Synchronized
    fun duplicate(id: String, newName: String): Instance? {
        val src = get(id) ?: return null
        val copy = src.copy(id = newId(newName), name = newName, createdAt = System.currentTimeMillis(), lastPlayedAt = 0, playTimeSeconds = 0)
        copyRecursively(paths.gameDir(src.id), paths.gameDir(copy.id))
        save(copy)
        return copy
    }

    companion object {
        fun deleteRecursively(dir: Path) {
            Files.walk(dir).use { s -> s.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }

        fun copyRecursively(from: Path, to: Path) {
            if (!Files.exists(from)) {
                Files.createDirectories(to)
                return
            }
            Files.walk(from).use { s ->
                s.forEach { p ->
                    val t = to.resolve(from.relativize(p).toString())
                    if (Files.isDirectory(p)) Files.createDirectories(t) else {
                        Files.createDirectories(t.parent)
                        Files.copy(p, t, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
        }
    }
}

package studio.obsifox.obsilauncher.core.instance

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import studio.obsifox.obsilauncher.core.ObsiSettings
import studio.obsifox.obsilauncher.core.Paths
import studio.obsifox.obsilauncher.core.game.VersionInstaller
import studio.obsifox.obsilauncher.core.loaders.LoaderType
import java.io.File
import java.util.UUID

/**
 * A playable profile: a version (vanilla or with a loader) plus custom settings.
 * The name is fully custom — players can create as many named instances as they
 * want from any version, with per-instance memory / JVM overrides.
 */
data class Instance(
    val id: String,
    val name: String,
    val versionId: String,
    val mcVersion: String,
    val loader: String = LoaderType.VANILLA.id,
    val loaderVersion: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val memoryMb: Int = 0,       // 0 = use global setting
    val javaArgs: String = "",   // "" = use global setting
) {
    val loaderType: LoaderType get() = LoaderType.byId(loader)
    val isCustom: Boolean get() = name != versionId
}

class InstancesStore(private val context: Context, private val settings: ObsiSettings) {

    private val file = File(Paths.obsiRoot(context), "instances.json")

    val instances = MutableStateFlow<List<Instance>>(emptyList())
    val activeId = MutableStateFlow("")

    init {
        load()
        migrateIfNeeded()
    }

    fun active(): Instance? = instances.value.firstOrNull { it.id == activeId.value }

    fun byVersion(versionId: String): Instance? = instances.value.firstOrNull { it.versionId == versionId }

    fun create(
        name: String,
        versionId: String,
        mcVersion: String,
        loader: LoaderType = LoaderType.VANILLA,
        loaderVersion: String? = null,
        select: Boolean = true,
    ): Instance {
        val clean = name.trim().ifEmpty { versionId }
        val existing = instances.value.firstOrNull { it.versionId == versionId }
        if (existing != null) {
            if (select) activeId.value = existing.id
            persist()
            return existing
        }
        val instance = Instance(
            id = UUID.randomUUID().toString(),
            name = clean,
            versionId = versionId,
            mcVersion = mcVersion,
            loader = loader.id,
            loaderVersion = loaderVersion,
        )
        instances.value = instances.value + instance
        if (select) activeId.value = instance.id
        persist()
        return instance
    }

    fun rename(id: String, name: String) {
        instances.value = instances.value.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it }
        persist()
    }

    fun update(id: String, transform: (Instance) -> Instance) {
        instances.value = instances.value.map { if (it.id == id) transform(it) else it }
        persist()
    }

    fun remove(id: String) {
        val target = instances.value.firstOrNull { it.id == id } ?: return
        instances.value = instances.value.filterNot { it.id == id }
        if (activeId.value == id) activeId.value = instances.value.firstOrNull()?.id.orEmpty()
        // deleting a custom instance keeps the underlying version files
        if (!VersionInstaller.versionJson(context, target.versionId).isFile.not()) {
            // version files exist; keep them (other instances may share the version)
        }
        persist()
    }

    fun setActive(id: String) {
        activeId.value = id
        persist()
        instances.value.firstOrNull { it.id == id }?.let { settings.selectedVersionValue = it.versionId }
    }

    /** One-time migration from 1.3.0's single selectedVersion. */
    private fun migrateIfNeeded() {
        if (instances.value.isNotEmpty()) return
        val legacy = settings.selectedVersionValue
        if (legacy.isNotBlank()) {
            val loader = when {
                legacy.contains("fabric", true) -> LoaderType.FABRIC
                legacy.contains("forge", true) -> LoaderType.FORGE
                legacy.contains("neoforge", true) -> LoaderType.NEOFORGE
                legacy.contains("quilt", true) -> LoaderType.QUILT
                legacy.contains("optifine", true) -> LoaderType.OPTIFINE
                else -> LoaderType.VANILLA
            }
            create(legacy, legacy, guessMcVersion(legacy), loader, select = true)
        }
    }

    private fun guessMcVersion(versionId: String): String {
        val direct = Regex("""^(\d+(?:\.\d+)+)""").find(versionId)?.groupValues?.get(1)
        if (direct != null && versionId.matches(Regex("""\d+(\.\d+)*"""))) return versionId
        val dash = versionId.substringBefore("-fabric").substringBefore("-forge").substringBefore("-OptiFine").substringBefore("-quilt")
        if (dash != versionId && dash.isNotBlank()) return dash
        if (versionId.startsWith("neoforge-")) {
            val parts = versionId.removePrefix("neoforge-").split('.')
            if (parts.size >= 2) return "1.${parts[0]}.${parts[1].substringBefore('.')}"
        }
        return direct ?: versionId
    }

    private fun load() {
        runCatching {
            if (!file.isFile) return
            val root = JSONObject(file.readText())
            val arr = root.optJSONArray("instances") ?: return
            val list = ArrayList<Instance>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list += Instance(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    versionId = o.optString("version_id"),
                    mcVersion = o.optString("mc_version"),
                    loader = o.optString("loader", LoaderType.VANILLA.id),
                    loaderVersion = o.optString("loader_version").takeIf { it.isNotEmpty() },
                    createdAt = o.optLong("created_at"),
                    memoryMb = o.optInt("memory_mb", 0),
                    javaArgs = o.optString("java_args"),
                )
            }
            instances.value = list
            activeId.value = root.optString("active").takeIf { id -> list.any { it.id == id } }
                ?: list.firstOrNull()?.id.orEmpty()
        }
    }

    private fun persist() {
        file.parentFile?.mkdirs()
        val root = JSONObject()
            .put("active", activeId.value)
            .put(
                "instances",
                JSONArray().apply {
                    instances.value.forEach {
                        put(
                            JSONObject()
                                .put("id", it.id)
                                .put("name", it.name)
                                .put("version_id", it.versionId)
                                .put("mc_version", it.mcVersion)
                                .put("loader", it.loader)
                                .put("loader_version", it.loaderVersion ?: "")
                                .put("created_at", it.createdAt)
                                .put("memory_mb", it.memoryMb)
                                .put("java_args", it.javaArgs),
                        )
                    }
                },
            )
        file.writeText(root.toString(2))
    }
}

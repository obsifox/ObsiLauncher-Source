package studio.obsifox.obsilauncher.core.game

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import studio.obsifox.obsilauncher.core.net.Http
import java.io.File

/** One entry of Mojang's version manifest. */
data class McVersion(
    val id: String,
    val type: String,          // release | snapshot | old_beta | old_alpha
    val url: String,
    val releaseTime: String,
) {
    val isOld: Boolean get() = type == "old_beta" || type == "old_alpha"
}

/**
 * Mojang piston-meta manifest, cached for 6 hours.
 * All version types are offered — releases, snapshots, old-beta and old-alpha.
 */
class VersionManifest(private val context: Context) {

    data class Latest(val release: String, val snapshot: String)

    val versions = MutableStateFlow<List<McVersion>>(emptyList())
    val latest = MutableStateFlow(Latest("", ""))
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    private val cacheFile = File(context.cacheDir, "version_manifest_v2.json")

    suspend fun refresh(force: Boolean = false) = withContext(Dispatchers.IO) {
        if (loading.value) return@withContext
        val fresh = force || !cacheFile.isFile ||
            System.currentTimeMillis() - cacheFile.lastModified() > 6L * 3600_000 ||
            versions.value.isEmpty()
        if (!fresh) return@withContext
        loading.value = true
        error.value = null
        try {
            val text = Http.get(MANIFEST_URL) // throws with the real reason when unreachable
            cacheFile.writeText(text)
            parse(text)
        } catch (e: Exception) {
            // fall back to any cached copy, however old
            if (cacheFile.isFile) {
                try {
                    parse(cacheFile.readText())
                } catch (_: Exception) {
                    error.value = e.message
                }
            } else {
                error.value = e.message
            }
        } finally {
            loading.value = false
        }
    }

    @Synchronized
    private fun parse(text: String) {
        val root = JSONObject(text)
        latest.value = Latest(
            release = root.optJSONObject("latest")?.optString("release").orEmpty(),
            snapshot = root.optJSONObject("latest")?.optString("snapshot").orEmpty(),
        )
        val arr: JSONArray = root.getJSONArray("versions")
        val list = ArrayList<McVersion>(arr.length())
        for (i in 0 until arr.length()) {
            val v = arr.getJSONObject(i)
            list += McVersion(
                id = v.getString("id"),
                type = v.optString("type", "release"),
                url = v.getString("url"),
                releaseTime = v.optString("releaseTime"),
            )
        }
        versions.value = list
    }

    fun releases(): List<McVersion> = versions.value.filter { it.type == "release" }
    fun snapshots(): List<McVersion> = versions.value.filter { it.type == "snapshot" }
    fun old(): List<McVersion> = versions.value.filter { it.isOld }

    private companion object {
        const val MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    }
}

package studio.obsifox.obsilauncher.core.accounts

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** A player profile. Offline-only by design: free, no Microsoft account needed. */
data class Account(
    val id: String,
    val name: String,
    val type: String = "offline", // reserved for future auth backends
) {
    /** Same UUID recipe the game itself uses for offline players. */
    fun offlineUuid(): String = UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray()).toString()
}

class AccountStore(context: Context) {

    private val file = File(context.filesDir, "obsi/accounts.json")

    val accounts = MutableStateFlow<List<Account>>(emptyList())
    val activeId = MutableStateFlow("")

    init {
        load()
    }

    fun active(): Account? = accounts.value.firstOrNull { it.id == activeId.value }

    fun add(name: String): Account {
        val trimmed = name.trim()
        require(trimmed.length in 3..16) { "name must be 3-16 characters" }
        require(trimmed.all { it.isLetterOrDigit() || it == '_' }) { "letters, digits and _ only" }
        val account = Account(
            id = UUID.randomUUID().toString(),
            name = trimmed,
        )
        val next = accounts.value.filterNot { it.name.equals(trimmed, ignoreCase = true) } + account
        accounts.value = next
        activeId.value = account.id
        persist()
        return account
    }

    fun setActive(id: String) {
        activeId.value = id
        persist()
    }

    fun remove(id: String) {
        accounts.value = accounts.value.filterNot { it.id == id }
        if (activeId.value == id) activeId.value = accounts.value.firstOrNull()?.id.orEmpty()
        persist()
    }

    private fun load() {
        runCatching {
            if (!file.isFile) return
            val root = JSONObject(file.readText())
            val arr: JSONArray = root.optJSONArray("accounts") ?: return
            val list = ArrayList<Account>(arr.length())
            for (i in 0 until arr.length()) {
                val a = arr.getJSONObject(i)
                list += Account(
                    id = a.optString("id"),
                    name = a.optString("name"),
                    type = a.optString("type", "offline"),
                )
            }
            accounts.value = list
            activeId.value = root.optString("active").takeIf { id -> list.any { acc -> acc.id == id } }
                ?: list.firstOrNull()?.id.orEmpty()
        }
    }

    private fun persist() {
        file.parentFile?.mkdirs()
        val root = JSONObject()
            .put("active", activeId.value)
            .put(
                "accounts",
                JSONArray().apply { accounts.value.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("type", it.type)) } },
            )
        file.writeText(root.toString(2))
    }
}

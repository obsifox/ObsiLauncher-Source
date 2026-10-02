package studio.obsifox.obsilauncher.core.accounts

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * A player profile. Offline profiles are the default (free, no Microsoft
 * account needed); Microsoft profiles use the free device-code flow.
 * [skinPath]/[capePath] power the local-only custom skin & cape feature —
 * they are applied client-side so only this player sees them.
 */
data class Account(
    val id: String,
    val name: String,
    val type: String = TYPE_OFFLINE,   // offline | microsoft
    val uuid: String = "",             // real uuid for Microsoft profiles
    val accessToken: String = "",
    val refreshToken: String = "",
    val tokenExpiresAt: Long = 0,
    val skinPath: String = "",
    val capePath: String = "",
    val skinModel: String = "",        // classic | slim (offline pick)
    val localSkinEnabled: Boolean = false,
) {
    /** Same UUID recipe the game itself uses for offline players. */
    fun offlineUuid(): String =
        if (type == TYPE_MICROSOFT && uuid.isNotEmpty()) uuid
        else UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray()).toString()

    val isMicrosoft: Boolean get() = type == TYPE_MICROSOFT

    companion object {
        const val TYPE_OFFLINE = "offline"
        const val TYPE_MICROSOFT = "microsoft"
    }
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
        val next = accounts.value.filterNot { it.name.equals(trimmed, ignoreCase = true) && it.type == Account.TYPE_OFFLINE } + account
        accounts.value = next
        activeId.value = account.id
        persist()
        return account
    }

    /** Insert / replace a Microsoft profile after a successful device-flow login. */
    fun upsertMicrosoft(name: String, uuid: String, accessToken: String, refreshToken: String, expiresAt: Long): Account {
        val existing = accounts.value.firstOrNull { it.type == Account.TYPE_MICROSOFT && it.uuid == uuid }
        val account = (existing ?: Account(UUID.randomUUID().toString(), name, Account.TYPE_MICROSOFT)).copy(
            name = name,
            uuid = uuid,
            accessToken = accessToken,
            refreshToken = refreshToken,
            tokenExpiresAt = expiresAt,
        )
        accounts.value = accounts.value.filterNot { it.id == account.id } + account
        activeId.value = account.id
        persist()
        return account
    }

    fun update(id: String, transform: (Account) -> Account) {
        accounts.value = accounts.value.map { if (it.id == id) transform(it) else it }
        persist()
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
                    type = a.optString("type", Account.TYPE_OFFLINE),
                    uuid = a.optString("uuid"),
                    accessToken = a.optString("access_token"),
                    refreshToken = a.optString("refresh_token"),
                    tokenExpiresAt = a.optLong("token_expires_at"),
                    skinPath = a.optString("skin_path"),
                    capePath = a.optString("cape_path"),
                    skinModel = a.optString("skin_model", "classic"),
                    localSkinEnabled = a.optBoolean("local_skin_enabled", false),
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
                JSONArray().apply {
                    accounts.value.forEach {
                        put(
                            JSONObject()
                                .put("id", it.id)
                                .put("name", it.name)
                                .put("type", it.type)
                                .put("uuid", it.uuid)
                                .put("access_token", it.accessToken)
                                .put("refresh_token", it.refreshToken)
                                .put("token_expires_at", it.tokenExpiresAt)
                                .put("skin_path", it.skinPath)
                                .put("cape_path", it.capePath)
                                .put("skin_model", it.skinModel)
                                .put("local_skin_enabled", it.localSkinEnabled),
                        )
                    }
                },
            )
        file.writeText(root.toString(2))
    }
}

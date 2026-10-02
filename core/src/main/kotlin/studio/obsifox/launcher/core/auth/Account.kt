package studio.obsifox.launcher.core.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import studio.obsifox.launcher.core.LauncherPaths
import studio.obsifox.launcher.core.util.StateJson
import studio.obsifox.launcher.core.util.writeTextAtomic
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

@Serializable
enum class AccountType { MICROSOFT, OFFLINE }

@Serializable
data class Account(
    val id: String,
    val type: AccountType,
    var username: String,
    /** Undashed UUID. */
    var uuid: String,
    var mcAccessToken: String? = null,
    /** Epoch millis when [mcAccessToken] expires. */
    var mcExpiresAt: Long = 0,
    var msRefreshToken: String? = null,
    var skinUrl: String? = null,
) {
    val isMicrosoft: Boolean get() = type == AccountType.MICROSOFT
}

/** Persists accounts to accounts.json (owner-only permissions where the OS supports it). */
class AccountStore(private val paths: LauncherPaths) {
    @Serializable
    private data class Db(val accounts: List<Account> = emptyList())

    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<List<Account>> = _flow.asStateFlow()
    val all: List<Account> get() = _flow.value

    private fun load(): List<Account> = try {
        if (Files.exists(paths.accountsFile)) StateJson.decodeFromString<Db>(Files.readString(paths.accountsFile)).accounts else emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    fun get(id: String?): Account? = id?.let { i -> all.firstOrNull { it.id == i } }

    @Synchronized
    fun upsert(account: Account) {
        _flow.update { list ->
            val i = list.indexOfFirst { it.id == account.id }
            if (i >= 0) list.toMutableList().also { it[i] = account } else list + account
        }
        persist()
    }

    @Synchronized
    fun remove(id: String) {
        _flow.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    private fun persist() {
        val text = StateJson.encodeToString(Db.serializer(), Db(_flow.value))
        writeTextAtomic(paths.accountsFile, text)
        try {
            Files.setPosixFilePermissions(paths.accountsFile, PosixFilePermissions.fromString("rw-------"))
        } catch (_: Exception) { /* non-POSIX file system (Windows) */ }
    }
}

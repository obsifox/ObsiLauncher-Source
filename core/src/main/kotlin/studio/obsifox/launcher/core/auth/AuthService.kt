package studio.obsifox.launcher.core.auth

import studio.obsifox.launcher.core.util.LauncherException
import studio.obsifox.launcher.core.util.offlineUuid
import studio.obsifox.launcher.core.util.undashed

/** Local accounts only - no Microsoft sign-in, no remote profile service. */
class AuthService(val store: AccountStore) {

    /** Offline accounts only need a player name (1-16 chars of A-Z a-z 0-9 _ ). */
    fun addOffline(name: String): Account {
        val n = name.trim()
        if (!isValidOfflineName(n)) throw LauncherException("Invalid name: use 1-16 letters, digits or underscores")
        val uuid = offlineUuid(n).undashed()
        val account = Account(id = "offline-$uuid", username = n, uuid = uuid)
        store.upsert(account)
        return account
    }

    companion object {
        private val nameRegex = Regex("^[A-Za-z0-9_]{1,16}$")
        fun isValidOfflineName(name: String) = nameRegex.matches(name)
    }
}

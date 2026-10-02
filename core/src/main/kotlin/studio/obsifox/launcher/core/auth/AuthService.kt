package studio.obsifox.launcher.core.auth

import studio.obsifox.launcher.core.net.Http
import studio.obsifox.launcher.core.util.LauncherException
import studio.obsifox.launcher.core.util.offlineUuid
import studio.obsifox.launcher.core.util.undashed

class AuthService(http: Http, val store: AccountStore, clientId: () -> String?) {
    val microsoft = MicrosoftAuth(http, clientId)

    /** Offline accounts only need a player name (1-16 chars of A-Z a-z 0-9 _ ). */
    fun addOffline(name: String): Account {
        val n = name.trim()
        if (!isValidOfflineName(n)) throw LauncherException("Invalid name: use 1-16 letters, digits or underscores")
        val uuid = offlineUuid(n).undashed()
        val account = Account(id = "offline-$uuid", type = AccountType.OFFLINE, username = n, uuid = uuid)
        store.upsert(account)
        return account
    }

    /** Returns an account whose Minecraft token is valid for at least a few more minutes (refreshing it if necessary). */
    suspend fun ensureFresh(account: Account): Account {
        if (!account.isMicrosoft) return account
        if (account.mcAccessToken != null && account.mcExpiresAt - System.currentTimeMillis() > 5 * 60_000L) return account
        val refreshed = microsoft.refresh(account)
        store.upsert(refreshed)
        return refreshed
    }

    companion object {
        private val nameRegex = Regex("^[A-Za-z0-9_]{1,16}$")
        fun isValidOfflineName(name: String) = nameRegex.matches(name)
    }
}

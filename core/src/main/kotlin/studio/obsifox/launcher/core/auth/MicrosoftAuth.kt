package studio.obsifox.launcher.core.auth

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import studio.obsifox.launcher.core.net.Http
import studio.obsifox.launcher.core.net.HttpException
import studio.obsifox.launcher.core.util.LauncherException
import studio.obsifox.launcher.core.util.RemoteJson

/** What the user must do to finish the device-code sign-in. */
data class DeviceCodeInfo(
    val userCode: String,
    val verificationUri: String,
    val deviceCode: String,
    val intervalSec: Int,
    val expiresInSec: Int,
)

enum class AuthProblem {
    NOT_CONFIGURED, DECLINED, EXPIRED, NO_XBOX_ACCOUNT, XBOX_UNAVAILABLE_IN_COUNTRY, ADULT_VERIFICATION, CHILD_ACCOUNT,
    NO_MINECRAFT, APP_NOT_APPROVED, OTHER,
}

class AuthException(val problem: AuthProblem, message: String, cause: Throwable? = null) : LauncherException(message, cause)

/**
 * Microsoft -> Xbox Live -> XSTS -> Minecraft services, using the OAuth device-code flow (works on every desktop,
 * no embedded browser and no redirect URI needed).
 *
 * Requires an Azure application (client) id that Mojang has approved for the Minecraft API. See docs/SETUP.md.
 */
class MicrosoftAuth(private val http: Http, private val clientId: () -> String?) {

    private fun cid(): String = clientId()?.trim().takeUnless { it.isNullOrEmpty() }
        ?: throw AuthException(AuthProblem.NOT_CONFIGURED, "Microsoft sign-in is not configured: no Azure client id (see docs/SETUP.md)")

    suspend fun startDeviceCode(): DeviceCodeInfo {
        val r = http.postForm("$AUTHORITY/devicecode", mapOf("client_id" to cid(), "scope" to SCOPE))
        val o = RemoteJson.parseToJsonElement(r.body.ifBlank { "{}" }).jsonObject
        if (r.status !in 200..299) throw AuthException(AuthProblem.OTHER, "Device code request failed: " + (o.s("error_description") ?: o.s("error") ?: r.status.toString()))
        return DeviceCodeInfo(
            userCode = o.s("user_code") ?: error("no user_code"),
            verificationUri = o.s("verification_uri") ?: "https://www.microsoft.com/link",
            deviceCode = o.s("device_code") ?: error("no device_code"),
            intervalSec = o["interval"]?.jsonPrimitive?.intOrNull ?: 5,
            expiresInSec = o["expires_in"]?.jsonPrimitive?.intOrNull ?: 900,
        )
    }

    /** Polls until the user finishes (or the code expires), then performs the whole Xbox/Minecraft chain. */
    suspend fun awaitLogin(info: DeviceCodeInfo): Account {
        var interval = info.intervalSec.coerceAtLeast(2)
        val deadline = System.currentTimeMillis() + info.expiresInSec * 1000L
        while (System.currentTimeMillis() < deadline) {
            currentCoroutineContext().ensureActive()
            delay(interval * 1000L)
            val r = http.postForm(
                "$AUTHORITY/token",
                mapOf("grant_type" to "urn:ietf:params:oauth:grant-type:device_code", "client_id" to cid(), "device_code" to info.deviceCode),
            )
            val o = RemoteJson.parseToJsonElement(r.body.ifBlank { "{}" }).jsonObject
            if (r.status in 200..299) {
                val access = o.s("access_token") ?: error("no access_token")
                return completeLogin(access, o.s("refresh_token"))
            }
            when (o.s("error")) {
                "authorization_pending" -> {}
                "slow_down" -> interval += 5
                "authorization_declined" -> throw AuthException(AuthProblem.DECLINED, "Sign-in was declined")
                "expired_token", "bad_verification_code" -> throw AuthException(AuthProblem.EXPIRED, "The sign-in code expired")
                else -> throw AuthException(AuthProblem.OTHER, o.s("error_description") ?: "Sign-in failed (${o.s("error")})")
            }
        }
        throw AuthException(AuthProblem.EXPIRED, "The sign-in code expired")
    }

    suspend fun refresh(account: Account): Account {
        val rt = account.msRefreshToken ?: throw AuthException(AuthProblem.EXPIRED, "No refresh token; please sign in again")
        val r = http.postForm(
            "$AUTHORITY/token",
            mapOf("grant_type" to "refresh_token", "client_id" to cid(), "refresh_token" to rt, "scope" to SCOPE),
        )
        val o = RemoteJson.parseToJsonElement(r.body.ifBlank { "{}" }).jsonObject
        if (r.status !in 200..299) {
            throw AuthException(AuthProblem.EXPIRED, "Session expired, please sign in again (${o.s("error") ?: r.status})")
        }
        return completeLogin(o.s("access_token") ?: error("no access_token"), o.s("refresh_token") ?: rt, account)
    }

    private suspend fun completeLogin(msAccessToken: String, msRefreshToken: String?, existing: Account? = null): Account {
        // 1. Xbox Live user token
        val xbl = postJsonOrThrow(
            "https://user.auth.xboxlive.com/user/authenticate",
            buildJsonObject {
                putJsonObject("Properties") {
                    put("AuthMethod", "RPS")
                    put("SiteName", "user.auth.xboxlive.com")
                    put("RpsTicket", "d=$msAccessToken")
                }
                put("RelyingParty", "http://auth.xboxlive.com")
                put("TokenType", "JWT")
            }.toString(),
        )
        val xblToken = xbl.s("Token") ?: error("no XBL token")
        val uhs = xbl["DisplayClaims"]?.jsonObject?.get("xui")?.jsonArray?.firstOrNull()?.jsonObject?.s("uhs") ?: error("no uhs")

        // 2. XSTS token for the Minecraft services
        val xsts = postJsonOrThrow(
            "https://xsts.auth.xboxlive.com/xsts/authorize",
            buildJsonObject {
                putJsonObject("Properties") {
                    put("SandboxId", "RETAIL")
                    putJsonArray("UserTokens") { add(JsonPrimitive(xblToken)) }
                }
                put("RelyingParty", "rp://api.minecraftservices.com/")
                put("TokenType", "JWT")
            }.toString(),
        )
        val xstsToken = xsts.s("Token") ?: error("no XSTS token")

        // 3. Minecraft access token
        val mc = postJsonOrThrow(
            "https://api.minecraftservices.com/authentication/login_with_xbox",
            buildJsonObject { put("identityToken", "XBL3.0 x=$uhs;$xstsToken") }.toString(),
        )
        val mcToken = mc.s("access_token") ?: error("no minecraft token")
        val expiresIn = mc["expires_in"]?.jsonPrimitive?.longOrNull ?: 86400L

        // 4. Profile (this is also the real "does the user own the game" check)
        val profileResp = http.raw("GET", "https://api.minecraftservices.com/minecraft/profile", mapOf("Authorization" to "Bearer $mcToken"))
        if (profileResp.status == 404) throw AuthException(AuthProblem.NO_MINECRAFT, "This Microsoft account does not own Minecraft: Java Edition")
        if (profileResp.status == 403) throw AuthException(AuthProblem.APP_NOT_APPROVED, "Mojang has not approved this application's client id for the Minecraft API yet (see docs/SETUP.md)")
        if (profileResp.status !in 200..299) throw AuthException(AuthProblem.OTHER, "Profile request failed (HTTP ${profileResp.status})")
        val profile = RemoteJson.parseToJsonElement(profileResp.body).jsonObject
        val uuid = profile.s("id") ?: error("no profile id")
        val name = profile.s("name") ?: error("no profile name")
        val skin = profile["skins"]?.jsonArray?.map { it.jsonObject }
            ?.let { skins -> skins.firstOrNull { it.s("state") == "ACTIVE" } ?: skins.firstOrNull() }?.s("url")

        return (existing ?: Account(id = uuid, type = AccountType.MICROSOFT, username = name, uuid = uuid)).copy(
            username = name,
            uuid = uuid,
            mcAccessToken = mcToken,
            mcExpiresAt = System.currentTimeMillis() + (expiresIn - 120).coerceAtLeast(60) * 1000,
            msRefreshToken = msRefreshToken ?: existing?.msRefreshToken,
            skinUrl = skin,
        )
    }

    private suspend fun postJsonOrThrow(url: String, body: String): JsonObject {
        val r = http.raw("POST", url, mapOf("Accept" to "application/json"), body, "application/json")
        val o = runCatching { RemoteJson.parseToJsonElement(r.body.ifBlank { "{}" }).jsonObject }.getOrDefault(JsonObject(emptyMap()))
        if (r.status in 200..299) return o
        val xerr = o["XErr"]?.jsonPrimitive?.longOrNull
        throw when (xerr) {
            2148916233L -> AuthException(AuthProblem.NO_XBOX_ACCOUNT, "This Microsoft account has no Xbox profile yet. Sign in once at xbox.com to create one.")
            2148916235L -> AuthException(AuthProblem.XBOX_UNAVAILABLE_IN_COUNTRY, "Xbox Live is not available in your country/region.")
            2148916236L, 2148916237L -> AuthException(AuthProblem.ADULT_VERIFICATION, "The account needs adult verification on the Xbox site.")
            2148916238L -> AuthException(AuthProblem.CHILD_ACCOUNT, "This is a child account; add it to a Microsoft family to play.")
            else -> AuthException(AuthProblem.OTHER, "Authentication failed at $url (HTTP ${r.status}${xerr?.let { ", XErr $it" } ?: ""})", HttpException(r.status, url, r.body))
        }
    }

    private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull

    companion object {
        const val AUTHORITY = "https://login.microsoftonline.com/consumers/oauth2/v2.0"
        const val SCOPE = "XboxLive.signin offline_access"
    }
}

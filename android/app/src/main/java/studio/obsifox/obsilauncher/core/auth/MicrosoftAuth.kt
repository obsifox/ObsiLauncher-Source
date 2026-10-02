package studio.obsifox.obsilauncher.core.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import studio.obsifox.obsilauncher.core.net.Http

/**
 * Microsoft account login via the OAuth2 device-code flow — free, in-app, no
 * keys or gates (the public Minecraft launcher client id is used).
 * Chain: MSA device code -> Xbox Live (XBL) -> XSTS -> Minecraft -> profile.
 */
class MicrosoftAuth {

    data class DeviceCode(val userCode: String, val verificationUrl: String, val deviceCode: String, val interval: Int)

    data class Profile(
        val id: String,
        val name: String,
        val accessToken: String,       // minecraft access token
        val refreshToken: String,
        val expiresAtMs: Long,
        val skinUrl: String?,
        val skinModel: String?,
    )

    /**
     * Step 1: request a device code. Show [DeviceCode.userCode] and
     * [DeviceCode.verificationUrl] to the user, then poll [login].
     */
    fun beginDeviceCode(): DeviceCode {
        val body = JSONObject().put("client_id", CLIENT_ID)
            .put("scope", "XboxLive.signin offline_access").toString()
        val text = Http.postJson("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode", body)
            ?: throw IllegalStateException("Microsoft login service unreachable")
        val root = JSONObject(text)
        return DeviceCode(
            userCode = root.getString("user_code"),
            verificationUrl = root.optString("verification_uri", "https://www.microsoft.com/link"),
            deviceCode = root.getString("device_code"),
            interval = root.optInt("interval", 5),
        )
    }

    /**
     * Step 2: poll until the user confirms. Returns the full profile or throws
     * with a readable message ("authorization_pending" keeps polling internally
     * until [timeoutMs]).
     */
    suspend fun login(device: DeviceCode, timeoutMs: Long = 15 * 60_000L): Profile = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var msaTokens: JSONObject? = null
        while (System.currentTimeMillis() < deadline) {
            delay(device.interval * 1000L)
            val body = JSONObject()
                .put("client_id", CLIENT_ID)
                .put("device_code", device.deviceCode)
                .put("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .toString()
            val text = Http.postJson("https://login.microsoftonline.com/consumers/oauth2/v2.0/token", body)
                ?: continue
            val root = JSONObject(text)
            val error = root.optString("error")
            when {
                error.isEmpty() -> { msaTokens = root; break }
                error == "authorization_pending" -> continue
                error == "slow_down" -> { delay(3000); continue }
                else -> throw IllegalStateException(root.optString("error_description", error))
            }
        }
        val msa = msaTokens ?: throw IllegalStateException("timed out waiting for Microsoft login")
        val refreshToken = msa.optString("refresh_token")
        val xbl = xblAuthenticate(msa.getString("access_token"))
        val xsts = xstsAuthorize(xbl.token)
        val mcToken = minecraftLoginWithXsts(xsts.token, xsts.uhs ?: throw IllegalStateException("XSTS: missing user hash"))
        val profile = minecraftProfile(mcToken)
        Profile(
            id = profile.first,
            name = profile.second,
            accessToken = mcToken,
            refreshToken = refreshToken,
            expiresAtMs = System.currentTimeMillis() + 24 * 3600_000L,
            skinUrl = profile.third?.first,
            skinModel = profile.third?.second,
        )
    }

    /** Refresh an expired Minecraft token from the stored refresh token. */
    suspend fun refresh(refreshToken: String): Profile = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("client_id", CLIENT_ID)
            .put("grant_type", "refresh_token")
            .put("refresh_token", refreshToken)
            .toString()
        val text = Http.postJson("https://login.microsoftonline.com/consumers/oauth2/v2.0/token", body)
            ?: throw IllegalStateException("Microsoft login service unreachable")
        val root = JSONObject(text)
        val newRefresh = root.optString("refresh_token").ifEmpty { refreshToken }
        val xbl = xblAuthenticate(root.getString("access_token"))
        val xsts = xstsAuthorize(xbl.token)
        val mcToken = minecraftLoginWithXsts(xsts.token, xsts.uhs ?: throw IllegalStateException("XSTS: missing user hash"))
        val profile = minecraftProfile(mcToken)
        Profile(profile.first, profile.second, mcToken, newRefresh, System.currentTimeMillis() + 24 * 3600_000L, profile.third?.first, profile.third?.second)
    }

    private data class Token(val token: String, val uhs: String?)

    private fun xblAuthenticate(msaAccessToken: String): Token {
        val body = JSONObject().put(
            "Properties",
            JSONObject()
                .put("AuthMethod", "RPS")
                .put("SiteName", "user.auth.xboxlive.com")
                .put("RpsTicket", "d=$msaAccessToken"),
        ).put("RelyingParty", "http://auth.xboxlive.com").put("TokenType", "JWT").toString()
        val text = Http.postJson("https://user.auth.xboxlive.com/user/authenticate", body)
            ?: throw IllegalStateException("Xbox Live unreachable")
        val root = JSONObject(text)
        val uhs = root.optJSONObject("DisplayClaims")?.optJSONArray("xui")?.optJSONObject(0)?.optString("uhs")
        return Token(root.getString("Token"), uhs)
    }

    private fun xstsAuthorize(xblToken: String): Token {
        val body = JSONObject().put(
            "Properties",
            JSONObject().put("SandboxId", "RETAIL").put(
                "UserTokens",
                org.json.JSONArray().put(xblToken),
            ),
        ).put("RelyingParty", "rp://api.minecraftservices.com/").put("TokenType", "JWT").toString()
        val text = Http.postJson("https://xsts.auth.xboxlive.com/xsts/authorize", body)
            ?: throw IllegalStateException("XSTS unreachable")
        val root = JSONObject(text)
        val uhs = root.optJSONObject("DisplayClaims")?.optJSONArray("xui")?.optJSONObject(0)?.optString("uhs")
            ?: throw IllegalStateException("XSTS: no user claims (is this account age-verified?)")
        return Token(root.getString("Token"), uhs)
    }

    private fun minecraftLoginWithXsts(xstsToken: String, uhs: String): String {
        val body = JSONObject().put("identityToken", "XBL3.0 x=$uhs;$xstsToken").toString()
        val text = Http.postJson("https://api.minecraftservices.com/authentication/login_with_xbox", body)
            ?: throw IllegalStateException("Minecraft services unreachable")
        return JSONObject(text).getString("access_token")
    }

    private fun minecraftProfile(mcToken: String): Triple<String, String, Pair<String, String>?> {
        val text = Http.authedJson("https://api.minecraftservices.com/minecraft/profile", mcToken)
            ?: throw IllegalStateException("cannot load the Minecraft profile")
        val root = JSONObject(text)
        if (root.has("error") || root.optString("name").isEmpty()) {
            throw IllegalStateException("this Microsoft account does not own Minecraft")
        }
        val skins = root.optJSONArray("skins")
        var skinUrl: String? = null; var model: String? = null
        if (skins != null) {
            for (i in 0 until skins.length()) {
                val s = skins.getJSONObject(i)
                if (s.optString("state") == "ACTIVE") {
                    skinUrl = s.optString("url")
                    model = s.optString("model", "classic")
                }
            }
        }
        return Triple(root.getString("id"), root.getString("name"), skinUrl?.let { it to (model ?: "classic") })
    }

    companion object {
        // Public client id of the official Minecraft launcher family — no secret, no key.
        const val CLIENT_ID = "00000000402b5328"
    }
}

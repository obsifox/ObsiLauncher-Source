package studio.obsifox.obsilauncher.core.cosmetics

import android.content.Context
import studio.obsifox.obsilauncher.core.Paths
import studio.obsifox.obsilauncher.core.accounts.Account
import java.io.File

/**
 * Local-only custom skins & capes.
 *
 * The skin/cape files live inside the launcher's own storage and are applied
 * client-side through CustomSkinLoader's LocalSkin provider, so ONLY this
 * player sees them — other players in the world still see the default skin.
 * (No server-side changes, nothing is uploaded.)
 */
object SkinManager {

    fun skinsRoot(context: Context): File = File(Paths.obsiRoot(context), "skins")

    fun skinFile(context: Context, accountId: String): File = File(skinsRoot(context), "$accountId-skin.png")
    fun capeFile(context: Context, accountId: String): File = File(skinsRoot(context), "$accountId-cape.png")

    /** Copy a picked image into the account's slot. Returns the stored path. */
    fun store(context: Context, accountId: String, kind: String, bytes: ByteArray): String {
        val dest = if (kind == "cape") capeFile(context, accountId) else skinFile(context, accountId)
        dest.parentFile?.mkdirs()
        dest.writeBytes(bytes)
        return dest.absolutePath
    }

    fun clear(context: Context, accountId: String, kind: String) {
        (if (kind == "cape") capeFile(context, accountId) else skinFile(context, accountId)).delete()
    }

    /**
     * Apply the local skin/cape into the instance's game directory using
     * CustomSkinLoader's LocalSkin folder. Call before every launch when
     * [Account.localSkinEnabled] is set.
     *
     * @return a user-facing warning, or null when everything is in place
     */
    fun applyToGameDir(context: Context, account: Account, gameDir: File): String? {
        if (!account.localSkinEnabled) return null
        val skin = skinFile(context, account.id)
        val cape = capeFile(context, account.id)
        if (!skin.isFile && !cape.isFile) return null
        val cslDir = File(gameDir, "CustomSkinLoader/LocalSkin")
        cslDir.mkdirs()
        if (skin.isFile) {
            skin.copyTo(File(cslDir, "${account.name}.png"), overwrite = true)
            // wide/slim model hint via the .skin info file CustomSkinLoader reads
            val model = if (account.skinModel == "slim") "slim" else "default"
            File(cslDir, "${account.name}.skin").writeText(model)
        }
        if (cape.isFile) {
            val capeDir = File(gameDir, "CustomSkinLoader/LocalCape")
            capeDir.mkdirs()
            cape.copyTo(File(capeDir, "${account.name}.png"), overwrite = true)
        }
        return null
    }

    /**
     * True when CustomSkinLoader is installed in the instance (its LocalSkin
     * provider is what renders the custom skin client-side).
     */
    fun customSkinLoaderInstalled(context: Context, gameDir: File): Boolean {
        val modsDir = File(gameDir, "mods")
        if (modsDir.listFiles()?.any { it.name.contains("customskinloader", ignoreCase = true) } == true) return true
        // also check the manifest
        val manifest = File(gameDir, "obsi-content.json")
        if (manifest.isFile && manifest.readText().contains("customskinloader", ignoreCase = true)) return true
        return false
    }

    /** The Modrinth project to install when CustomSkinLoader is missing. */
    const val CSL_PROJECT_HINT = "customskinloader"
}

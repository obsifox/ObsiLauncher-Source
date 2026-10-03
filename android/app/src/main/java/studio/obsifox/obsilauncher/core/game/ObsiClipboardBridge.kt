package studio.obsifox.obsilauncher.core.game

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Clipboard bridge used by the vendored org.lwjgl.glfw.CallbackBridge so the
 * JVM-side LWJGL fork can read/write the Android clipboard.
 * GPL-3.0-or-later — ObsiLauncher port.
 */
object ObsiClipboardBridge {
    @JvmField
    @Volatile
    var CLIPBOARD: ClipboardManager? = null

    fun attach(context: Context) {
        CLIPBOARD = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }

    @JvmStatic
    fun openLink(link: String) {
        runCatching {
            val ctx = CLIPBOARD?.let { null } // no direct context held; fallback below
            null
        }
        // opened through the static app context, see App.kt
        val app = AppContextHolder.get() ?: return
        runCatching {
            app.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/** Tiny app-context holder shared by the vendored glue classes. */
object AppContextHolder {
    @Volatile
    private var appContext: Context? = null

    @JvmStatic
    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    @JvmStatic
    fun get(): Context? = appContext
}

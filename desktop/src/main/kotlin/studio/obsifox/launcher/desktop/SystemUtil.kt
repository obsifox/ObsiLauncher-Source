package studio.obsifox.launcher.desktop

import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** Thin wrappers over AWT / the OS shell (every call is best effort and never throws). */
object SystemOpen {
    fun open(path: Path) {
        try { Files.createDirectories(path) } catch (_: Exception) {}
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(path.toFile())
            } else fallback(path.toString())
        }.onFailure { runCatching { fallback(path.toString()) } }
    }

    fun browse(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
            } else fallback(url)
        }.onFailure { runCatching { fallback(url) } }
    }

    private fun fallback(target: String) {
        val os = System.getProperty("os.name", "").lowercase()
        val cmd = when {
            "win" in os -> listOf("rundll32", "url.dll,FileProtocolHandler", target)
            "mac" in os -> listOf("open", target)
            else -> listOf("xdg-open", target)
        }
        ProcessBuilder(cmd).inheritIO().start()
    }
}

fun pickImageFile(title: String): Path? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    dialog.setFilenameFilter { _, name -> name.endsWith(".png", true) || name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) || name.endsWith(".webp", true) }
    dialog.isVisible = true
    val file = dialog.file ?: return null
    return Paths.get(dialog.directory ?: "", file)
}

/** Best-effort exclusive fullscreen on the first AWT window (Compose windows are AWT frames under the hood). */
fun setFullscreen(on: Boolean) {
    runCatching {
        val device = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
        val win = java.awt.Window.getWindows().firstOrNull { it.isShowing } ?: return
        val frame = win as? java.awt.Frame
        if (on) {
            if (!device.isFullScreenSupported) frame?.extendedState = java.awt.Frame.MAXIMIZED_BOTH
            else device.fullScreenWindow = win
        } else {
            if (device.fullScreenWindow != null) device.fullScreenWindow = null
            frame?.extendedState = java.awt.Frame.NORMAL
        }
    }
}

fun copyToClipboard(text: String) {
    runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
}

/** Native file picker for a single file with the given [extension] (without dot). Blocks until the user answers. */
fun pickFile(title: String, extension: String): Path? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    dialog.setFilenameFilter { _, name -> name.endsWith(".$extension", ignoreCase = true) }
    dialog.isVisible = true
    val file = dialog.file ?: return null
    return Paths.get(dialog.directory ?: "", file)
}

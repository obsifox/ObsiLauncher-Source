package studio.obsifox.launcher.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import studio.obsifox.launcher.core.LauncherCore
import studio.obsifox.launcher.core.LauncherPaths
import studio.obsifox.launcher.core.util.Platform
import java.awt.Dimension

fun main() {
    val build = BuildInfo.load()
    val core = LauncherCore(LauncherPaths(Platform.defaultDataDir()), build.version)
    val app = AppController(core, build)
    val icon = runCatching { BitmapPainter(useResource("icon.png", ::loadImageBitmap)) }.getOrNull()

    application {
        val windowState = rememberWindowState(size = DpSize(1180.dp, 760.dp))
        val sessions by app.sessions.collectAsState()
        val settings by core.settings.flow.collectAsState()

        // optionally get out of the way while the game is running
        LaunchedEffect(sessions.isNotEmpty(), settings.hideLauncherWhileRunning) {
            windowState.isMinimized = sessions.isNotEmpty() && settings.hideLauncherWhileRunning
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = "ObsiLauncher",
            icon = icon,
            state = windowState,
        ) {
            window.minimumSize = Dimension(980, 640)
            App(app)
        }
    }
}

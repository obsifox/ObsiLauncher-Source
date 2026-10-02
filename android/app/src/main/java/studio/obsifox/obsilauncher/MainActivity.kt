package studio.obsifox.obsilauncher

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import studio.obsifox.obsilauncher.core.game.GameState
import studio.obsifox.obsilauncher.obsi.ObsiWallpaperLayer
import studio.obsifox.obsilauncher.ui.ObsiApp
import studio.obsifox.obsilauncher.ui.theme.ObsiTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        // in-app language override: system | en | fa
        val prefs = newBase.getSharedPreferences("obsi_settings", android.content.Context.MODE_PRIVATE)
        val lang = prefs.getString("language", "system") ?: "system"
        super.attachBaseContext(
            if (lang == "system") newBase
            else {
                val config = android.content.res.Configuration(newBase.resources.configuration)
                config.setLocale(java.util.Locale.forLanguageTag(lang))
                val ctx = newBase.createConfigurationContext(config)
                // also applies to layout direction (FA = RTL)
                ctx
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as App

        setContent {
            val themeMode by app.settings.themeMode.collectAsState()
            val accent by app.wallpaper.accent.collectAsState()
            val blur by app.settings.blur.collectAsState()
            val active by app.wallpaper.active.collectAsState()
            val gameState by app.gameManager.state.collectAsState()

            // keep the screen on while the game runs
            window.addFlags(
                if (gameState == GameState.RUNNING || gameState == GameState.PREPARING) {
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                } else {
                    0
                },
            )

            Box(modifier = Modifier.fillMaxSize()) {
                ObsiWallpaperLayer(wallpaper = app.wallpaper, blurPx = blur, modifier = Modifier.fillMaxSize())
                ObsiTheme(mode = themeMode, dynamicAccent = androidx.compose.ui.graphics.Color(accent)) {
                    ObsiApp()
                }
            }
        }
    }

    override fun onDestroy() {
        // the game is a child process; stop it with the launcher
        (application as App).gameManager.stop()
        super.onDestroy()
    }
}

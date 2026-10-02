package studio.obsifox.obsilauncher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import studio.obsifox.obsilauncher.R
import studio.obsifox.obsilauncher.app
import studio.obsifox.obsilauncher.core.game.GameState
import studio.obsifox.obsilauncher.ui.screens.AboutScreen
import studio.obsifox.obsilauncher.ui.screens.AccountsScreen
import studio.obsifox.obsilauncher.ui.screens.ConsoleScreen
import studio.obsifox.obsilauncher.ui.screens.HomeScreen
import studio.obsifox.obsilauncher.ui.screens.SettingsScreen
import studio.obsifox.obsilauncher.ui.screens.VersionsScreen
import studio.obsifox.obsilauncher.ui.theme.LocalObsi
import studio.obsifox.obsilauncher.ui.screens.stringResourceCompat

enum class Screen(val titleRes: Int) {
    HOME(R.string.nav_home),
    VERSIONS(R.string.nav_versions),
    ACCOUNTS(R.string.nav_accounts),
    CONSOLE(R.string.nav_console),
    SETTINGS(R.string.nav_settings),
    ABOUT(R.string.nav_about),
}

@Composable
fun ObsiApp() {
    val context = LocalContext.current
    val app = context.app
    val obsi = LocalObsi.current
    var screen by remember { mutableStateOf(Screen.HOME) }

    val selected by app.settings.selectedVersion.collectAsState()
    val blur by app.settings.blur.collectAsState()
    val gameState by app.gameManager.state.collectAsState()
    val wallpaperActive by app.wallpaper.active.collectAsState()

    // wallpaper follows the selected version
    LaunchedEffect(selected) {
        app.wallpaper.sync(selected.ifBlank { null })
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = Color.Transparent) {
                Screen.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = screen == entry,
                        onClick = { screen = entry },
                        icon = {
                            Text(
                                text = screenIcon(entry),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                        label = { Text(stringResource(entry.titleRes), style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = obsi.accent,
                            selectedTextColor = obsi.accent,
                            indicatorColor = obsi.accentDim,
                            unselectedIconColor = obsi.textDim,
                            unselectedTextColor = obsi.textDim,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (screen) {
                        Screen.HOME -> HomeScreen(
                            onPlay = {
                                val version = app.settings.selectedVersionValue
                                val account = app.accounts.active()
                                val pack = app.runtimePacks.packByName(app.settings.runtimePackValue)
                                if (version.isNotBlank() && account != null && pack != null) {
                                    app.gameManager.launch(context, version, account, pack)
                                    screen = Screen.CONSOLE
                                }
                            },
                            onPickVersion = { screen = Screen.VERSIONS },
                            onPickAccount = { screen = Screen.ACCOUNTS },
                        )
                        Screen.VERSIONS -> VersionsScreen()
                        Screen.ACCOUNTS -> AccountsScreen()
                        Screen.CONSOLE -> ConsoleScreen()
                        Screen.SETTINGS -> SettingsScreen()
                        Screen.ABOUT -> AboutScreen()
                    }
                }
            }
        }
    }
}

private fun screenIcon(screen: Screen): String = when (screen) {
    Screen.HOME -> "⌂"
    Screen.VERSIONS -> "❖"
    Screen.ACCOUNTS -> "☻"
    Screen.CONSOLE -> "▤"
    Screen.SETTINGS -> "⚙"
    Screen.ABOUT -> "✦"
}

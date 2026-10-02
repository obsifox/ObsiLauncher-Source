package studio.obsifox.obsilauncher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import studio.obsifox.obsilauncher.R
import studio.obsifox.obsilauncher.app
import studio.obsifox.obsilauncher.ui.screens.AboutScreen
import studio.obsifox.obsilauncher.ui.screens.AccountsScreen
import studio.obsifox.obsilauncher.ui.screens.BrowseScreen
import studio.obsifox.obsilauncher.ui.screens.ConsoleScreen
import studio.obsifox.obsilauncher.ui.screens.HomeScreen
import studio.obsifox.obsilauncher.ui.screens.InstanceDetailScreen
import studio.obsifox.obsilauncher.ui.screens.SettingsScreen
import studio.obsifox.obsilauncher.ui.screens.VersionsScreen
import studio.obsifox.obsilauncher.ui.screens.stringResourceCompat
import studio.obsifox.obsilauncher.ui.theme.LocalObsi

enum class Screen(val titleRes: Int) {
    HOME(R.string.nav_home),
    VERSIONS(R.string.nav_versions),
    BROWSE(R.string.nav_browse),
    ACCOUNTS(R.string.nav_accounts),
    SETTINGS(R.string.nav_settings),
    ABOUT(R.string.nav_about),
}

/** Secondary destinations on top of the tab bar. */
sealed class Overlay {
    data object None : Overlay()
    data object Console : Overlay()
    data object InstanceDetail : Overlay()
}

@Composable
fun ObsiApp() {
    val context = LocalContext.current
    val app = context.app
    val obsi = LocalObsi.current
    var screen by remember { mutableStateOf(Screen.HOME) }
    var overlay by remember { mutableStateOf<Overlay>(Overlay.None) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val selected by app.settings.selectedVersion.collectAsState()
    val gameState by app.gameManager.state.collectAsState()
    val wallpaperActive by app.wallpaper.active.collectAsState()

    // wallpaper follows the active instance's version
    LaunchedEffect(selected) {
        app.wallpaper.sync(selected.ifBlank { null })
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (overlay == Overlay.None) {
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
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (overlay) {
                Overlay.Console -> ConsoleScreen(onClose = { overlay = Overlay.None })
                Overlay.InstanceDetail -> InstanceDetailScreen(onClose = { overlay = Overlay.None })
                Overlay.None -> when (screen) {
                    Screen.HOME -> HomeScreen(
                        onPlay = {
                            scope.launch {
                                var account = app.accounts.active()
                                // Microsoft tokens are refreshed silently when close to expiry
                                if (account?.isMicrosoft == true &&
                                    account.tokenExpiresAt < System.currentTimeMillis() + 10 * 60_000L
                                ) {
                                    try {
                                        val refreshed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            app.microsoft.refresh(account.refreshToken)
                                        }
                                        app.accounts.upsertMicrosoft(
                                            refreshed.name, refreshed.id, refreshed.accessToken,
                                            refreshed.refreshToken, refreshed.expiresAtMs,
                                        )
                                        account = app.accounts.active()
                                    } catch (_: Exception) {
                                        // fall back to the stored token; the console shows auth errors
                                    }
                                }
                                val instance = app.instances.active()
                                val pack = app.runtimePacks.packByName(app.settings.runtimePackValue)
                                    ?: app.runtimePacks.packs.value.firstOrNull()
                                if (instance != null && account != null && pack != null) {
                                    app.gameManager.launch(context, instance, account, pack)
                                    overlay = Overlay.Console
                                }
                            }
                        },
                        onPickVersion = { screen = Screen.VERSIONS },
                        onPickAccount = { screen = Screen.ACCOUNTS },
                        onOpenConsole = { overlay = Overlay.Console },
                        onOpenInstance = { overlay = Overlay.InstanceDetail },
                        onOpenBrowse = { screen = Screen.BROWSE },
                    )
                    Screen.VERSIONS -> VersionsScreen()
                    Screen.BROWSE -> BrowseScreen()
                    Screen.ACCOUNTS -> AccountsScreen()
                    Screen.SETTINGS -> SettingsScreen()
                    Screen.ABOUT -> AboutScreen()
                }
            }
        }
    }
}

private fun screenIcon(screen: Screen): String = when (screen) {
    Screen.HOME -> "⌂"
    Screen.VERSIONS -> "❖"
    Screen.BROWSE -> "⬢"
    Screen.ACCOUNTS -> "☻"
    Screen.SETTINGS -> "⚙"
    Screen.ABOUT -> "✦"
}

package studio.obsifox.obsilauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import studio.obsifox.obsilauncher.R
import studio.obsifox.obsilauncher.app
import studio.obsifox.obsilauncher.ui.gate.UpdateGateScreen
import studio.obsifox.obsilauncher.ui.screens.AboutScreen
import studio.obsifox.obsilauncher.ui.screens.AccountsScreen
import studio.obsifox.obsilauncher.ui.screens.BrowseScreen
import studio.obsifox.obsilauncher.ui.screens.ConsoleScreen
import studio.obsifox.obsilauncher.ui.screens.HomeScreen
import studio.obsifox.obsilauncher.ui.screens.InstanceDetailScreen
import studio.obsifox.obsilauncher.ui.screens.SettingsScreen
import studio.obsifox.obsilauncher.ui.screens.SetupWizard
import studio.obsifox.obsilauncher.ui.screens.VersionsScreen
import studio.obsifox.obsilauncher.ui.theme.LocalObsi

enum class Screen(val titleRes: Int) {
    HOME(R.string.nav_play),
    VERSIONS(R.string.nav_versions),
    BROWSE(R.string.nav_browse),
    ACCOUNTS(R.string.nav_accounts),
    SETTINGS(R.string.nav_settings),
    ABOUT(R.string.nav_about),
}

/** Tabs shown in the top bar — accounts live behind the avatar, about behind the star. */
private val TopTabs = listOf(Screen.HOME, Screen.VERSIONS, Screen.BROWSE, Screen.SETTINGS)

/** Height of the floating top bar (content below it starts here when not HOME). */
val TopBarSpace = 68.dp

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

    // wallpaper follows the active instance's version — always, wizard included
    LaunchedEffect(selected) {
        app.wallpaper.sync(selected.ifBlank { null })
    }

    // first launch: the setup wizard replaces the whole launcher shell
    val setupDone by app.settings.setupDone.collectAsState()
    if (!setupDone) {
        SetupWizard()
        return
    }

    // every open: the update gate checks updates + runtime before the shell
    var gatePassed by remember { mutableStateOf(false) }
    if (!gatePassed) {
        UpdateGateScreen(onEnter = { gatePassed = true })
        return
    }

    Box(Modifier.fillMaxSize()) {
        // screen content -------------------------------------------------------
        val contentModifier = if (overlay == Overlay.None && screen != Screen.HOME) {
            Modifier.padding(top = TopBarSpace)
        } else {
            Modifier
        }
        Box(contentModifier.fillMaxSize()) {
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
                    )
                    Screen.VERSIONS -> VersionsScreen()
                    Screen.BROWSE -> BrowseScreen()
                    Screen.ACCOUNTS -> AccountsScreen()
                    Screen.SETTINGS -> SettingsScreen()
                    Screen.ABOUT -> AboutScreen()
                }
            }
        }

        // floating top bar -----------------------------------------------------
        if (overlay == Overlay.None) {
            val accounts by app.accounts.accounts.collectAsState()
            val activeAccountId by app.accounts.activeId.collectAsState()
            val activeAccount = accounts.firstOrNull { it.id == activeAccountId }
            ObsiTopBar(
                account = activeAccount,
                current = screen,
                onSelect = { screen = it },
                onAccounts = { screen = Screen.ACCOUNTS },
                onAbout = { screen = Screen.ABOUT },
            )
        }
    }
}

@Composable
private fun ObsiTopBar(
    account: studio.obsifox.obsilauncher.core.accounts.Account?,
    current: Screen,
    onSelect: (Screen) -> Unit,
    onAccounts: () -> Unit,
    onAbout: () -> Unit,
) {
    val obsi = LocalObsi.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xB3140F0C), Color(0x66140F0C), Color.Transparent),
                ),
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // the player's real Minecraft head — opens the accounts screen
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAccounts),
                contentAlignment = Alignment.Center,
            ) {
                studio.obsifox.obsilauncher.ui.components.PlayerHead(
                    account = account,
                    modifier = Modifier.size(34.dp),
                )
            }
            Spacer(Modifier.width(9.dp))
            Text(
                text = account?.name ?: stringResource(R.string.nav_accounts),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = obsi.text,
                modifier = Modifier.clickable(onClick = onAccounts),
            )
            Spacer(Modifier.width(26.dp))
            TopTabs.forEach { tab ->
                TopTab(
                    label = stringResource(tab.titleRes),
                    selected = current == tab,
                    onClick = { onSelect(tab) },
                )
                Spacer(Modifier.width(10.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "✦",
                style = MaterialTheme.typography.titleMedium,
                color = if (current == Screen.ABOUT) obsi.accent else obsi.textDim,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAbout)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun TopTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val obsi = LocalObsi.current
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = if (selected) obsi.accent else obsi.textDim,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .drawBehind {
                if (selected) {
                    drawRoundRect(
                        color = obsi.accent,
                        topLeft = Offset(0f, size.height - 3.dp.toPx()),
                        size = Size(size.width, 3.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                }
            },
    )
}

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontFamily
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
import studio.obsifox.obsilauncher.ui.screens.GameScreen
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

/**
 * v1.9.0 — the shared launch routine, used by the PLAY button and by the
 * automatic launch after a download finishes. Refreshes Microsoft tokens
 * when they are close to expiry, then spawns the game and reports back.
 *
 * v1.12.0 — PRE-FLIGHT: version files install + sha1 verify before spawn.
 *
 * v1.13.0 — the runtime check finally talks to the SAME system that boots
 * the JVM: ObsiComponents (the Internal-* JREs). The old pre-flight verified
 * RuntimePacks (pack.json/launcher.so) while beginGame() booted an
 * ObsiComponents runtime — two disconnected stores, so a "verified" device
 * could still fail to open the JVM. Also: the missing GameManager.launch()
 * glue finally exists, so PLAY actually reaches JLI_Launch now.
 */
internal suspend fun launchGame(
    context: android.content.Context,
    app: studio.obsifox.obsilauncher.App,
    onStarted: () -> Unit,
) {
    val instance = app.instances.active() ?: return

    // ---- 1) version files ---------------------------------------------------
    if (!app.installer.isInstalled(instance.versionId)) {
        val v = app.manifest.versions.value.firstOrNull { it.id == instance.versionId }
            ?: return // unknown version and offline — nothing we can do here
        if (app.installer.state.value is studio.obsifox.obsilauncher.core.game.InstallState.Running) {
            return // an install is already in flight; the button shows its progress
        }
        app.installer.install(v, app.settings)
        if (app.installer.state.value !is studio.obsifox.obsilauncher.core.game.InstallState.Done) {
            return // download failed — the console/button carry the state
        }
    }

    // ---- 2) runtime / JVM (ObsiComponents — the boot path's own store) ------
    val major = studio.obsifox.obsilauncher.core.game.LaunchPipeline.javaMajor(context, instance.versionId)
    var runtimeName = studio.obsifox.obsilauncher.core.runtime.ObsiComponents.installedRuntimeName(context, major)
    if (runtimeName == null) {
        // nothing usable — unpack the bundled Internal-21 (zero-touch), then
        // try to fetch the version's own runtime when one is published
        studio.obsifox.obsilauncher.core.runtime.ObsiComponents.ensureBundledRuntime(context)
        runtimeName = studio.obsifox.obsilauncher.core.runtime.ObsiComponents.installedRuntimeName(context, major)
        if (runtimeName == null) {
            val wanted = DOWNLOADABLE_MAJOR[major] ?: "jre-21"
            if (studio.obsifox.obsilauncher.core.runtime.ObsiComponents.downloadRuntime(context, wanted)) {
                runtimeName = studio.obsifox.obsilauncher.core.runtime.ObsiComponents.installedRuntimeName(context, major)
            }
        }
    }
    if (runtimeName == null) return // no JVM on disk and none could be fetched

    // ---- 3) account + spawn -------------------------------------------------
    var account = app.accounts.active()
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
    if (account != null) {
        if (app.gameManager.launch(context, instance, account, runtimeName)) {
            onStarted()
        }
    }
}

/** Internal-* runtime to fetch for a Minecraft java requirement (best effort). */
private val DOWNLOADABLE_MAJOR = mapOf(8 to "jre-8", 17 to "jre-17", 25 to "jre-25")

/** v1.9.0 — downloads the version that is currently selected in settings (vanilla). */
internal suspend fun startSelectedDownload(app: studio.obsifox.obsilauncher.App) {
    val vid = app.settings.selectedVersionValue
    if (vid.isBlank()) return
    if (app.installer.state.value is studio.obsifox.obsilauncher.core.game.InstallState.Running) return
    if (app.installer.isInstalled(vid)) return
    val v = app.manifest.versions.value.firstOrNull { it.id == vid } ?: return
    app.installer.install(v, app.settings)
    app.instances.create(vid, vid, vid)
}

/** Secondary destinations on top of the tab bar. */
sealed class Overlay {
    data object None : Overlay()
    data object Console : Overlay()
    data object InstanceDetail : Overlay()
    data object Game : Overlay()
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

    // v1.9.0: on the very first setup the launcher pre-selects the LATEST
    // Minecraft release (not downloaded yet) so the home button is a
    // ready-to-press DOWNLOAD right away.
    LaunchedEffect(Unit) {
        if (app.settings.selectedVersionValue.isBlank()) {
            app.manifest.refresh(force = false)
            val latest = app.manifest.latest.value.release
            if (latest.isNotBlank()) app.settings.selectedVersionValue = latest
        } else {
            app.manifest.refresh(force = false)
        }
    }

    // ---- download flow (v1.9.0) --------------------------------------------
    // a version download starting anywhere pulls the user back to the home
    // screen, where the big button turns into a live DOWNLOAD progress pill;
    // the moment the download finishes the game launches on its own.
    val installState by app.installer.state.collectAsState()
    val loaderInstallState by app.loaders.state.collectAsState()
    LaunchedEffect(installState, loaderInstallState) {
        val running = installState is studio.obsifox.obsilauncher.core.game.InstallState.Running ||
            loaderInstallState is studio.obsifox.obsilauncher.core.game.InstallState.Running
        if (running && screen != Screen.HOME) screen = Screen.HOME
    }
    LaunchedEffect(installState) {
        val done = installState as? studio.obsifox.obsilauncher.core.game.InstallState.Done
        if (done != null) {
            val instance = app.instances.byVersion(done.id)
                ?: app.instances.create(done.id, done.id, done.id)
            app.instances.setActive(instance.id)
            app.settings.selectedVersionValue = instance.versionId
            launchGame(context, app) { overlay = Overlay.Game }
        }
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

    // v1.13.0 — crash reporting: after the process restart that follows a game
    // exit, the persisted marker becomes the "Game Crashed" dialog; a boot
    // failure reaches us live through the same flow.
    val crashExit by studio.obsifox.obsilauncher.core.game.ObsiGameExit.lastExit.collectAsState()
    LaunchedEffect(Unit) {
        val marker = studio.obsifox.obsilauncher.core.game.ObsiGameExit.consumeMarker(context)
        if (marker != null && marker.code != 0) {
            studio.obsifox.obsilauncher.core.game.ObsiGameExit.post(marker)
        }
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
                Overlay.Game -> GameScreen(onExitRequest = { overlay = Overlay.None })
                Overlay.None -> when (screen) {
                    Screen.HOME -> HomeScreen(
                        onPlay = {
                            scope.launch {
                                launchGame(context, app) { overlay = Overlay.Game }
                            }
                        },
                        onDownload = {
                            scope.launch {
                                startSelectedDownload(app)
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

    // v1.13.0 — the user's reference crash report: title, the extracted error
    // in a scrollable box, Copy Exit Code and Close.
    crashExit?.let { exit ->
        CrashDialog(exit = exit, onDismiss = { studio.obsifox.obsilauncher.core.game.ObsiGameExit.dismiss() })
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

/**
 * v1.13.0 — the game crash report, styled after the user's reference mock:
 * a dark panel, the crash headline in red, the extracted error in a
 * monospace scroll area and two actions — Copy Exit Code and Close.
 */
@Composable
private fun CrashDialog(
    exit: studio.obsifox.obsilauncher.core.game.ObsiGameExit.Exit,
    onDismiss: () -> Unit,
) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val label = stringResource(R.string.crash_title)
    val copiedLabel = stringResource(R.string.crash_copied)
    var copied by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE5604C),
            )
        },
        text = {
            Column {
                if (exit.versionId != null) {
                    Text(
                        text = exit.versionId + "  ·  " +
                            stringResource(R.string.crash_exit_code, exit.code),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFB9AFA6),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x3314000000)),
                ) {
                    Text(
                        text = exit.excerpt.ifBlank { stringResource(R.string.crash_empty) },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFE8E2DB),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp),
                    )
                }
            }
        },
        confirmButton = {
            Text(
                text = if (copied) copiedLabel else stringResource(R.string.crash_copy_code),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF7ED957),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        clipboard.setText(
                            androidx.compose.ui.text.AnnotatedString("Exit code: ${exit.code}"),
                        )
                        copied = true
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        },
        dismissButton = {
            Text(
                text = stringResource(R.string.crash_close),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFB9AFA6),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        },
        containerColor = Color(0xFF201A17),
    )
}

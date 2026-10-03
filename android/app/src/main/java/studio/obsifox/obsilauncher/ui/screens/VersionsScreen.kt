package studio.obsifox.obsilauncher.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import studio.obsifox.obsilauncher.R
import studio.obsifox.obsilauncher.app
import studio.obsifox.obsilauncher.core.game.InstallState
import studio.obsifox.obsilauncher.core.game.McVersion
import studio.obsifox.obsilauncher.core.instance.Instance
import studio.obsifox.obsilauncher.core.loaders.LoaderType
import studio.obsifox.obsilauncher.obsi.bundledArtFor
import studio.obsifox.obsilauncher.ui.components.GlassCard
import studio.obsifox.obsilauncher.ui.components.LoaderGlyph
import studio.obsifox.obsilauncher.ui.components.ObsiButton
import studio.obsifox.obsilauncher.ui.components.ObsiGhostButton
import studio.obsifox.obsilauncher.ui.components.ObsiTextButton
import studio.obsifox.obsilauncher.ui.components.ProgressRow
import studio.obsifox.obsilauncher.ui.theme.LocalObsi

/**
 * Version picker as a GRID OF CARDS, each showing the artwork of that
 * version's era; tapping a card opens a compact loader-icon picker where
 * every loader name/version sits in an orange (accent) box with white text.
 */
@Composable
fun VersionsScreen() {
    val context = LocalContext.current
    val app = context.app
    val obsi = LocalObsi.current

    var tab by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val tabs = listOf(
        stringResourceCompat(R.string.versions_installed),
        stringResourceCompat(R.string.versions_releases),
        stringResourceCompat(R.string.versions_snapshots),
        stringResourceCompat(R.string.versions_old),
    )

    LaunchedEffect(Unit) { app.manifest.refresh(force = false) }

    val manifestLoading by app.manifest.loading.collectAsState()
    val allVersions by app.manifest.versions.collectAsState()
    val releases = allVersions.filter { it.type == "release" }
    val snapshots = allVersions.filter { it.type == "snapshot" }
    val old = allVersions.filter { it.isOld }
    val installState by app.installer.state.collectAsState()
    val loaderState by app.loaders.state.collectAsState()
    val instances by app.instances.instances.collectAsState()

    // loader install flow state
    var loaderPick by remember { mutableStateOf<McVersion?>(null) }
    var loaderVersionPick by remember { mutableStateOf<Pair<LoaderType, McVersion>?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        TabRow(
            selectedTabIndex = tab,
            containerColor = Color.Transparent,
            contentColor = obsi.accent,
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(title, style = MaterialTheme.typography.labelLarge) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        when {
            installState is InstallState.Running -> InstallProgressCard(installState as InstallState.Running)
            loaderState is InstallState.Running -> InstallProgressCard(loaderState as InstallState.Running)
            installState is InstallState.Failed -> InstallFailedCard(installState as InstallState.Failed)
            loaderState is InstallState.Failed -> InstallFailedCard(loaderState as InstallState.Failed)
            installState is InstallState.Done || loaderState is InstallState.Done -> {
                val id = when {
                    installState is InstallState.Done -> (installState as InstallState.Done).id
                    else -> (loaderState as InstallState.Done).id
                }
                GlassCard(modifier = Modifier.padding(bottom = 10.dp)) {
                    Text(stringResourceCompat(R.string.dl_done, id), color = obsi.accent)
                }
            }
        }

        when (tab) {
            0 -> InstalledGrid(instances) { v ->
                app.instances.setActive(v.id)
                app.settings.selectedVersionValue = v.versionId
            }
            1 -> RemoteGrid(releases, manifestLoading) { loaderPick = it }
            2 -> RemoteGrid(snapshots, manifestLoading) { loaderPick = it }
            3 -> RemoteGrid(old, manifestLoading) { loaderPick = it }
        }
    }

    // step 1: loader icons for this Minecraft version -------------------------
    loaderPick?.let { mc ->
        LoaderPickerDialog(
            mc = mc,
            onDismiss = { loaderPick = null },
            onVanilla = { picked ->
                val version = picked
                loaderPick = null
                scope.launch {
                    app.installer.install(version, app.settings)
                    app.instances.create(version.id, version.id, version.id, LoaderType.VANILLA)
                }
            },
            onLoader = { type ->
                loaderVersionPick = type to mc
                loaderPick = null
            },
        )
    }

    // step 2: loader version numbers (orange chips) ---------------------------
    loaderVersionPick?.let { (type, mc) ->
        LoaderVersionDialog(
            type = type,
            mc = mc.id,
            onDismiss = { loaderVersionPick = null },
            onPick = { lv ->
                val pair = type to mc
                loaderVersionPick = null
                scope.launch {
                    try {
                        val installed = app.loaders.install(type, mc.id, lv, app.installer, app.settings)
                        app.instances.create(installed.versionId, installed.versionId, mc.id, type, installed.loaderVersion)
                    } catch (_: Exception) {
                    }
                }
            },
        )
    }
}

/** one version card: era artwork + version id + type. */
@Composable
private fun VersionCard(
    title: String,
    subtitle: String,
    mcVersion: String,
    selected: Boolean,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val obsi = LocalObsi.current
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.7f)
            .clip(RoundedCornerShape(14.dp))
            .border(
                1.5.dp,
                if (selected) obsi.accent else Color(0x2EFFFFFF),
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick),
    ) {
        Image(
            painter = painterResource(bundledArtFor(mcVersion)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x14000000), Color(0xCC0B0908)),
                    ),
                ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF4EFEA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFB9AFA6),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (badge != null) {
            Text(
                badge,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(obsi.accent)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun InstalledGrid(instances: List<Instance>, onSelect: (Instance) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val obsi = LocalObsi.current
    if (instances.isEmpty()) {
        GlassCard { Text(stringResourceCompat(R.string.versions_empty_installed), color = obsi.textDim) }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
    ) {
        items(instances, key = { it.id }) { instance ->
            VersionCard(
                title = instance.name,
                subtitle = loaderBadge(instance),
                mcVersion = instance.mcVersion,
                selected = instance.id == app.instances.activeId.value,
                badge = stringResourceCompat(R.string.versions_selected).takeIf {
                    instance.id == app.instances.activeId.value
                },
                onClick = { onSelect(instance) },
            )
        }
    }
}

@Composable
private fun RemoteGrid(
    versions: List<McVersion>,
    loading: Boolean,
    onInstall: (McVersion) -> Unit,
) {
    val obsi = LocalObsi.current
    if (versions.isEmpty()) {
        GlassCard {
            Text(
                if (loading) "…" else stringResourceCompat(R.string.versions_empty_remote),
                color = obsi.textDim,
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
    ) {
        items(versions, key = { it.id }) { v ->
            VersionCard(
                title = v.id,
                subtitle = v.type + (v.releaseTime.takeIf { it.isNotEmpty() }
                    ?.let { " · ${it.substring(0, 10)}" } ?: ""),
                mcVersion = v.id,
                selected = false,
                onClick = { onInstall(v) },
            )
        }
    }
}

/** loader picker as a single row of icon tiles with orange name chips. */
@Composable
private fun LoaderPickerDialog(
    mc: McVersion,
    onDismiss: () -> Unit,
    onVanilla: (McVersion) -> Unit,
    onLoader: (LoaderType) -> Unit,
) {
    val obsi = LocalObsi.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResourceCompat(R.string.loader_pick_title, mc.id)) },
        text = {
            Column {
                Text(
                    stringResourceCompat(R.string.loader_pick_hint),
                    color = obsi.textDim,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                val loaders = listOf(
                    LoaderType.VANILLA, LoaderType.FABRIC, LoaderType.FORGE,
                    LoaderType.NEOFORGE, LoaderType.QUILT, LoaderType.OPTIFINE,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    loaders.forEach { type ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (type == LoaderType.VANILLA) onVanilla(mc) else onLoader(type)
                                }
                                .padding(4.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(obsi.accentDim.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                LoaderGlyph(type = type, color = obsi.text, modifier = Modifier.size(28.dp))
                            }
                            Spacer(Modifier.height(5.dp))
                            // loader name inside an orange box, white text
                            Text(
                                type.display,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(obsi.accent)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { ObsiTextButton(stringResourceCompat(R.string.cancel), onClick = onDismiss) },
    )
}

@Composable
private fun LoaderVersionDialog(
    type: LoaderType,
    mc: String,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    val obsi = LocalObsi.current
    var versions by remember { mutableStateOf<List<studio.obsifox.obsilauncher.core.loaders.LoaderVersion>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val app = context.app

    LaunchedEffect(type, mc) {
        try {
            versions = app.loaders.versions(type, mc)
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResourceCompat(R.string.loader_version_title, type.display, mc)) },
        text = {
            Column {
                if (loading) {
                    Text("…", color = obsi.textDim)
                } else if (versions.isEmpty()) {
                    Text(stringResourceCompat(R.string.loader_none), color = obsi.textDim)
                } else {
                    LazyColumn(modifier = Modifier.height(320.dp)) {
                        items(versions, key = { it.version }) { lv ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(lv.version) }
                                    .padding(vertical = 6.dp),
                            ) {
                                // the loader number inside an orange box, white text
                                Text(
                                    lv.version,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .width(150.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(obsi.accent)
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                if (lv.recommended) {
                                    Text(
                                        stringResourceCompat(R.string.loader_recommended),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = obsi.accent,
                                    )
                                } else if (!lv.stable) {
                                    Text(
                                        stringResourceCompat(R.string.loader_unstable),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = obsi.textDim,
                                    )
                                }
                            }
                        }
                    }
                }
                error?.let { Text(it, color = obsi.danger, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {},
        dismissButton = { ObsiTextButton(stringResourceCompat(R.string.cancel), onClick = onDismiss) },
    )
}

@Composable
private fun InstallProgressCard(state: InstallState.Running) {
    val obsi = LocalObsi.current
    val context = LocalContext.current
    GlassCard(modifier = Modifier.padding(bottom = 10.dp)) {
        val label = when (state.step) {
            "manifest" -> stringResourceCompat(R.string.dl_step_manifest)
            "client" -> stringResourceCompat(R.string.dl_step_client)
            "libraries" -> stringResourceCompat(R.string.dl_step_libraries, state.done + 1, state.total)
            "natives" -> stringResourceCompat(R.string.dl_step_natives)
            "assets" -> stringResourceCompat(R.string.dl_step_assets, state.done + 1, state.total)
            "loader_profile" -> stringResourceCompat(R.string.dl_step_loader_profile)
            "loader_installer" -> stringResourceCompat(R.string.dl_step_loader_installer)
            "loader_output" -> stringResourceCompat(R.string.dl_step_loader_output)
            "modpack_index" -> stringResourceCompat(R.string.dl_step_modpack_index)
            "modpack_files" -> stringResourceCompat(R.string.dl_step_modpack_files, state.done + 1, state.total)
            "verify" -> stringResourceCompat(R.string.dl_step_verify)
            "repair_client" -> stringResourceCompat(R.string.dl_step_repair_client)
            "repair_libraries" -> stringResourceCompat(R.string.dl_step_repair_libraries)
            else -> if (state.step.startsWith("content:")) state.step.removePrefix("content:") else state.step
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { ProgressRow(label, state.fraction) }
            ObsiTextButton(
                stringResourceCompat(R.string.dl_cancel),
                onClick = { context.app.installer.cancel() },
            )
        }
    }
}

@Composable
private fun InstallFailedCard(state: InstallState.Failed) {
    val context = LocalContext.current
    GlassCard(modifier = Modifier.padding(bottom = 10.dp)) {
        Text(
            stringResourceCompat(R.string.dl_failed, state.message ?: "unknown"),
            style = MaterialTheme.typography.bodyMedium,
        )
        ObsiGhostButton(stringResourceCompat(R.string.dl_retry), onClick = {
            context.app.installer.state.value = InstallState.Idle
            context.app.loaders.state.value = InstallState.Idle
        })
    }
}

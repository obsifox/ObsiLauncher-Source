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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import studio.obsifox.obsilauncher.obsi.artForVersion
import studio.obsifox.obsilauncher.obsi.releaseNameFor
import studio.obsifox.obsilauncher.ui.components.GlassCard
import studio.obsifox.obsilauncher.ui.components.LoaderIcon
import studio.obsifox.obsilauncher.ui.components.ObsiGhostButton
import studio.obsifox.obsilauncher.ui.components.ObsiTextButton
import studio.obsifox.obsilauncher.ui.components.ProgressRow
import studio.obsifox.obsilauncher.ui.theme.LocalObsi

/**
 * Version picker — versions are grouped under a PARENT (e.g. every 1.21.x
 * patch under a single "1.21" card). Each parent card shows the era's
 * official artwork and how many children it holds; tapping it opens the
 * child-version menu, and picking a child asks for the loader (icons only,
 * names/numbers inside orange boxes with white text).
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

    // install flow state: parent -> child -> loader -> loader version
    var childPick by remember { mutableStateOf<String?>(null) }
    var loaderPick by remember { mutableStateOf<McVersion?>(null) }
    var loaderVersionPick by remember { mutableStateOf<Pair<LoaderType, McVersion>?>(null) }
    // custom display name for any installed version / loader build (v1.9.0)
    var renameTarget by remember { mutableStateOf<studio.obsifox.obsilauncher.core.instance.Instance?>(null) }

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

        // the grids live in their own bounded box so the install banners can
        // never push or overlap them (v1.9.0 layout fix)
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> InstalledGrid(instances, onRename = { renameTarget = it }) { v ->
                    app.instances.setActive(v.id)
                    app.settings.selectedVersionValue = v.versionId
                }
                1 -> ParentGrid(releases, manifestLoading, onOpen = { childPick = it })
                2 -> RemoteGrid(snapshots, manifestLoading) { loaderPick = it }
                3 -> ParentGrid(old, manifestLoading, onOpen = { childPick = it })
            }
        }
    }

    renameTarget?.let { target ->
        RenameInstanceDialog(
            currentName = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                app.instances.rename(target.id, newName)
                renameTarget = null
            },
        )
    }

    // step 1: the parent's children ------------------------------------------
    childPick?.let { parent ->
        ChildVersionDialog(
            parent = parent,
            versions = when (tab) {
                3 -> old
                else -> releases
            }.filter { parentId(it.id) == parent },
            onDismiss = { childPick = null },
            onPick = { mc ->
                childPick = null
                loaderPick = mc
            },
        )
    }

    // step 2: loader icons for the chosen child -------------------------------
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

    // step 3: loader version numbers (orange chips) ---------------------------
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

/** "1.21.4" -> "1.21", "26.3" -> "26" — the representative parent group. */
internal fun parentId(versionId: String): String {
    val base = versionId.substringBefore('-').substringBefore('+')
    val parts = base.split('.')
    return if (parts.size >= 2 && parts[0].all(Char::isDigit) && parts[0].isNotEmpty() &&
        parts[1].all(Char::isDigit) && parts[1].isNotEmpty()
    ) {
        "${parts[0]}.${parts[1]}"
    } else {
        base
    }
}

/** one version card: era artwork + version id + type. */
@Composable
private fun VersionCard(
    title: String,
    subtitle: String,
    mcVersion: String,
    selected: Boolean,
    loaderType: LoaderType? = null,
    badge: String? = null,
    onRename: (() -> Unit)? = null,
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
            painter = painterResource(artForVersion(mcVersion)),
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
        // the loader's own icon, top-left — grass block for vanilla builds
        if (loaderType != null) {
            LoaderIcon(
                type = loaderType,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(26.dp),
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
        // custom-name affordance: small pencil chip below the badge (v1.9.0)
        if (onRename != null) {
            Text(
                "\u270E",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 8.dp)
                    .offset(y = if (badge != null) 32.dp else 8.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0x66000000))
                    .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(7.dp))
                    .clickable(onClick = onRename)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun InstalledGrid(
    instances: List<Instance>,
    onRename: (Instance) -> Unit,
    onSelect: (Instance) -> Unit,
) {
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
                loaderType = instance.loaderType,
                badge = stringResourceCompat(R.string.versions_selected).takeIf {
                    instance.id == app.instances.activeId.value
                },
                onRename = { onRename(instance) },
                onClick = { onSelect(instance) },
            )
        }
    }
}

/** flat card grid — snapshots and anything that has no family grouping. */
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

/**
 * The PARENT grid — one card per version family, era artwork + the family's
 * release name + an orange chip counting the children inside.
 */
@Composable
private fun ParentGrid(
    versions: List<McVersion>,
    loading: Boolean,
    onOpen: (String) -> Unit,
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
    val groups = remember(versions) {
        versions.sortedByDescending { it.releaseTime }
            .groupBy { parentId(it.id) }
            .map { (parent, children) ->
                ParentGroup(parent, children)
            }
            .sortedByDescending { it.newest.releaseTime }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
    ) {
        items(groups, key = { it.parent }) { g ->
            val name = releaseNameFor(g.parent)
            ParentCard(
                parent = g.parent,
                subtitle = name.ifBlank { g.newest.type },
                count = g.children.size,
                latest = g.newest.id,
                onClick = { onOpen(g.parent) },
            )
        }
    }
}

private data class ParentGroup(val parent: String, val children: List<McVersion>) {
    val newest: McVersion get() = children.first()
}

@Composable
private fun ParentCard(
    parent: String,
    subtitle: String,
    count: Int,
    latest: String,
    onClick: () -> Unit,
) {
    val obsi = LocalObsi.current
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.7f)
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, Color(0x2EFFFFFF), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Image(
            painter = painterResource(artForVersion(latest)),
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
                parent,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF4EFEA),
                maxLines = 1,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFB9AFA6),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // the orange marker: this parent represents N versions
        Text(
            " $count ",
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

/** v1.9.0 — set a custom display name for an installed version / loader build. */
@Composable
private fun RenameInstanceDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(currentName) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResourceCompat(R.string.instances_rename_title)) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            ObsiTextButton(stringResourceCompat(R.string.instances_rename_save), onClick = { onConfirm(text) })
        },
        dismissButton = { ObsiTextButton(stringResourceCompat(R.string.cancel), onClick = onDismiss) },
    )
}

/** the parent's children, newest first, ids inside orange boxes. */
@Composable
private fun ChildVersionDialog(
    parent: String,
    versions: List<McVersion>,
    onDismiss: () -> Unit,
    onPick: (McVersion) -> Unit,
) {
    val obsi = LocalObsi.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResourceCompat(R.string.parent_pick_title, parent)) },
        text = {
            if (versions.isEmpty()) {
                Text("…", color = obsi.textDim)
            } else {
                LazyColumn(modifier = Modifier.height(340.dp)) {
                    items(versions, key = { it.id }) { v ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(v) }
                                .padding(vertical = 6.dp),
                        ) {
                            Text(
                                v.id,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                modifier = Modifier
                                    .width(130.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(obsi.accent)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                releaseNameFor(parentId(v.id)).takeIf { it.isNotBlank() && parentId(v.id) != v.id }?.let {
                                    Text(it, style = MaterialTheme.typography.labelMedium, color = obsi.text)
                                }
                                Text(
                                    v.releaseTime.take(10) + if (v.type != "release") " · ${v.type}" else "",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = obsi.textDim,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { ObsiTextButton(stringResourceCompat(R.string.cancel), onClick = onDismiss) },
    )
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
                                    .background(Color(0xFFFFFFFF)),
                                contentAlignment = Alignment.Center,
                            ) {
                                LoaderIcon(type = type, modifier = Modifier.size(34.dp))
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

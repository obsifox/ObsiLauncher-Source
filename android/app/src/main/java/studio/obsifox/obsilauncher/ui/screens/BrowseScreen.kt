package studio.obsifox.obsilauncher.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import studio.obsifox.obsilauncher.R
import studio.obsifox.obsilauncher.app
import studio.obsifox.obsilauncher.core.game.InstallState
import studio.obsifox.obsilauncher.core.modrinth.DepCandidate
import studio.obsifox.obsilauncher.core.modrinth.MrProject
import studio.obsifox.obsilauncher.core.modrinth.MrVersion
import studio.obsifox.obsilauncher.core.modrinth.ProjectKind
import studio.obsifox.obsilauncher.core.modrinth.SearchHit
import studio.obsifox.obsilauncher.ui.components.GlassCard
import studio.obsifox.obsilauncher.ui.components.ObsiButton
import studio.obsifox.obsilauncher.ui.components.ObsiGhostButton
import studio.obsifox.obsilauncher.ui.components.ObsiTextButton
import studio.obsifox.obsilauncher.ui.components.ProgressRow
import studio.obsifox.obsilauncher.ui.components.SectionTitle
import studio.obsifox.obsilauncher.ui.theme.LocalObsi

@Composable
fun BrowseScreen() {
    val context = LocalContext.current
    val app = context.app
    val obsi = LocalObsi.current
    val scope = rememberCoroutineScope()

    val instances by app.instances.instances.collectAsState()
    val activeInstance = instances.firstOrNull { it.id == app.instances.activeId.value }

    var tab by remember { mutableIntStateOf(0) }
    val kinds = listOf(ProjectKind.MOD, ProjectKind.MODPACK, ProjectKind.RESOURCEPACK, ProjectKind.SHADER)
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<MrProject?>(null) }
    var installProgress by remember { mutableStateOf<InstallState?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    // .mrpack import
    val pickMrpack = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val target = java.io.File(context.cacheDir, "import_${System.currentTimeMillis()}.mrpack")
        scope.launch {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { input.copyTo(it) }
                }
                installProgress = InstallState.Running("modpack_index", 0, 1, null)
                val versionId = app.modrinth.installModpack(target, "modpack", app.settings, app.installer, app.loaders) {
                    installProgress = it
                }
                app.instances.create(versionId, versionId, versionId.substringBefore("-fabric").substringBefore("-forge").substringBefore("-OptiFine").takeIf { it != versionId } ?: versionId)
                message = context.getString(R.string.browse_modpack_done)
            } catch (e: Exception) {
                message = e.message
            } finally {
                installProgress = null
                target.delete()
            }
        }
    }

    fun search() {
        scope.launch {
            loading = true
            error = null
            try {
                results = app.modrinth.api.search(
                    query = query.trim(),
                    kind = kinds[tab],
                    gameVersion = activeInstance?.mcVersion,
                    loaders = if (kinds[tab] == ProjectKind.MOD) app.modrinth.content.compatibleLoaders(activeInstance?.versionId ?: "") else emptyList(),
                    sort = "relevance",
                    limit = 25,
                ).hits
            } catch (e: Exception) {
                error = e.message
                results = emptyList()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(tab) { if (query.isBlank() && results.isEmpty()) search() else search() }

    Column(modifier = Modifier.fillMaxWidth()) {
        TabRow(
            selectedTabIndex = tab,
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = obsi.accent,
        ) {
            kinds.forEachIndexed { index, kind ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = {
                        Text(
                            when (kind) {
                                ProjectKind.MOD -> stringResourceCompat(R.string.browse_mods)
                                ProjectKind.MODPACK -> stringResourceCompat(R.string.browse_modpacks)
                                ProjectKind.RESOURCEPACK -> stringResourceCompat(R.string.browse_resourcepacks)
                                ProjectKind.SHADER -> stringResourceCompat(R.string.browse_shaders)
                            },
                            maxLines = 1,
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        GlassCard {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResourceCompat(R.string.browse_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                ObsiButton(stringResourceCompat(R.string.browse_search), onClick = { search() })
                Spacer(Modifier.height(0.dp))
                ObsiTextButton(
                    stringResourceCompat(R.string.browse_import_mrpack),
                    onClick = { pickMrpack.launch("application/octet-stream") },
                )
            }
            if (activeInstance != null) {
                Text(
                    stringResourceCompat(R.string.browse_target, activeInstance.name, activeInstance.mcVersion),
                    style = MaterialTheme.typography.labelMedium,
                    color = obsi.textDim,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                Text(
                    stringResourceCompat(R.string.browse_no_instance),
                    style = MaterialTheme.typography.labelMedium,
                    color = obsi.danger,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        installProgress?.let { state ->
            GlassCard(modifier = Modifier.padding(top = 12.dp)) {
                when (state) {
                    is InstallState.Running -> ProgressRow(
                        state.step.removePrefix("content:"),
                        state.fraction,
                    )
                    else -> {}
                }
            }
        }

        message?.let {
            GlassCard(modifier = Modifier.padding(top = 12.dp)) {
                Text(it, style = MaterialTheme.typography.bodyMedium)
                ObsiTextButton(stringResourceCompat(R.string.ok), onClick = { message = null })
            }
        }
        error?.let {
            GlassCard(modifier = Modifier.padding(top = 12.dp)) {
                Text(stringResourceCompat(R.string.err_generic, it), color = obsi.danger)
            }
        }

        SectionTitle(stringResourceCompat(R.string.browse_results))
        if (loading) {
            GlassCard { Text("…", color = obsi.textDim) }
        } else if (results.isEmpty()) {
            GlassCard { Text(stringResourceCompat(R.string.browse_empty), color = obsi.textDim) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(results, key = { it.projectId }) { hit ->
                    GlassCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        try {
                                            detail = app.modrinth.api.project(hit.projectId)
                                        } catch (e: Exception) {
                                            error = e.message
                                        }
                                    }
                                },
                        ) {
                            if (hit.iconUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = hit.iconUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(MaterialTheme.shapes.small),
                                )
                                Spacer(Modifier.size(12.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(hit.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    hit.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = obsi.textDim,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${hit.downloads} ⬇ · ${hit.author}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = obsi.textDim,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    detail?.let { project ->
        ProjectDetailDialog(
            project = project,
            onDismiss = { detail = null },
            onMessage = { message = it },
            onProgress = { installProgress = it },
        )
    }
}

@Composable
private fun ProjectDetailDialog(
    project: MrProject,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
    onProgress: (InstallState) -> Unit,
) {
    val context = LocalContext.current
    val app = context.app
    val obsi = LocalObsi.current
    val scope = rememberCoroutineScope()

    val instances by app.instances.instances.collectAsState()
    val active = instances.firstOrNull { it.id == app.instances.activeId.value }
    val kind = ProjectKind.fromApi(project.projectType)

    var versions by remember { mutableStateOf<List<MrVersion>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var picked by remember { mutableStateOf<MrVersion?>(null) }

    LaunchedEffect(project.id, active?.versionId) {
        try {
            versions = app.modrinth.api.versions(
                project.id,
                loaders = if (kind == ProjectKind.MOD) app.modrinth.content.compatibleLoaders(active?.versionId ?: "") else emptyList(),
                gameVersions = listOfNotNull(active?.mcVersion),
            )
        } catch (_: Exception) {
            versions = emptyList()
        } finally {
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(project.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(project.description, style = MaterialTheme.typography.bodyMedium, color = obsi.textDim)
                Spacer(Modifier.height(8.dp))
                Text(
                    "${project.downloads} ⬇ · ${project.loaders.joinToString(", ").ifEmpty { "—" }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = obsi.textDim,
                )
                Spacer(Modifier.height(10.dp))
                SectionTitle(stringResourceCompat(R.string.browse_versions))
                if (loading) {
                    Text("…", color = obsi.textDim)
                } else if (versions.isEmpty()) {
                    Text(stringResourceCompat(R.string.browse_no_compatible), color = obsi.textDim)
                } else {
                    versions.take(15).forEach { v ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(v.versionNumber, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${v.versionType} · ${v.loaders.joinToString(",")}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = obsi.textDim,
                                )
                            }
                            ObsiGhostButton(stringResourceCompat(R.string.versions_install), onClick = { picked = v })
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { ObsiTextButton(stringResourceCompat(R.string.cancel), onClick = onDismiss) },
    )

    picked?.let { version ->
        DependencyDialog(
            project = project,
            version = version,
            onDismiss = { picked = null },
            onInstalled = { names ->
                picked = null
                onDismiss()
                onMessage(names)
            },
            onProgress = onProgress,
        )
    }
}

/**
 * The dependency confirmation dialog: required dependencies are locked
 * (checked + disabled), optional ones can be toggled, and every entry that is
 * already installed is shown but excluded from the plan.
 */
@Composable
private fun DependencyDialog(
    project: MrProject,
    version: MrVersion,
    onDismiss: () -> Unit,
    onInstalled: (String) -> Unit,
    onProgress: (InstallState) -> Unit,
) {
    val context = LocalContext.current
    val app = context.app
    val obsi = LocalObsi.current
    val scope = rememberCoroutineScope()

    var candidates by remember { mutableStateOf<List<DepCandidate>>(emptyList()) }
    var checked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(version.id) {
        try {
            val list = app.modrinth.newResolver().resolve(project, version)
            candidates = list
            checked = list.filter { it.required && !it.alreadyInstalled }.map { it.project.id }.toSet()
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(stringResourceCompat(R.string.deps_title, project.title)) },
        text = {
            Column {
                if (loading) {
                    Text("…", color = obsi.textDim)
                } else {
                    Text(
                        stringResourceCompat(R.string.deps_hint, version.versionNumber),
                        style = MaterialTheme.typography.bodyMedium,
                        color = obsi.textDim,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (candidates.isEmpty()) {
                        Text(stringResourceCompat(R.string.deps_none))
                    } else {
                        Column(Modifier.height(300.dp).verticalScroll(rememberScrollState())) {
                            candidates.forEach { dep ->
                                val locked = dep.required
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Checkbox(
                                        checked = locked || checked.contains(dep.project.id),
                                        onCheckedChange = { on ->
                                            checked = if (on) checked + dep.project.id else checked - dep.project.id
                                        },
                                        enabled = !locked && !working,
                                        colors = CheckboxDefaults.colors(checkedColor = obsi.accent),
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(dep.project.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                                        val badge = when {
                                            dep.alreadyInstalled -> stringResourceCompat(R.string.deps_installed)
                                            dep.required -> stringResourceCompat(R.string.deps_required)
                                            else -> stringResourceCompat(R.string.deps_optional)
                                        }
                                        val parent = dep.parentTitle?.let { " · " + stringResourceCompat(R.string.deps_required_by, it) } ?: ""
                                        Text(
                                            "${dep.version.versionNumber} · $badge$parent",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = obsi.textDim,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    error?.let { Text(it, color = obsi.danger, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
        },
        confirmButton = {
            ObsiButton(
                text = stringResourceCompat(R.string.deps_install),
                enabled = !working && !loading,
                onClick = {
                    working = true
                    error = null
                    scope.launch {
                        try {
                            val active = app.instances.active() ?: throw IllegalStateException("no active instance")
                            app.modrinth.forInstance(active.versionId)
                            app.modrinth.installVersion(project, version, explicit = true) { onProgress(it) }
                            val names = StringBuilder(project.title)
                            candidates
                                .filter { it.required || checked.contains(it.project.id) }
                                .filterNot { it.alreadyInstalled }
                                .forEach { dep ->
                                    runCatching {
                                        app.modrinth.installVersion(dep.project, dep.version, explicit = false) { }
                                    }
                                    names.append(", ").append(dep.project.title)
                                }
                            onInstalled(names.toString())
                        } catch (e: Exception) {
                            error = e.message
                            working = false
                        }
                    }
                },
            )
        },
        dismissButton = {
            ObsiTextButton(stringResourceCompat(R.string.cancel), onClick = onDismiss, enabled = !working)
        },
    )
}

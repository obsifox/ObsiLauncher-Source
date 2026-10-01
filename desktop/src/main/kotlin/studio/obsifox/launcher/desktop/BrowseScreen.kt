package studio.obsifox.launcher.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import studio.obsifox.launcher.core.instance.Instance
import studio.obsifox.launcher.core.modrinth.MrVersion
import studio.obsifox.launcher.core.modrinth.Project
import studio.obsifox.launcher.core.modrinth.ProjectHit
import studio.obsifox.launcher.core.modrinth.ProjectKind
import studio.obsifox.launcher.core.modrinth.SortIndex

@Composable
fun BrowseScreen(initial: Screen.Browse) {
    val app = LocalApp.current
    val instances by app.core.instances.flow.collectAsState()
    val scope = rememberCoroutineScope()

    var kind by remember(initial) { mutableStateOf(initial.kind) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(SortIndex.DOWNLOADS) }
    var targetId by remember(initial) { mutableStateOf(initial.instanceId ?: app.selectedInstance()?.id) }
    var compatibleOnly by remember { mutableStateOf(true) }
    val target = instances.firstOrNull { it.id == targetId }

    var hits by remember { mutableStateOf<List<ProjectHit>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    suspend fun load(offset: Int) {
        loading = true
        error = null
        try {
            val useFilter = compatibleOnly && target != null && kind != ProjectKind.MODPACK
            val loaders = if (useFilter && kind == ProjectKind.MOD) app.core.content.compatibleLoaders(target!!) else emptyList()
            val res = app.core.modrinth.search(
                query.trim(), kind, gameVersion = if (useFilter) target!!.mcVersion else null, loaders = loaders, sort = sort, limit = 20, offset = offset,
            )
            hits = if (offset == 0) res.hits else hits + res.hits
            total = res.totalHits
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: app.s["error"]
        } finally {
            loading = false
        }
    }

    // debounce typing, reload immediately on any other change
    LaunchedEffect(query, kind, sort, targetId, compatibleOnly, reloadKey) {
        delay(if (query.isEmpty()) 0 else 350)
        load(0)
    }

    var versionPicker by remember { mutableStateOf<ProjectHit?>(null) }

    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(t("browse_title")) {
            Dim("modrinth.com")
        }
        LabeledField(
            t("search"), query, { query = it }, hint = t("search_hint"),
            trailing = { Icon(ObsiIcons.Search, null, tint = Obsi.textDim) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ProjectKind.entries.filter { it != ProjectKind.DATAPACK }.forEach { k ->
                FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(t("type_${k.apiType}")) })
            }
            Box(Modifier.weight(1f))
            Box(Modifier.width(190.dp)) {
                DropdownField(t("sort_by"), t("sort_${sort.api}"), SortIndex.entries.map { it.name to t("sort_${it.api}") }, { sort = SortIndex.valueOf(it) })
            }
        }
        if (kind != ProjectKind.MODPACK) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(300.dp)) {
                    DropdownField(
                        t("target_profile"), target?.let { "${it.name} · ${it.subtitle}" } ?: "—",
                        instances.map { it.id to "${it.name} · ${it.subtitle}" }, { targetId = it },
                    )
                }
                if (target != null) {
                    androidx.compose.material3.Checkbox(compatibleOnly, { compatibleOnly = it })
                    Dim(t("filter_compatible"))
                }
            }
        }

        val listState = rememberLazyListState()
        when {
            error != null -> EmptyState(ObsiIcons.Warning, t("error"), error) { SoftButton(t("retry"), { reloadKey++ }, icon = ObsiIcons.Refresh) }
            hits.isEmpty() && loading -> Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) { CircularProgressIndicator(color = Obsi.orange) }
            hits.isEmpty() -> EmptyState(ObsiIcons.Search, t("no_results"))
            else -> LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(hits, key = { it.projectId }) { hit ->
                    ProjectRow(hit, target, kind, onInstall = {
                        scope.launch {
                            if (kind == ProjectKind.MODPACK) { versionPicker = hit; return@launch }
                            if (target == null) { app.toast(app.s["need_profile"]); return@launch }
                            try {
                                val project = app.core.modrinth.project(hit.projectId)
                                app.installContent(target, project, null)
                            } catch (e: Exception) { app.toast(e.message ?: app.s["error"]) }
                        }
                    }, onVersions = { versionPicker = hit })
                }
                item {
                    if (hits.size < total) {
                        Box(Modifier.fillMaxWidth().padding(12.dp), Alignment.Center) {
                            if (loading) CircularProgressIndicator(color = Obsi.orange)
                            else SoftButton(t("load_more"), { scope.launch { load(hits.size) } })
                        }
                    }
                }
            }
        }
    }

    versionPicker?.let { hit -> VersionPickerDialog(hit, if (kind == ProjectKind.MODPACK) null else target, onDismiss = { versionPicker = null }) }
}

@Composable
private fun ProjectRow(hit: ProjectHit, target: Instance?, kind: ProjectKind, onInstall: () -> Unit, onVersions: () -> Unit) {
    ObsiCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RemoteImage(hit.iconUrl, hit.title, 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(hit.title, color = Obsi.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Dim(tf("by_author", hit.author))
                }
                Dim(hit.description, size = 13, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill(tf("downloads_count", formatCount(hit.downloads)), Obsi.green)
                    hit.displayCategories.take(3).forEach { Pill(it.replaceFirstChar { c -> c.uppercase() }, Obsi.purple) }
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (kind == ProjectKind.MODPACK) PrimaryButton(t("choose_version"), onVersions, icon = ObsiIcons.Download)
                else {
                    PrimaryButton(t("install"), onInstall, icon = ObsiIcons.Download, enabled = target != null)
                    SoftButton(t("choose_version"), onVersions, enabled = target != null)
                }
            }
        }
    }
}

@Composable
private fun VersionPickerDialog(hit: ProjectHit, target: Instance?, onDismiss: () -> Unit) {
    val app = LocalApp.current
    var project by remember { mutableStateOf<Project?>(null) }
    var versions by remember { mutableStateOf<List<MrVersion>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(hit.projectId, target?.id) {
        try {
            project = app.core.modrinth.project(hit.projectId)
            val kind = ProjectKind.fromApi(hit.projectType)
            val loaders = if (target != null && kind == ProjectKind.MOD) app.core.content.compatibleLoaders(target) else emptyList()
            versions = app.core.modrinth.versions(hit.projectId, loaders, if (target != null) listOf(target.mcVersion) else emptyList())
        } catch (e: Exception) { error = e.message ?: app.s["error"] }
    }
    WideDialog(tf("pick_version_title", hit.title), onDismiss, width = 640.dp) {
        when {
            error != null -> Text(error!!, color = Obsi.red)
            versions == null -> Dim(t("loading"))
            versions!!.isEmpty() -> Text(tf("no_compatible", target?.let { "${it.mcVersion} ${it.loader.display}" } ?: ""), color = Obsi.textDim)
            else -> LazyColumn(Modifier.padding(0.dp).fillMaxWidth().androidxHeight(320), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(versions!!.take(80), key = { it.id }) { v ->
                    ObsiCard(Modifier.fillMaxWidth(), onClick = {
                        val p = project ?: return@ObsiCard
                        onDismiss()
                        if (ProjectKind.fromApi(p.projectType) == ProjectKind.MODPACK) app.installModpack(p, v)
                        else if (target != null) app.installContent(target, p, v)
                    }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(v.name.ifBlank { v.versionNumber }, color = Obsi.text, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Dim(tf("version_meta", v.gameVersions.take(4).joinToString(", ") + if (v.gameVersions.size > 4) "…" else "", v.loaders.joinToString(", ")))
                            }
                            Pill(v.versionType, when (v.versionType) { "release" -> Obsi.green; "beta" -> Obsi.yellow; else -> Obsi.red })
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.androidxHeight(dp: Int): Modifier = this.then(Modifier.height(dp.dp))

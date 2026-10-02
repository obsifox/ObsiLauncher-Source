package studio.obsifox.launcher.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class NavItem(val key: String, val icon: ImageVector, val target: Screen, val matches: (Screen) -> Boolean)

@Composable
fun App(app: AppController) {
    val strings by app.strings.collectAsState()
    val settings by app.core.settings.flow.collectAsState()
    val backdrop by app.backdrop.collectAsState()
    val wizard by app.wizardOpen.collectAsState()

    // committed appearance: theme + (optionally) the wallpaper's own colours
    LaunchedEffect(settings.theme, settings.adaptColors, backdrop.palette, wizard) {
        if (!wizard) Obsi.apply(ThemeId.of(settings.theme), if (settings.adaptColors) backdrop.palette else null)
    }
    val fullscreen by app.fullscreen.collectAsState()
    LaunchedEffect(fullscreen) { setFullscreen(fullscreen) }

    CompositionLocalProvider(LocalApp provides app, LocalStrings provides strings) {
        ObsiTheme(strings.lang) { Root(app) }
    }
}

@Composable
private fun Root(app: AppController) {
    val gatePassed by app.gatePassed.collectAsState()
    val wizard by app.wizardOpen.collectAsState()
    val crash by app.crash.collectAsState()

    Box(Modifier.fillMaxSize().background(Obsi.bg0)) {
        if (!gatePassed) GateScreen() else Shell(app)
        if (gatePassed && wizard) FirstRunWizard()
    }

    crash?.let { c ->
        AlertDialog(
            onDismissRequest = { app.crash.value = null },
            title = { Text(t("crash_title")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tf("crash_text", c.instance.name, c.exitCode), color = Obsi.textDim)
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF0A0712)).padding(10.dp)) {
                        c.tail.takeLast(10).forEach { line ->
                            val bad = "/ERROR]" in line || "Exception" in line || "Caused by" in line || "crashed" in line
                            Text(
                                line, color = if (bad) Obsi.red else Color(0xFFD6D0E8), fontSize = 11.sp, lineHeight = 15.sp, maxLines = 2,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { app.crash.value = null }) { Text(t("close"), color = Obsi.orange) } },
            dismissButton = { TextButton(onClick = { c.logPath?.let { SystemOpen.open(it.parent) }; app.crash.value = null }) { Text(t("view_log")) } },
            containerColor = Obsi.bg2, shape = RoundedCornerShape(20.dp),
        )
    }
}

@Composable
private fun Shell(app: AppController) {
    val screen by app.screen.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { app.toasts.collect { snackbar.showSnackbar(it) } }

    if (screen == Screen.Home) {
        Scaffold(containerColor = Color.Transparent, snackbarHost = { SnackbarHost(snackbar) }) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) { HomeScreen() }
        }
        return
    }

    val nav = remember {
        listOf(
            NavItem("nav_home", ObsiIcons.Home, Screen.Home) { it is Screen.Home },
            NavItem("nav_instances", ObsiIcons.Apps, Screen.Instances) { it is Screen.Instances || it is Screen.InstanceDetail },
            NavItem("nav_browse", ObsiIcons.Explore, Screen.Browse()) { it is Screen.Browse },
            NavItem("nav_accounts", ObsiIcons.Person, Screen.Accounts) { it is Screen.Accounts },
            NavItem("nav_console", ObsiIcons.Terminal, Screen.Console) { it is Screen.Console },
            NavItem("nav_settings", ObsiIcons.Settings, Screen.Settings) { it is Screen.Settings },
        )
    }
    val sessions by app.sessions.collectAsState()

    Scaffold(containerColor = Obsi.bg0, snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        Row(Modifier.fillMaxSize().padding(pad)) {
            // ------------------------------------------------------------------ sidebar
            Column(
                Modifier.width(210.dp).fillMaxHeight().background(Obsi.bg1).padding(vertical = 20.dp, horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    FoxMark(30.dp)
                    Column(Modifier.padding(horizontal = 10.dp)) {
                        Text("Obsi", color = Obsi.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Launcher", color = Obsi.orange, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    }
                }
                nav.forEach { item ->
                    val selected = item.matches(screen)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (selected) Obsi.bg3 else Color.Transparent)
                            .clickable { app.go(item.target) }.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(item.icon, null, tint = if (selected) Obsi.orange else Obsi.textDim, modifier = Modifier.size(22.dp))
                        Text(t(item.key), Modifier.padding(horizontal = 12.dp).weight(1f), color = if (selected) Obsi.text else Obsi.textDim,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        if (item.key == "nav_console" && sessions.isNotEmpty()) Box(Modifier.size(8.dp).clip(CircleShape).background(Obsi.green))
                    }
                }
                Box(Modifier.weight(1f))
                Text("v${app.build.version}", color = Obsi.textDim, fontSize = 11.sp, modifier = Modifier.padding(start = 12.dp))
            }

            // ------------------------------------------------------------------ content + task bar
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (val s = screen) {
                        Screen.Home -> HomeScreen()
                        Screen.Instances -> InstancesScreen()
                        is Screen.InstanceDetail -> InstanceDetailScreen(s.id, s.tab)
                        is Screen.Browse -> BrowseScreen(s)
                        Screen.Accounts -> AccountsScreen()
                        Screen.Settings -> SettingsScreen()
                        Screen.Console -> ConsoleScreen()
                    }
                }
                TaskBar(app)
            }
        }
    }
}

@Composable
internal fun TaskBar(app: AppController) {
    val tasks by app.tasks.collectAsState()
    val strings by app.strings.collectAsState()
    if (tasks.isEmpty()) return
    Column(Modifier.fillMaxWidth().background(Obsi.bg1).padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tasks.takeLast(3).forEach { task ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row {
                        Text(task.title, color = Obsi.text, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        val detail = when (task.status) {
                            TaskStatus.RUNNING -> task.progress?.let { strings.stage(it) } ?: ""
                            TaskStatus.DONE -> strings["task_done"]
                            TaskStatus.FAILED -> strings["task_failed"] + ": " + (task.error ?: "")
                            TaskStatus.CANCELLED -> strings["cancel"]
                        }
                        Text("  ·  $detail", color = if (task.status == TaskStatus.FAILED) Obsi.red else Obsi.textDim, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    when (task.status) {
                        TaskStatus.RUNNING -> {
                            val f = task.progress?.fraction ?: -1f
                            if (f in 0f..1f) LinearProgressIndicator(progress = { f }, Modifier.fillMaxWidth(), color = Obsi.orange, trackColor = Obsi.bg3)
                            else LinearProgressIndicator(Modifier.fillMaxWidth(), color = Obsi.orange, trackColor = Obsi.bg3)
                        }
                        TaskStatus.DONE -> LinearProgressIndicator(progress = { 1f }, Modifier.fillMaxWidth(), color = Obsi.green, trackColor = Obsi.bg3)
                        TaskStatus.FAILED -> LinearProgressIndicator(progress = { 1f }, Modifier.fillMaxWidth(), color = Obsi.red, trackColor = Obsi.bg3)
                        TaskStatus.CANCELLED -> {}
                    }
                }
                if (task.status == TaskStatus.RUNNING) IconAction(ObsiIcons.Close, strings["task_cancel"], { app.cancelTask(task.id) })
                else if (task.status == TaskStatus.FAILED) IconAction(ObsiIcons.Close, strings["close"], { app.dismissTask(task.id) })
            }
        }
    }
}

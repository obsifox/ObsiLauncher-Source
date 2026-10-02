package studio.obsifox.launcher.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import studio.obsifox.launcher.core.launch.GameSession

@Composable
fun ConsoleScreen() {
    val app = LocalApp.current
    val sessions by app.sessions.collectAsState()
    var selectedId by remember { mutableStateOf<String?>(null) }
    val current: Pair<String, GameSession>? = sessions.entries.firstOrNull { it.key == selectedId }?.toPair() ?: sessions.entries.firstOrNull()?.toPair()

    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(t("console_title"))
        if (current == null) {
            EmptyState(ObsiIcons.Terminal, t("console_empty"))
            return@Column
        }
        if (sessions.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sessions.forEach { (id, s) -> FilterChip(selected = id == current.first, onClick = { selectedId = id }, label = { Text(s.instance.name) }) }
            }
        }
        val session = current.second
        val lines by session.lines.collectAsState()
        val running by session.running.collectAsState()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(if (running) tf("console_running", session.pid) else t("game_exited"), if (running) obsi().good else obsi().textDim)
            Dim(session.instance.name, modifier = Modifier.weight(1f))
            SoftButton(t("copy_log"), { copyToClipboard(lines.joinToString("\n")); app.toast(app.s["copied"]) }, icon = ObsiIcons.Copy)
            SoftButton(t("open_folder"), { SystemOpen.open(app.core.paths.gameDir(session.instance.id)) }, icon = ObsiIcons.Folder)
            if (running) SoftButton(t("kill"), { session.stop() }, icon = ObsiIcons.Stop, danger = true)
        }
        // game logs are LTR text whatever the UI language is
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            val state = rememberLazyListState()
            LaunchedEffect(lines.size) { if (lines.isNotEmpty()) state.scrollToItem(lines.size - 1) }
            SelectionContainer(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Color(0xFF0A0712)).padding(12.dp), state = state) {
                    items(lines.size) { i ->
                        val l = lines[i]
                        val color = when {
                            "/ERROR]" in l || l.startsWith("Exception") || l.contains("Caused by") -> obsi().bad
                            "/WARN]" in l -> obsi().warn
                            else -> Color(0xFFD6D0E8)
                        }
                        Text(l, color = color, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}

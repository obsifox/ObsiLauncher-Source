package studio.obsifox.launcher.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * About screen — brand identity only. It intentionally carries no third-party
 * credits, links or source names apart from the single MobileGlues renderer note.
 */
@Composable
fun AboutScreen() {
    val app = LocalApp.current
    val o = obsi()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ObsiCard(Modifier.fillMaxWidth(), color = o.glassHigh) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 42.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(o.accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    FoxMark(56.dp, tint = o.accent)
                }
                Text("ObsiLauncher", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, color = o.text)
                Text(
                    "v${app.build.version}${if (app.build.commit != "dev") " · ${app.build.commit}" else ""}",
                    color = o.accent, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                )
                Dim("Multi-profile Minecraft: Java Edition launcher", size = 13)
            }
        }

        ObsiCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("about_credits"), style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = o.text)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(4.dp)).background(o.accent))
                    Column {
                        Text("MobileGlues", color = o.text, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Dim(t("about_mobileglues"), size = 12)
                    }
                }
            }
        }

        ObsiCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("about_free_title"), style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = o.text)
                Text(t("about_disclaimer"), color = o.textDim, fontSize = 12.sp)
            }
        }
    }
}

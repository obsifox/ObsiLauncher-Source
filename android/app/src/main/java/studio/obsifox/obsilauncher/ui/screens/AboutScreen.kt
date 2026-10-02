package studio.obsifox.obsilauncher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import studio.obsifox.obsilauncher.BuildConfig
import studio.obsifox.obsilauncher.R
import studio.obsifox.obsilauncher.app
import studio.obsifox.obsilauncher.ui.components.GlassCard
import studio.obsifox.obsilauncher.ui.components.KeyValueRow
import studio.obsifox.obsilauncher.ui.theme.LocalObsi

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val obsi = LocalObsi.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        GlassCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(88.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResourceCompat(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResourceCompat(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = obsi.textDim,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResourceCompat(R.string.about_free),
                    style = MaterialTheme.typography.titleMedium,
                    color = obsi.accent,
                )
            }
        }

        GlassCard(modifier = Modifier.padding(top = 14.dp)) {
            Text(
                stringResourceCompat(R.string.about_credit),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResourceCompat(R.string.about_license),
                style = MaterialTheme.typography.bodyMedium,
                color = obsi.textDim,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        GlassCard(modifier = Modifier.padding(top = 14.dp)) {
            Text(
                stringResourceCompat(R.string.settings_runtime),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            val pack = context.app.settings.runtimePack.collectAsState().value
            KeyValueRow("Runtime", pack.ifBlank { "—" })
            val game = context.app.gameManager
            val gameState by game.state.collectAsState()
            KeyValueRow("Game", gameState.name.lowercase().replace('_', ' '))
            KeyValueRow("Package", BuildConfig.APPLICATION_ID)
        }
    }
}

@Composable
internal fun stringResourceCompat(id: Int, vararg args: Any): String =
    androidx.compose.ui.res.stringResource(id, *args)

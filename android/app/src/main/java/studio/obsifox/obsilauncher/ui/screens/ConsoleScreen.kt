package studio.obsifox.obsilauncher.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import studio.obsifox.obsilauncher.R
import studio.obsifox.obsilauncher.app
import studio.obsifox.obsilauncher.core.game.GameState
import studio.obsifox.obsilauncher.ui.components.GlassCard
import studio.obsifox.obsilauncher.ui.components.ObsiButton
import studio.obsifox.obsilauncher.ui.theme.LocalObsi

@Composable
fun ConsoleScreen(onClose: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.app
    val obsi = LocalObsi.current

    val log by app.gameManager.log.collectAsState()
    val state by app.gameManager.state.collectAsState()
    val exitCode by app.gameManager.exitCode.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 24.dp),
    ) {
        GlassCard {
            Row {
                Text(
                    text = when (state) {
                        GameState.RUNNING -> stringResourceCompat(R.string.console_running)
                        GameState.PREPARING -> stringResourceCompat(R.string.home_launching, "")
                        else -> stringResourceCompat(R.string.console_stopped)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state == GameState.RUNNING) obsi.accent else obsi.textDim,
                    modifier = Modifier.weight(1f),
                )
                if (state == GameState.RUNNING || state == GameState.PREPARING) {
                    ObsiButton(
                        text = stringResourceCompat(R.string.console_stop),
                        onClick = { app.gameManager.stop() },
                        danger = true,
                    )
                }
                studio.obsifox.obsilauncher.ui.components.ObsiTextButton(
                    text = "✕",
                    onClick = onClose,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            exitCode?.let { code ->
                if (state == GameState.EXITED) {
                    Text(
                        stringResourceCompat(R.string.console_exit_code, code),
                        style = MaterialTheme.typography.bodyMedium,
                        color = obsi.textDim,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.weight(1f)) {
            if (log.isBlank()) {
                Text(stringResourceCompat(R.string.console_empty), color = obsi.textDim)
            } else {
                Text(
                    text = log,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFD8E4D0),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

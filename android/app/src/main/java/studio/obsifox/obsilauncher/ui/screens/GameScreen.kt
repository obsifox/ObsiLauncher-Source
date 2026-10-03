package studio.obsifox.obsilauncher.ui.screens

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import studio.obsifox.obsilauncher.R
import studio.obsifox.obsilauncher.app
import studio.obsifox.obsilauncher.core.game.GameState
import studio.obsifox.obsilauncher.ui.theme.LocalObsi

/**
 * v1.13.0 — the missing link between PLAY and the JVM.
 *
 * The ZalithLauncher/PojavLauncher model boots the game JVM INSIDE the
 * launcher process and the game renders into a SurfaceView this screen
 * owns. PLAY only prepares the session; THIS screen hands the Surface to
 * GameManager.beginGame() the moment the holder reports it ready. While
 * the game runs, a minimal overlay (state + stop + collapsible log) stays
 * available on top of the render surface.
 */
@Composable
fun GameScreen(onExitRequest: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val obsi = LocalObsi.current

    val state by app.gameManager.state.collectAsState()
    val log by app.gameManager.log.collectAsState()
    var surface by remember { mutableStateOf<Surface?>(null) }
    var showLog by remember { mutableStateOf(false) }

    // the JVM boots exactly once, when the surface first exists
    LaunchedEffect(surface) {
        surface?.let { app.gameManager.beginGame(context, it) }
    }

    // when the session ends (exit code already recorded by GameManager),
    // come back to the shell
    LaunchedEffect(state) {
        if (state == GameState.EXITED) onExitRequest()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            surface = holder.surface
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            // the JVM owns this process; a destroyed surface
                            // means the session is already over
                        }
                    })
                    setZOrderMediaOverlay(false)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // ---- overlay -------------------------------------------------------
        Column(
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when (state) {
                        GameState.PREPARING -> stringResourceCompat(R.string.game_booting)
                        GameState.RUNNING -> stringResourceCompat(R.string.game_running)
                        else -> stringResourceCompat(R.string.game_booting)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFF4EFEA),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0x8C140F0C))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Spacer(Modifier.weight(1f))
                OverlayPill(stringResourceCompat(R.string.game_log)) { showLog = !showLog }
                Spacer(Modifier.width(8.dp))
                OverlayPill(stringResourceCompat(R.string.console_stop), danger = true) {
                    app.gameManager.stop(context)
                }
            }
            if (showLog) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xB3140F0C)),
                ) {
                    Text(
                        text = log.ifBlank { "…" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFD8E4D0),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayPill(text: String, danger: Boolean = false, onClick: () -> Unit) {
    val obsi = LocalObsi.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (danger) obsi.danger else Color(0x8C140F0C))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

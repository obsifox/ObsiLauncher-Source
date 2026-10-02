package studio.obsifox.launcher.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import javafx.scene.paint.Color as FxColor
import java.nio.file.Path

/**
 * Muted, looping video background (used only for the latest Minecraft release).
 *
 * Rendered through JavaFX Media inside a Swing panel so no extra process or
 * native player is needed. Everything is defensive: if JavaFX cannot start
 * (headless machine, missing codecs) the composable simply draws nothing and
 * the still wallpaper underneath remains visible.
 */
@Composable
fun VideoBackground(file: Path, modifier: Modifier = Modifier, muted: Boolean = true) {
    // Copy the media to a plain path URI (spaces etc. are handled by URI)
    val uri = remember(file) {
        try {
            val normalized = file.toAbsolutePath().normalize()
            normalized.toUri().toString()
        } catch (_: Exception) {
            null
        }
    } ?: return

    val holder = remember(file) { VideoHolder(uri) }

    SwingPanel(
        modifier = modifier,
        factory = { holder.panel },
        update = { holder.setMuted(muted) },
    )

    DisposableEffect(file) {
        onDispose { holder.release() }
    }
}

private class VideoHolder(uri: String) {
    val panel: JFXPanel
    private var player: MediaPlayer? = null

    init {
        panel = JFXPanel()
        // Build the FX scene on the JavaFX thread; keep it fully non-fatal.
        runCatching {
            Platform.setImplicitExit(false)
            Platform.runLater {
                runCatching {
                    val media = Media(uri)
                    val p = MediaPlayer(media)
                    p.isMute = true
                    p.cycleCount = MediaPlayer.INDEFINITE
                    p.volume = 0.6
                    // start a few seconds into the trailer (per product spec)
                    p.setOnReady {
                        runCatching {
                            val d = media.duration
                            if (d.toSeconds() > 14.0) p.seek(javafx.util.Duration.seconds(8.0))
                        }
                        p.play()
                    }
                    val view = MediaView(p)
                    view.isPreserveRatio = true
                    val root = Group(view)
                    val scene = Scene(root, FxColor.TRANSPARENT)
                    scene.fill = FxColor.TRANSPARENT
                    // keep the video filling the panel
                    scene.widthProperty().addListener { _, _, w -> view.fitWidth = w.toDouble() }
                    scene.heightProperty().addListener { _, _, h -> view.fitHeight = h.toDouble() }
                    panel.scene = scene
                    view.fitWidth = panel.width.coerceAtLeast(1).toDouble()
                    view.fitHeight = panel.height.coerceAtLeast(1).toDouble()
                    player = p
                }
            }
        }
    }

    fun setMuted(muted: Boolean) {
        runCatching {
            Platform.runLater { player?.isMute = muted }
        }
    }

    fun release() {
        runCatching {
            Platform.runLater {
                runCatching {
                    player?.stop()
                    player?.dispose()
                    player = null
                }
            }
        }
    }
}

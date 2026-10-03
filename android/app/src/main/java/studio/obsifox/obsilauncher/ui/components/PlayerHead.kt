package studio.obsifox.obsilauncher.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import studio.obsifox.obsilauncher.core.accounts.Account
import studio.obsifox.obsilauncher.core.net.Http
import studio.obsifox.obsilauncher.core.Paths
import java.io.File

/**
 * A real Minecraft player head — the face (8x8) plus the hat layer, scaled
 * with nearest-neighbour so the pixels stay crisp.
 *
 * Skin resolution order: a locally picked skin -> the account's Mojang skin
 * (Microsoft accounts, cached on disk) -> the bundled default Steve skin,
 * so the head is always the real deal, never a letter placeholder.
 */
@Composable
fun PlayerHead(account: Account?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var skin by remember(account?.id, account?.skinPath) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(account?.id, account?.skinPath, account?.accessToken) {
        skin = withContext(Dispatchers.IO) {
            HeadRenderer.skinFor(context, account)
        }?.asImageBitmap()
    }

    val bmp = skin
    Canvas(modifier) {
        if (bmp != null) {
            drawFace(bmp, hat = true)
        } else {
            // graceful placeholder while the skin loads
            drawRoundRect(
                Color(0xFF2A2226),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.2f),
            )
        }
    }
}

/** draw the 8x8 face + hat overlay of a 64x64 skin, filling the canvas. */
private fun DrawScope.drawFace(skin: ImageBitmap, hat: Boolean) {
    val w = size.width.toInt().coerceAtLeast(1)
    val h = size.height.toInt().coerceAtLeast(1)
    drawImage(
        skin,
        srcOffset = androidx.compose.ui.unit.IntOffset(8, 8),
        srcSize = androidx.compose.ui.unit.IntSize(8, 8),
        dstOffset = androidx.compose.ui.unit.IntOffset(0, 0),
        dstSize = androidx.compose.ui.unit.IntSize(w, h),
        filterQuality = FilterQuality.None,
    )
    if (hat) {
        drawImage(
            skin,
            srcOffset = androidx.compose.ui.unit.IntOffset(40, 8),
            srcSize = androidx.compose.ui.unit.IntSize(8, 8),
            dstOffset = androidx.compose.ui.unit.IntOffset(0, 0),
            dstSize = androidx.compose.ui.unit.IntSize(w, h),
            filterQuality = FilterQuality.None,
        )
    }
    // hairline rim so light heads stay visible on light cards
    drawRect(
        Color(0x22000000),
        topLeft = Offset.Zero,
        size = Size(size.width, size.height),
    )
}

/** decode + cache helpers, all offline-tolerant. */
private object HeadRenderer {

    fun skinFor(context: Context, account: Account?): Bitmap? {
        // 1. local custom skin picked in the accounts screen
        account?.skinPath?.takeIf { it.isNotBlank() }
            ?.let { File(it) }?.takeIf { it.isFile }
            ?.let { runCatching { decode(it) }.getOrNull() }
            ?.let { return it }

        // 2. the Microsoft profile skin, cached per account id
        if (account?.isMicrosoft == true && account.accessToken.isNotBlank()) {
            val cache = File(File(Paths.obsiRoot(context), "heads"), "${account.id}.png")
            if (cache.isFile) {
                runCatching { decode(cache) }.getOrNull()?.let { return it }
            }
            try {
                val profile = Http.authedJson(
                    "https://api.minecraftservices.com/minecraft/profile",
                    account.accessToken,
                )
                if (profile != null) {
                    val arr = org.json.JSONObject(profile).optJSONArray("skins")
                    if (arr != null && arr.length() > 0) {
                        var url = arr.optJSONObject(0)?.optString("url") ?: ""
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            if (o.optString("state") == "ACTIVE") { url = o.optString("url"); break }
                        }
                        if (url.isNotBlank()) {
                            cache.parentFile?.mkdirs()
                            Http.downloadToFile(url, cache) { _, _ -> }
                            runCatching { decode(cache) }.getOrNull()?.let { return it }
                        }
                    }
                }
            } catch (_: Exception) {
                // offline / token expired — fall through to Steve
            }
        }

        // 3. the bundled default Steve skin — always available
        return defaultSteve(context)
    }

    fun defaultSteve(context: Context): Bitmap? = runCatching {
        context.assets.open("skins/steve.png").use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    private fun decode(f: File): Bitmap? =
        BitmapFactory.decodeFile(f.absolutePath)
}

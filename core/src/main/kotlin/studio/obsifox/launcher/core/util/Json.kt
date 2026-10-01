package studio.obsifox.launcher.core.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Lenient parser for remote documents (Mojang, Modrinth, loader meta ...). */
val RemoteJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

/** Pretty writer for our own state files. */
val StateJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    prettyPrint = true
    encodeDefaults = true
    explicitNulls = false
}

fun JsonElement.str(key: String): String? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull

/** Writes atomically so a crash never leaves a half-written state file. */
fun writeTextAtomic(path: Path, text: String) {
    Files.createDirectories(path.parent)
    val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
    Files.writeString(tmp, text)
    try {
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: Exception) {
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
    }
}

package studio.obsifox.launcher.core.util

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID

fun ByteArray.toHex(): String {
    val sb = StringBuilder(size * 2)
    for (b in this) {
        sb.append("0123456789abcdef"[(b.toInt() shr 4) and 0xF]).append("0123456789abcdef"[b.toInt() and 0xF])
    }
    return sb.toString()
}

fun digestFile(path: Path, algorithm: String): String {
    val md = MessageDigest.getInstance(algorithm)
    Files.newInputStream(path).use { input ->
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
    }
    return md.digest().toHex()
}

fun sha1Hex(path: Path): String = digestFile(path, "SHA-1")
fun sha512Hex(path: Path): String = digestFile(path, "SHA-512")
fun sha1Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-1").digest(bytes).toHex()

/** UUID of an offline-mode player, identical to what a vanilla server in offline mode computes. */
fun offlineUuid(name: String): UUID = UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(Charsets.UTF_8))

fun UUID.undashed(): String = toString().replace("-", "")

/** Parses a UUID with or without dashes. */
fun parseUuid(s: String): UUID =
    if (s.contains('-')) UUID.fromString(s)
    else UUID.fromString(s.replaceFirst(Regex("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)"), "$1-$2-$3-$4-$5"))

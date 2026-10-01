package studio.obsifox.launcher.core.util

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

enum class OsName(val mojang: String) { WINDOWS("windows"), LINUX("linux"), MACOS("osx") }

/** Host operating system / CPU detection plus the keys Mojang uses for it. */
object Platform {
    val os: OsName = System.getProperty("os.name", "").lowercase().let {
        when {
            "win" in it -> OsName.WINDOWS
            "mac" in it || "darwin" in it -> OsName.MACOS
            else -> OsName.LINUX
        }
    }

    /** "x86_64" | "arm64" | "x86" (anything else is passed through). */
    val arch: String = System.getProperty("os.arch", "").lowercase().let {
        when (it) {
            "amd64", "x86_64", "x64" -> "x86_64"
            "aarch64", "arm64" -> "arm64"
            "x86", "i386", "i686" -> "x86"
            else -> it
        }
    }

    val osVersion: String = System.getProperty("os.version", "")
    val is64Bit: Boolean get() = arch != "x86"
    val pathSeparator: String = File.pathSeparator

    /** Key used by Mojang's `java-runtime/all.json`; null when Mojang ships no runtime for this machine. */
    val mojangRuntimeKey: String? = when (os) {
        OsName.WINDOWS -> when (arch) { "x86_64" -> "windows-x64"; "arm64" -> "windows-arm64"; "x86" -> "windows-x86"; else -> null }
        OsName.LINUX -> when (arch) { "x86_64" -> "linux"; "x86" -> "linux-i386"; else -> null }
        OsName.MACOS -> when (arch) { "arm64" -> "mac-os-arm64"; "x86_64" -> "mac-os"; else -> null }
    }

    fun defaultDataDir(): Path {
        System.getenv("OBSI_HOME")?.takeIf { it.isNotBlank() }?.let { return Paths.get(it) }
        val home = System.getProperty("user.home")
        return when (os) {
            OsName.WINDOWS -> Paths.get(System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: "$home/AppData/Roaming", "ObsiLauncher")
            OsName.MACOS -> Paths.get(home, "Library", "Application Support", "ObsiLauncher")
            OsName.LINUX -> Paths.get(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share", "ObsiLauncher")
        }
    }

    /** Total physical memory in MB (best effort, 0 when unknown). */
    fun totalMemoryMb(): Int = try {
        val bean = java.lang.management.ManagementFactory.getOperatingSystemMXBean()
        val m = bean.javaClass.methods.firstOrNull { it.name == "getTotalMemorySize" || it.name == "getTotalPhysicalMemorySize" }
        m?.isAccessible = true
        ((m?.invoke(bean) as? Long ?: 0L) / (1024 * 1024)).toInt()
    } catch (_: Throwable) {
        0
    }
}

package studio.obsifox.obsilauncher.core.game

import android.content.Context
import studio.obsifox.obsilauncher.core.ObsiSettings
import studio.obsifox.obsilauncher.core.Paths
import studio.obsifox.obsilauncher.core.accounts.Account
import studio.obsifox.obsilauncher.core.instance.Instance
import studio.obsifox.obsilauncher.core.jni.ObsiBridge
import studio.obsifox.obsilauncher.core.runtime.Pack
import org.json.JSONObject
import java.io.File

/**
 * Builds the full JVM command line for an instance and spawns it
 * through [ObsiBridge.forkAndExec] (system-linker trick).
 */
object LaunchPipeline {

    data class Request(
        val instance: Instance,
        val account: Account,
        val pack: Pack,
        val memoryMb: Int,
        val extraJvmArgs: List<String>,
    )

    fun buildClasspath(context: Context, versionId: String): List<File> {
        val json = versionJsonOrInherited(context, versionId)
        val out = ArrayList<File>()
        out += clientJarChain(context, versionId)
        val libRoot = Paths.librariesRoot(context)
        for (lib in VersionInstallerCollector.collect(context, versionId)) {
            val file = File(libRoot, lib.second)
            if (file.isFile) out += file
        }
        return out
    }

    /** client jar chain entry: the child jar (OptiFine jar = fully patched) */
    private fun clientJarChain(context: Context, versionId: String): File =
        VersionInstaller.clientJar(context, versionId)

    private fun versionJsonOrInherited(context: Context, versionId: String): JSONObject =
        runCatching { JSONObject(VersionInstaller.versionJson(context, versionId).readText()) }.getOrDefault(JSONObject())

    fun argv(context: Context, req: Request): List<String> {
        val versionId = req.instance.versionId
        val json = versionJsonOrInherited(context, versionId)
        val versionDir = Paths.versionDir(context, versionId)
        val assetsIndex = json.optJSONObject("assetIndex")?.optString("id")?.takeIf { it.isNotEmpty() }
            ?: req.instance.mcVersion

        val jvm = ArrayList<String>()
        jvm += req.pack.jvmArgs.filter { !it.startsWith("-Xmx") && !it.startsWith("-Xms") }
        val mx = if (req.memoryMb > 0) req.memoryMb else 2048
        jvm += "-Xmx${mx}M"
        jvm += "-Xms${(mx / 2).coerceAtLeast(512)}M"
        jvm += "-XX:+UseG1GC"
        jvm += "-XX:G1NewSizePercent=20"
        jvm += "-XX:G1ReservePercent=20"
        jvm += "-XX:MaxGCPauseMillis=50"
        jvm += "-XX:G1HeapRegionSize=32M"
        jvm += req.extraJvmArgs.filter { it.isNotBlank() }
        jvm += "-Djava.library.path=${VersionInstaller.nativesDir(context, versionId).absolutePath}:${req.pack.libDirs.joinToString(":") { it.absolutePath }}"
        jvm += "-Dorg.lwjgl.system.allocator=jemalloc"
        jvm += "-Dio.netty.tryReflectionSetAccessible=true"
        jvm += "-Dfml.earlyprogresswindow=false"
        jvm += "-cp"
        jvm += buildClasspath(context, versionId).joinToString(":") { it.absolutePath }
        jvm += json.optString("mainClass").ifEmpty { "net.minecraft.client.main.Main" }

        val game = ArrayList<String>()
        game += "--username"
        game += req.account.name
        game += "--uuid"
        game += req.account.offlineUuid()
        game += "--accessToken"
        game += req.account.accessToken.ifEmpty { "0" }
        game += "--clientId"
        game += "\${clientid}"
        game += "--xuid"
        game += "\${auth_xuid}"
        game += "--userType"
        game += if (req.account.isMicrosoft) "msa" else "legacy"
        game += "--versionIndex"
        game += versionId
        game += "--versionType"
        game += "ObsiLauncher"
        game += "--version"
        game += versionId
        game += "--gameDir"
        game += versionDir.absolutePath
        game += "--assetsDir"
        game += Paths.assetsRoot(context).absolutePath
        game += "--assetIndex"
        game += assetsIndex
        game += "--width"
        game += "1280"
        game += "--height"
        game += "720"

        return jvm + game
    }

    fun env(context: Context, req: Request): List<String> {
        val versionDir = Paths.versionDir(context, req.instance.versionId)
        val libPath = listOf(
            VersionInstaller.nativesDir(context, req.instance.versionId).absolutePath,
            req.pack.libDirs.joinToString(":") { it.absolutePath },
        ).joinToString(":")
        return listOf(
            "HOME=${versionDir.absolutePath}",
            "JAVA_HOME=${req.pack.dir.absolutePath}",
            "PATH=${File(req.pack.dir, "bin").absolutePath}:/system/bin:/system/xbin",
            "LD_LIBRARY_PATH=$libPath",
            "TMPDIR=${versionDir.absolutePath}",
            "RENDERER=mobileglues",
        )
    }

    /** Spawn the game. Returns the child pid. */
    fun spawn(context: Context, req: Request): Int {
        val linker = if (android.os.Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) "/system/bin/linker64" else "/system/bin/linker"
        return ObsiBridge.forkAndExec(
            linker,
            req.pack.launcherSo.absolutePath,
            argv(context, req).toTypedArray(),
            env(context, req).toTypedArray(),
        )
    }
}

/** internal helper: gather classpath library paths from a version (inheriting parents) */
private object VersionInstallerCollector {
    fun collect(context: Context, versionId: String): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        val seen = HashSet<String>()
        fun addFrom(root: JSONObject) {
            val libs = root.optJSONArray("libraries") ?: return
            for (i in 0 until libs.length()) {
                val entry = libs.getJSONObject(i)
                if (!VersionInstaller.rulesAllow(entry.optJSONArray("rules"))) continue
                val coord = entry.optString("name")
                if (coord.isEmpty() || !seen.add(coord)) continue
                val path = entry.optJSONObject("downloads")?.optJSONObject("artifact")?.optString("path")
                    ?.takeIf { it.isNotEmpty() } ?: VersionInstaller.mavenPath(coord)
                out += coord to path
            }
        }
        val json = VersionInstaller.versionJson(context, versionId)
        if (!json.isFile) return out
        val root = runCatching { JSONObject(json.readText()) }.getOrNull() ?: return out
        val inherits = root.optString("inheritsFrom").takeIf { it.isNotEmpty() && it != versionId }
        if (inherits != null) {
            val parent = VersionInstaller.versionJson(context, inherits)
            if (parent.isFile) runCatching { addFrom(JSONObject(parent.readText())) }
        }
        addFrom(root)
        return out
    }
}

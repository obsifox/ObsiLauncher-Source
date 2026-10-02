package studio.obsifox.obsilauncher.core.game

import android.content.Context
import studio.obsifox.obsilauncher.core.ObsiSettings
import studio.obsifox.obsilauncher.core.Paths
import studio.obsifox.obsilauncher.core.accounts.Account
import studio.obsifox.obsilauncher.core.jni.ObsiBridge
import studio.obsifox.obsilauncher.core.runtime.Pack
import org.json.JSONObject
import java.io.File

/**
 * Builds the full JVM command line for the selected version and spawns it
 * through [ObsiBridge.forkAndExec] (system-linker trick).
 */
object LaunchPipeline {

    data class Request(
        val versionId: String,
        val account: Account,
        val pack: Pack,
        val memoryMb: Int,
        val extraJvmArgs: List<String>,
    )

    fun buildClasspath(context: Context, versionId: String): List<File> {
        val json = VersionInstaller.versionJson(context, versionId)
        val root = JSONObject(json.readText())
        val out = ArrayList<File>()
        out += VersionInstaller.clientJar(context, versionId)
        val libs = root.optJSONArray("libraries") ?: return out
        val libRoot = Paths.librariesRoot(context)
        for (i in 0 until libs.length()) {
            val entry = libs.getJSONObject(i)
            if (!VersionInstaller.rulesAllow(entry.optJSONArray("rules"))) continue
            val artifact = entry.optJSONObject("downloads")?.optJSONObject("artifact") ?: continue
            val path = artifact.optString("path").ifEmpty { VersionInstaller.mavenPath(entry.getString("name")) }
            val file = File(libRoot, path)
            if (file.isFile) out += file
        }
        return out
    }

    fun argv(context: Context, req: Request): List<String> {
        val json = JSONObject(VersionInstaller.versionJson(context, req.versionId).readText())
        val versionDir = Paths.versionDir(context, req.versionId)
        val natives = VersionInstaller.nativesDir(context, req.versionId)
        val assetsIndex = json.optJSONObject("assetIndex")?.optString("id")?.takeIf { it.isNotEmpty() }
            ?: req.versionId

        val jvm = ArrayList<String>()
        jvm += req.pack.jvmArgs
        jvm += "-Xmx${req.memoryMb}M"
        jvm += "-Xms${(req.memoryMb / 2).coerceAtLeast(512)}M"
        jvm += "-XX:+UseG1GC"
        jvm += "-XX:G1NewSizePercent=20"
        jvm += "-XX:G1ReservePercent=20"
        jvm += "-XX:MaxGCPauseMillis=50"
        jvm += "-XX:G1HeapRegionSize=32M"
        jvm += req.extraJvmArgs.filter { it.isNotBlank() }
        jvm += "-Djava.library.path=${natives.absolutePath}:${req.pack.libDirs.joinToString(":") { it.absolutePath }}"
        jvm += "-Dorg.lwjgl.system.allocator=jemalloc"
        jvm += "-Dio.netty.tryReflectionSetAccessible=true"
        jvm += "-Dfml.earlyprogresswindow=false"
        jvm += "-cp"
        jvm += buildClasspath(context, req.versionId).joinToString(":") { it.absolutePath }
        jvm += json.optString("mainClass").ifEmpty { "net.minecraft.client.main.Main" }

        val game = ArrayList<String>()
        game += "--username"
        game += req.account.name
        game += "--uuid"
        game += req.account.offlineUuid()
        game += "--accessToken"
        game += "0"
        game += "--clientId"
        game += "\${clientid}"
        game += "--xuid"
        game += "\${auth_xuid}"
        game += "--userType"
        game += "legacy"
        game += "--versionIndex"
        game += req.versionId
        game += "--versionType"
        game += "ObsiLauncher"
        game += "--version"
        game += req.versionId
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
        val versionDir = Paths.versionDir(context, req.versionId)
        val libPath = listOf(
            VersionInstaller.nativesDir(context, req.versionId).absolutePath,
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

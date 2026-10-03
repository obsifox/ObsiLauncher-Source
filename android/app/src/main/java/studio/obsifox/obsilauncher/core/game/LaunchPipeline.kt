package studio.obsifox.obsilauncher.core.game

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import studio.obsifox.obsilauncher.BuildConfig
import studio.obsifox.obsilauncher.core.Paths
import studio.obsifox.obsilauncher.core.accounts.Account
import studio.obsifox.obsilauncher.core.instance.Instance
import studio.obsifox.obsilauncher.core.runtime.ObsiComponents
import java.io.File

/**
 * Builds the full JVM command line for an instance, following the proven
 * ZalithLauncher/PojavLauncher argument shape:
 *
 *   -Djava.library.path, MIO lib patcher agent, log4j hardening config,
 *   caciocavallo AWT bootclasspath, our LWJGL 3 fork first on the classpath,
 *   ${…} JSON value substitution for both JVM and game arguments.
 *
 * The JVM itself is booted IN-PROCESS (see [GameManager] + net.kdt.pojavlaunch
 * vendored natives) instead of a fork/exec child.
 */
object LaunchPipeline {

    data class Request(
        val instance: Instance,
        val account: Account,
        val runtimeName: String,        // "Internal-21" …
        val memoryMb: Int,
        val extraJvmArgs: List<String>,
    )

    // ------------------------------------------------------------------ paths

    fun componentDir(context: Context, id: String) = Paths.componentDir(context, id)

    fun nativesDir(context: Context, versionId: String) = VersionInstaller.nativesDir(context, versionId)

    /** the app's own extracted native libs (liblwjgl*.so, libgl4es_114.so, …) */
    fun appNativeDir(context: Context): String {
        val appInfo = context.applicationInfo
        return appInfo.nativeLibraryDir
    }

    // ------------------------------------------------------------- classpath

    /**
     * Classpath = our LWJGL 3 fork (fat jar with the GLFW replacement) FIRST —
     * exactly like Zalith's Tools.getLWJGL3ClassPath() — then the game's own
     * client jar + collected libraries (inheriting parent versions).
     */
    fun buildClasspath(context: Context, versionId: String): String {
        val out = ArrayList<String>()
        val lwjgl3 = componentDir(context, ObsiComponents.LWJGL3)
        lwjgl3.listFiles()?.forEach { f ->
            if (f.name.endsWith(".jar")) out += f.absolutePath
        }
        out += VersionInstaller.clientJar(context, versionId).absolutePath
        val libRoot = Paths.librariesRoot(context)
        for (lib in VersionInstallerCollector.collect(context, versionId)) {
            val file = File(libRoot, lib.second)
            if (file.isFile) out += file.absolutePath
        }
        return out.joinToString(":")
    }

    // ------------------------------------------------------------ arg helpers

    /** ${key} substitution over Mojang's argument templates. */
    private fun insertJsonValues(arg: String, values: Map<String, String>): String {
        var s = arg
        for ((k, v) in values) s = s.replace("\${$k}", v)
        return s
    }

    /** version json, walking inheritsFrom */
    private fun versionJsonOrInherited(context: Context, versionId: String): JSONObject =
        runCatching { JSONObject(VersionInstaller.versionJson(context, versionId).readText()) }.getOrDefault(JSONObject())

    fun javaMajor(context: Context, versionId: String): Int {
        val json = versionJsonOrInherited(context, versionId)
        val direct = json.optJSONObject("javaVersion")?.optInt("majorVersion", 0) ?: 0
        if (direct > 0) return direct
        val inherits = json.optString("inheritsFrom").takeIf { it.isNotEmpty() && it != versionId }
        if (inherits != null) return javaMajor(context, inherits)
        return 8
    }

    // ------------------------------------------------------------------ argv

    fun argv(context: Context, req: Request): List<String> {
        val versionId = req.instance.versionId
        val json = versionJsonOrInherited(context, versionId)
        val versionDir = Paths.versionDir(context, versionId)
        val gameDir = File(versionDir, "game").takeIf { it.isDirectory } ?: versionDir
        val libsHome = Paths.librariesRoot(context).absolutePath
        val nativesDir = nativesDir(context, versionId).absolutePath
        val nativeAppDir = appNativeDir(context)
        val lwjgl3Dir = componentDir(context, ObsiComponents.LWJGL3)

        val jvm = ArrayList<String>()
        jvm += "-Xms${(req.memoryMb / 2).coerceAtLeast(512)}M"
        jvm += "-Xmx${req.memoryMb}M"
        jvm += "-XX:ActiveProcessorCount=" + Runtime.getRuntime().availableProcessors()

        // ---- Zalith getJavaArgs() core properties ---------------------------
        // (missing ones were exactly why the JVM refused to boot in-process:
        //  without -Djava.home the JLI bootstrap mis-resolves its own home,
        //  and without the FORK launch mechanism jspawnhelper does not exist
        //  on Android, killing every subprocess the game tries to spawn)
        val jreHome = ObsiComponents.runtimeHome(context, req.runtimeName).absolutePath
        val cacheDir = context.cacheDir.absolutePath
        jvm += "-Djava.home=$jreHome"
        jvm += "-Djava.io.tmpdir=$cacheDir"
        jvm += "-Duser.home=${gameDir.absolutePath}"
        jvm += "-Dos.name=Linux"
        jvm += "-Dos.version=Android-" + android.os.Build.VERSION.RELEASE
        jvm += "-Dpojav.path.minecraft=" + Paths.gamesRoot(context).absolutePath
        jvm += "-Dpojav.path.game.home=" + Paths.gamesRoot(context).absolutePath
        jvm += "-Dlog4j2.formatMsgNoLookups=true"
        jvm += "-Djdk.lang.Process.launchMechanism=FORK"
        jvm += "-Dorg.lwjgl.vulkan.libname=libvulkan.so"
        jvm += "-Dnet.minecraft.clientmodname=ObsiLauncher"
        jvm += "-Dfml.earlyprogresswindow=false"
        jvm += "-Dloader.disable_forked_guis=true"
        jvm += "-Dsodium.checks.issue2561=false"
        // GLFW stub geometry (the vendored input bridge reads these)
        val metrics = context.resources.displayMetrics
        jvm += "-Dglfwstub.windowWidth=${metrics.widthPixels}"
        jvm += "-Dglfwstub.windowHeight=${metrics.heightPixels}"
        jvm += "-Dglfwstub.initEgl=false"
        // DNS for the JVM resolver (Zalith resolv.conf parity)
        runCatching {
            val resolv = File(context.filesDir, "resolv.conf")
            if (!resolv.isFile) {
                // net.dns* are hidden APIs — resolve them reflectively, with
                // public resolvers as the fallback
                val sp = Class.forName("android.os.SystemProperties")
                val get = sp.getMethod("get", String::class.java, String::class.java)
                val dns1 = runCatching {
                    get.invoke(null, "net.dns1", "8.8.8.8") as String
                }.getOrDefault("8.8.8.8")
                val dns2 = runCatching {
                    get.invoke(null, "net.dns2", "8.8.4.4") as String
                }.getOrDefault("8.8.4.4")
                resolv.writeText("nameserver $dns1\nnameserver $dns2\n")
            }
            jvm += "-Dext.net.resolvPath=${resolv.absolutePath}"
        }

        // java args the user typed on the instance / settings, parsed crudely
        req.extraJvmArgs.filter { it.isNotBlank() }.forEach { jvm += it }

        // MIO lib patcher (vendor parity: fixes sodium/misc native loading)
        val mio = File(componentDir(context, ObsiComponents.COMPONENTS), "MioLibPatcher.jar")
        if (mio.isFile) jvm += "-javaagent:${mio.absolutePath}"

        // log4j hardening configs from the components store
        val isPre112 = runCatching { versionLess(versionId, "1.12") }.getOrDefault(false)
        val log4j = File(
            componentDir(context, ObsiComponents.COMPONENTS),
            if (isPre112) "log4j-rce-patch-1.7.xml" else "log4j-rce-patch-1.12.xml"
        )
        if (log4j.isFile) jvm += "-Dlog4j.configurationFile=${log4j.absolutePath}"

        // version-specific natives + our own libs first on java.library.path
        jvm += "-Djava.library.path=$nativesDir:$nativeAppDir"
        jvm += "-Djna.boot.library.path=$nativesDir"

        // freetype from our own libs (font rendering 1.17+)
        jvm += "-Dorg.lwjgl.freetype.libname=$nativeAppDir/libfreetype.so"

        // renderer selection: LWJGL loads the desktop-GL provider directly
        jvm += "-Dorg.lwjgl.opengl.libname=libgl4es_114.so"

        // caciocavallo AWT backend (needed by old versions / some mods)
        jvm += cacioArgs(context, req)

        // Mojang's arguments.jvm (1.13+) with our substitution map
        val varArgMap = mapOf(
            "classpath_separator" to ":",
            "library_directory" to libsHome,
            "version_name" to versionId,
            "natives_directory" to nativesDir,
        )
        val argList = json.optJSONObject("arguments")
        argList?.optJSONArray("jvm")?.let { arr ->
            for (i in 0 until arr.length()) {
                val a = arr.opt(i)
                if (a is String) {
                    var s = a
                    if (s.startsWith("-DignoreList=")) s = "$s,$versionId.jar"
                    jvm += insertJsonValues(s, varArgMap)
                }
            }
        }

        jvm += "-cp"
        jvm += buildClasspath(context, versionId)
        val mainClass = json.optString("mainClass").ifEmpty { "net.minecraft.client.main.Main" }
        // Zalith LaunchArgs: export the main-class package on Java 9+ so
        // Forge/NeoForge bootstrap launchers can reflect into it
        val major = javaMajor(context, versionId)
        if (major > 8) {
            val pkg = mainClass.substringBeforeLast('.')
            if (pkg.isNotEmpty()) jvm += "--add-exports=$pkg/$pkg=ALL-UNNAMED"
        }
        jvm += mainClass

        // ---- game args ----
        val game = ArrayList<String>()
        val gameVar = mapOf(
            "auth_session" to req.account.accessToken,
            "auth_access_token" to req.account.accessToken,
            "auth_player_name" to req.account.name,
            "auth_uuid" to req.account.offlineUuid().replace("-", ""),
            "auth_xuid" to "0",
            "auth_clientid" to "obsilauncher",
            "user_properties" to "{}",
            "user_type" to if (req.account.isMicrosoft) "msa" else "legacy",
            "version_name" to (json.optString("inheritsFrom").takeIf { it.isNotEmpty() } ?: versionId),
            "launcher_name" to "ObsiLauncher",
            "launcher_version" to BuildConfig.VERSION_NAME,
            "version_type" to "ObsiLauncher",
            "assets_root" to Paths.assetsRoot(context).absolutePath,
            "game_assets" to Paths.assetsRoot(context).absolutePath,
            "assets_index_name" to (json.optJSONObject("assetIndex")?.optString("id")?.takeIf { it.isNotEmpty() } ?: req.instance.mcVersion),
            "game_directory" to gameDir.absolutePath,
            "clientid" to "obsilauncher",
        )
        var haveGameArgs = false
        argList?.optJSONArray("game")?.let { arr ->
            for (i in 0 until arr.length()) {
                val a = arr.opt(i)
                if (a is String) {
                    game += insertJsonValues(a, gameVar)
                    haveGameArgs = true
                }
            }
        }
        if (!haveGameArgs) {
            // pre-1.13 minecraftArguments string
            json.optString("minecraftArguments").takeIf { it.isNotEmpty() }?.let { s ->
                game += insertJsonValues(s, gameVar).split(" ").filter { it.isNotBlank() }
                haveGameArgs = true
            }
        }
        if (!haveGameArgs) {
            game += listOf(
                "--username", req.account.name,
                "--uuid", req.account.offlineUuid(),
                "--accessToken", req.account.accessToken.ifEmpty { "0" },
                "--userType", if (req.account.isMicrosoft) "msa" else "legacy",
                "--version", versionId,
                "--gameDir", gameDir.absolutePath,
                "--assetsDir", Paths.assetsRoot(context).absolutePath,
                "--assetIndex", gameVar["assets_index_name"] ?: req.instance.mcVersion,
            )
        }

        return jvm + game
    }

    private fun cacioArgs(context: Context, req: Request): List<String> {
        val cacio8 = componentDir(context, ObsiComponents.CACIO)
        val cacio17 = componentDir(context, ObsiComponents.CACIO17)
        val java8 = req.runtimeName.endsWith("8") || req.runtimeName.endsWith("Internal-8")
        val cacioDir = if (java8) cacio8 else cacio17
        if (!cacioDir.isDirectory || cacioDir.listFiles().isNullOrEmpty()) return emptyList()
        val args = ArrayList<String>()
        args += "-Djava.awt.headless=false"
        args += "-Dcacio.managed.screensize=800x480"
        args += "-Dcacio.font.fontmanager=sun.awt.X11FontManager"
        args += "-Dcacio.font.fontscaler=sun.font.FreetypeFontScaler"
        args += "-Dswing.defaultlaf=javax.swing.plaf.nimbus.NimbusLookAndFeel"
        if (java8) {
            args += "-Dawt.toolkit=net.java.openjdk.cacio.ctc.CTCToolkit"
            args += "-Djava.awt.graphicsenv=net.java.openjdk.cacio.ctc.CTCGraphicsEnvironment"
        } else {
            args += "-Dawt.toolkit=com.github.caciocavallosilano.cacio.ctc.CTCToolkit"
            args += "-Djava.awt.graphicsenv=com.github.caciocavallosilano.cacio.ctc.CTCGraphicsEnvironment"
            val agent = File(cacio17, "cacio-agent.jar")
            if (agent.isFile) args += "-javaagent:${agent.absolutePath}"
            args += listOf(
                "--add-exports=java.desktop/java.awt=ALL-UNNAMED",
                "--add-exports=java.desktop/java.awt.peer=ALL-UNNAMED",
                "--add-exports=java.desktop/sun.awt.image=ALL-UNNAMED",
                "--add-exports=java.desktop/sun.java2d=ALL-UNNAMED",
                "--add-exports=java.desktop/java.awt.dnd.peer=ALL-UNNAMED",
                "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
                "--add-exports=java.desktop/sun.awt.event=ALL-UNNAMED",
                "--add-exports=java.desktop/sun.awt.datatransfer=ALL-UNNAMED",
                "--add-exports=java.desktop/sun.font=ALL-UNNAMED",
                "--add-exports=java.base/sun.security.action=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.java2d=ALL-UNNAMED",
                "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
                "--add-opens=java.base/java.net=ALL-UNNAMED",
            )
        }
        val sb = StringBuilder("-Xbootclasspath/").append(if (java8) "p" else "a")
        cacioDir.listFiles()?.forEach { if (it.name.endsWith(".jar")) sb.append(":").append(it.absolutePath) }
        args += sb.toString()
        return args
    }

    // ------------------------------------------------------------------- env

    fun env(context: Context, req: Request, jreHome: String): List<String> {
        val versionId = req.instance.versionId
        val versionDir = Paths.versionDir(context, versionId)
        val gameDir = File(versionDir, "game").takeIf { it.isDirectory } ?: versionDir
        val nativeAppDir = appNativeDir(context)
        val libDirs = ArrayList<String>()
        libDirs += nativesDir(context, versionId).absolutePath
        libDirs += jreHome + "/" + ToolsHome.dirNameHomeJre(jreHome) + "/jli"
        libDirs += jreHome + "/" + ToolsHome.dirNameHomeJre(jreHome)
        libDirs += nativeAppDir
        val ldPath = libDirs.joinToString(":") + ":/system/lib64:/vendor/lib64"
        return listOf(
            "POJAV_NATIVEDIR=$nativeAppDir",
            "JAVA_HOME=$jreHome",
            "HOME=${gameDir.absolutePath}",
            "TMPDIR=${context.cacheDir.absolutePath}",
            "LD_LIBRARY_PATH=$ldPath",
            "PATH=$jreHome/bin:/system/bin:/system/xbin:/vendor/bin",
            "POJAV_RENDERER=opengles2",
            "LIBGL_ES=2",
            "LIBGL_MIPMAP=3",
            "LIBGL_NOERROR=1",
            "LIBGL_NOINTOVLHACK=1",
            "LIBGL_NORMALIZE=1",
            "AWTSTUB_WIDTH=800",
            "AWTSTUB_HEIGHT=480",
            "MOD_ANDROID_RUNTIME=",
            "FORCE_VSYNC=false",
        )
    }

    /** crude semver-ish compare for 1.x.y version ids */
    fun versionLess(id: String, than: String): Boolean {
        fun nums(v: String) = v.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val a = nums(id)
        val b = nums(than)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x < y
        }
        return false
    }

    /** path helpers shared with the GameManager */
    object ToolsHome {
        fun dirNameHomeJre(jreHome: String): String {
            // Zalith relocateLibPath: some JREs nest natives under lib/<arch>
            for (arch in arches()) {
                val f = File(jreHome, "lib/$arch")
                if (f.exists() && f.isDirectory) return "lib/$arch"
            }
            return "lib"
        }

        fun arches(): List<String> {
            val is64 = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
            return if (is64) listOf("arm64", "aarch64") else listOf("arm", "aarch32")
        }
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

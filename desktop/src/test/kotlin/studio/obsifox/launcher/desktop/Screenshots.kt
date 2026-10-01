@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package studio.obsifox.launcher.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import studio.obsifox.launcher.core.LauncherCore
import studio.obsifox.launcher.core.LauncherPaths
import studio.obsifox.launcher.core.auth.Account
import studio.obsifox.launcher.core.auth.AccountType
import studio.obsifox.launcher.core.instance.Instance
import studio.obsifox.launcher.core.instance.LoaderType
import studio.obsifox.launcher.core.modrinth.ContentManifest
import studio.obsifox.launcher.core.modrinth.InstalledEntry
import studio.obsifox.launcher.core.modrinth.ProjectKind
import studio.obsifox.launcher.core.util.StateJson
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Renders the real UI headlessly (no window, no GPU) into PNG files, using a throw-away data directory with demo profiles.
 *   java -cp "<test runtime classpath>" studio.obsifox.launcher.desktop.ScreenshotsKt <outDir> [en|fa] [network:true|false]
 */
fun main(args: Array<String>) {
    val out = Paths.get(args.getOrElse(0) { "build/screens" })
    val lang = args.getOrElse(1) { "en" }
    val network = args.getOrElse(2) { "true" } == "true"
    Files.createDirectories(out)

    val home = Files.createTempDirectory("obsi-shots")
    val core = LauncherCore(LauncherPaths(home), "0.1.0", null)
    val app = AppController(core, BuildInfo("0.1.0", null, "demo"))
    seed(core)
    core.settings.update { it.copy(language = lang, selectedInstanceId = "survival") }

    val pages = buildList<Pair<String, Screen>> {
        add("home" to Screen.Home)
        add("profiles" to Screen.Instances)
        add("profile-mods" to Screen.InstanceDetail("survival", 0))
        add("profile-settings" to Screen.InstanceDetail("survival", 3))
        if (network) add("modrinth" to Screen.Browse("survival", ProjectKind.MOD))
        add("accounts" to Screen.Accounts)
        add("settings" to Screen.Settings)
    }
    for ((name, screen) in pages) {
        app.go(screen)
        render(app, out.resolve("$name-$lang.png"), settleMs = if (name == "modrinth") 6000 else 1200)
        println("wrote $name-$lang.png")
    }
    kotlin.system.exitProcess(0)
}

private fun render(app: AppController, file: Path, settleMs: Long) {
    val scene = ImageComposeScene(1180, 760, Density(1f)) { App(app) }
    try {
        val frames = (settleMs / 50).toInt().coerceAtLeast(10)
        for (i in 0..frames) {
            scene.render(i * 50_000_000L)
            Thread.sleep(50)
        }
        val img = scene.render((frames + 1) * 50_000_000L)
        Files.write(file, img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
    } finally {
        scene.close()
    }
}

private fun seed(core: LauncherCore) {
    val now = System.currentTimeMillis()
    val day = 86_400_000L
    core.instances.save(Instance("survival", "Survival 1.21", "1.21.1", LoaderType.FABRIC, "0.19.5", "fabric-loader-0.19.5-1.21.1", lastPlayedAt = now - 2 * day, playTimeSeconds = 3600 * 41 + 600))
    core.instances.save(Instance("pvp", "PvP Lab", "1.8.9", LoaderType.VANILLA, null, "1.8.9", lastPlayedAt = now - 9 * day, playTimeSeconds = 5400))
    core.instances.save(Instance("create", "Create: Above & Beyond", "1.20.1", LoaderType.FORGE, "47.4.10", "1.20.1-forge-47.4.10", playTimeSeconds = 0))
    core.instances.save(Instance("neo", "NeoForge Snapshot", "1.21.1", LoaderType.NEOFORGE, "21.1.252", "neoforge-21.1.252", lastPlayedAt = now - 30 * day))
    core.auth.addOffline("Steve")
    core.accounts.upsert(Account("ms-demo", AccountType.MICROSOFT, "ObsiFox", "11111111222233334444555555555555", mcAccessToken = "x", mcExpiresAt = now + day))
    core.settings.update { it.copy(selectedAccountId = "ms-demo") }

    val mods = core.paths.gameDir("survival").resolve("mods")
    Files.createDirectories(mods)
    val entries = mutableListOf<InstalledEntry>()
    for ((file, title, version) in listOf(Triple("sodium-fabric-0.6.0.jar", "Sodium", "0.6.0"), Triple("lithium-fabric-0.13.0.jar", "Lithium", "0.13.0"), Triple("iris-1.8.0.jar", "Iris Shaders", "1.8.0"), Triple("fabric-api-0.100.jar", "Fabric API", "0.100.0"))) {
        Files.write(mods.resolve(file), ByteArray(1_400_000 + file.length * 9000))
        entries += InstalledEntry(file, "mod", "p-$title", "v-$title", version, title)
    }
    Files.write(mods.resolve("my-private-mod.jar.disabled"), ByteArray(80_000))
    Files.writeString(core.paths.instanceDir("survival").resolve("obsi-content.json"), StateJson.encodeToString(ContentManifest.serializer(), ContentManifest(entries)))
}

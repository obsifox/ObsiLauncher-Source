package studio.obsifox.launcher.core

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assumptions.assumeTrue
import studio.obsifox.launcher.core.instance.LoaderType
import studio.obsifox.launcher.core.modrinth.ProjectKind
import studio.obsifox.launcher.core.modrinth.SortIndex
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Opt-in tests that talk to the real Mojang / Modrinth / Fabric / Forge servers:
 *   ./gradlew :core:test -Pnet=true --tests '*NetworkIntegrationTest*'
 * Game data goes to $OBSI_TEST_HOME (default: build/obsi-test-home) so repeated runs are fast.
 */
class NetworkIntegrationTest {
    private val enabled = System.getProperty("obsi.net") == "true"
    private val home: Path = Paths.get(System.getenv("OBSI_TEST_HOME") ?: "build/obsi-test-home").toAbsolutePath()
    private val core by lazy { LauncherCore(LauncherPaths(home), "test") }

    private fun log(s: String) = println("[it] $s")

    @Test
    fun mojangManifestAndLoaderLists() = runBlocking {
        assumeTrue(enabled)
        val m = core.mojang.manifest(force = true)
        log("latest release=${m.latest.release} snapshot=${m.latest.snapshot} total=${m.versions.size}")
        assertTrue(m.latest.release.isNotBlank() && m.versions.size > 100)

        for ((type, mc) in listOf(LoaderType.FABRIC to "1.21.1", LoaderType.QUILT to "1.21.1", LoaderType.FORGE to "1.20.1", LoaderType.NEOFORGE to "1.21.1", LoaderType.FORGE to "1.12.2")) {
            val v = core.loaders.versions(type, mc)
            log("$type $mc -> ${v.size} versions, first=${v.firstOrNull()}, recommended=${v.firstOrNull { it.recommended }}")
            assertTrue(v.isNotEmpty(), "$type has versions for $mc")
        }
        val latestMc = m.latest.release
        for (type in LoaderType.entries.filter { it != LoaderType.VANILLA }) {
            val v = core.loaders.versions(type, latestMc)
            log("$type for latest release $latestMc -> ${v.size} versions (first=${v.firstOrNull()?.version})")
        }
    }

    @Test
    fun modrinthSearchVersionsAndHashLookup() = runBlocking {
        assumeTrue(enabled)
        val res = core.modrinth.search("sodium", ProjectKind.MOD, "1.21.1", listOf("fabric"), sort = SortIndex.DOWNLOADS, limit = 5)
        log("search sodium -> total=${res.totalHits}, first=${res.hits.firstOrNull()?.title}")
        assertTrue(res.hits.isNotEmpty())
        val hit = res.hits.first()
        val versions = core.modrinth.versions(hit.projectId, listOf("fabric"), listOf("1.21.1"))
        log("versions=${versions.size}, newest=${versions.firstOrNull()?.versionNumber}")
        assertTrue(versions.isNotEmpty())
        val file = versions.first().primaryFile!!
        val byHash = core.modrinth.versionsByHashes(listOf(file.hashes.getValue("sha1")))
        assertTrue(byHash.values.any { it.id == versions.first().id })
        val packs = core.modrinth.search("", ProjectKind.MODPACK, "1.20.1", sort = SortIndex.DOWNLOADS, limit = 3)
        log("modpacks: " + packs.hits.joinToString { it.title })
        assertTrue(packs.hits.isNotEmpty())
        val p = core.modrinth.project(hit.slug)
        assertTrue(p.title.isNotBlank())
    }

    private suspend fun smokeLaunch(mc: String, loader: LoaderType, expect: String) {
        val inst = core.createInstance("Smoke $mc ${loader.name}", mc, loader, null) { u ->
            if (u.fraction >= 0 && (u.fraction * 100).toInt() % 25 == 0) log("  ${u.stage} ${(u.fraction * 100).toInt()}%") else if (u.fraction < 0) log("  ${u.stage}")
        }
        log("created ${inst.id}: launch id=${inst.launchVersionId}, loader=${inst.loaderVersion}")
        val account = core.auth.addOffline("Tester")
        // small heap: the CI/sandbox boxes are tiny, and the game dies at window creation anyway
        core.instances.save(inst.copy(maxMemoryMb = 640, minMemoryMb = 128))
        val prepared = core.prepare(inst.id, account) { }
        log("java=${prepared.command.executable}")
        Files.writeString(home.resolve("last-command.txt"), prepared.command.commandLine.joinToString("\n"))
        log("cmd=" + prepared.command.redacted(listOf()).take(300) + " ...")
        val session = core.start(prepared)
        val code = try {
            withTimeout(120_000) { session.exitCode.await() }
        } finally {
            session.stopAndWait()
        }
        val out = session.lines.value
        log("game exited with $code; ${out.size} lines; tail:")
        out.takeLast(12).forEach { log("   | $it") }
        assertTrue(out.any { it.contains(expect) }, "expected '$expect' in game output")
    }

    @Test
    fun vanillaInstallAndLaunch() = runBlocking {
        assumeTrue(enabled)
        smokeLaunch("1.21.1", LoaderType.VANILLA, "Setting user: Tester")
    }

    @Test
    fun fabricInstallAndLaunch() = runBlocking {
        assumeTrue(enabled)
        smokeLaunch("1.21.1", LoaderType.FABRIC, "Setting user: Tester")
    }

    @Test
    fun quiltInstallAndLaunch() = runBlocking {
        assumeTrue(enabled)
        smokeLaunch("1.21.1", LoaderType.QUILT, "Setting user: Tester")
    }

    @Test
    fun neoForgeInstallAndLaunch() = runBlocking {
        assumeTrue(enabled)
        smokeLaunch("1.21.1", LoaderType.NEOFORGE, "Tester")
    }

    @Test
    fun forgeInstallAndLaunch() = runBlocking {
        assumeTrue(enabled)
        smokeLaunch("1.20.1", LoaderType.FORGE, "Tester")
    }
}

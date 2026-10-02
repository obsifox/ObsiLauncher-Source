package studio.obsifox.launcher.core

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import studio.obsifox.launcher.core.auth.Account
import studio.obsifox.launcher.core.instance.Instance
import studio.obsifox.launcher.core.instance.Settings
import studio.obsifox.launcher.core.launch.LaunchBuilder
import studio.obsifox.launcher.core.loaders.LoaderService
import studio.obsifox.launcher.core.mojang.Maven
import studio.obsifox.launcher.core.mojang.OsRule
import studio.obsifox.launcher.core.mojang.Rule
import studio.obsifox.launcher.core.mojang.Rules
import studio.obsifox.launcher.core.mojang.VersionJson
import studio.obsifox.launcher.core.mojang.VersionResolver
import studio.obsifox.launcher.core.net.Mirrors
import studio.obsifox.launcher.core.util.RemoteJson
import studio.obsifox.launcher.core.util.offlineUuid
import studio.obsifox.launcher.core.util.undashed
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineTests {
    @Test
    fun offlineUuidMatchesVanillaServers() {
        // Known value: a vanilla offline-mode server gives "Notch" -> b50ad385-829d-3141-a216-7e7d7539ba7f
        assertEquals("b50ad385829d3141a2167e7d7539ba7f", offlineUuid("Notch").undashed())
    }

    @Test
    fun mavenPaths() {
        assertEquals("org/ow2/asm/asm/9.6/asm-9.6.jar", Maven.path("org.ow2.asm:asm:9.6"))
        assertEquals("org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-linux.jar", Maven.path("org.lwjgl:lwjgl:3.3.3:natives-linux"))
        assertEquals("a/b/c/1.0/c-1.0.zip", Maven.path("a.b:c:1.0@zip"))
        assertEquals("org.lwjgl:lwjgl:natives-linux", Maven.key("org.lwjgl:lwjgl:3.3.3:natives-linux"))
    }

    @Test
    fun rulesLastMatchingWins() {
        val rules = listOf(Rule("allow"), Rule("disallow", os = OsRule(name = "no-such-os")))
        assertTrue(Rules.allowed(rules))
        assertFalse(Rules.allowed(listOf(Rule("allow", os = OsRule(name = "no-such-os")))))
        assertTrue(Rules.allowed(listOf(Rule("allow", features = mapOf("x" to true))), mapOf("x" to true)))
        assertFalse(Rules.allowed(listOf(Rule("allow", features = mapOf("x" to true))), emptyMap()))
    }

    @Test
    fun asciiCheck() {
        assertTrue(studio.obsifox.launcher.core.util.Platform.isAscii("C:\\Users\\John\\AppData"))
        assertFalse(studio.obsifox.launcher.core.util.Platform.isAscii("C:\\Users\\\u06A9\u0627\u0631\u0628\u0631\\AppData"))
    }

    @Test
    fun splitArgsHonoursQuotes() {
        assertEquals(listOf("-Da=1", "-Db=two words", "-X"), LaunchBuilder.splitArgs("-Da=1 \"-Db=two words\"   -X"))
    }

    @Test
    fun neoForgePrefixes() {
        assertEquals("21.1.", LoaderService.neoForgePrefix("1.21.1"))
        assertEquals("21.0.", LoaderService.neoForgePrefix("1.21"))
        assertEquals("20.4.", LoaderService.neoForgePrefix("1.20.4"))
        assertEquals(null, LoaderService.neoForgePrefix("1.20.1"))
        assertEquals(null, LoaderService.neoForgePrefix("1.12.2"))
    }

    @Test
    fun loaderVersionOrdering() {
        val unsorted = listOf("0.20.0-beta.9", "0.24.0", "0.20.0-beta.10", "0.30.1-beta.4", "0.30.1", "0.9.0", "0.30.0")
        val sorted = unsorted.sortedWith { a, b -> LoaderService.compareVersions(b, a) }
        assertEquals(listOf("0.30.1", "0.30.1-beta.4", "0.30.0", "0.24.0", "0.20.0-beta.10", "0.20.0-beta.9", "0.9.0"), sorted)
        assertEquals(listOf("21.1.252", "21.1.10", "21.1.9"), listOf("21.1.9", "21.1.252", "21.1.10").sortedWith { a, b -> LoaderService.compareVersions(b, a) })
        assertTrue(LoaderService.compareVersions("47.4.10", "47.4.9") > 0)
    }

    @Test
    fun mirrorRewrite() {
        assertEquals("https://bmclapi2.bangbang93.com/maven/net/minecraft/x.jar", Mirrors.bmclapi("https://libraries.minecraft.net/net/minecraft/x.jar"))
        assertEquals(null, Mirrors.bmclapi("https://api.modrinth.com/v2/search"))
    }

    @Test
    fun inheritanceMergesLibrariesAndArguments() {
        val parent = RemoteJson.decodeFromString<VersionJson>(
            """{"id":"1.20.1","type":"release","mainClass":"net.minecraft.client.main.Main",
               "assetIndex":{"id":"5"},"downloads":{"client":{"url":"u","sha1":"s","size":1}},
               "javaVersion":{"component":"java-runtime-gamma","majorVersion":17},
               "libraries":[{"name":"a:b:1"},{"name":"c:d:1"}],
               "arguments":{"game":["--username","${'$'}{auth_player_name}"],"jvm":["-cp","${'$'}{classpath}"]}}""",
        )
        val child = RemoteJson.decodeFromString<VersionJson>(
            """{"id":"fabric-loader-0.15-1.20.1","inheritsFrom":"1.20.1","type":"release","mainClass":"net.fabricmc.loader.impl.launch.knot.KnotClient",
               "libraries":[{"name":"c:d:2"},{"name":"e:f:1"}],
               "arguments":{"game":[],"jvm":["-DFabricMcEmu= net.minecraft.client.main.Main "]}}""",
        )
        val rv = VersionResolver.resolve(listOf(parent, child))
        assertEquals("fabric-loader-0.15-1.20.1", rv.id)
        assertEquals("1.20.1", rv.rootId)
        assertEquals("net.fabricmc.loader.impl.launch.knot.KnotClient", rv.mainClass)
        assertEquals(listOf("a:b:1", "c:d:2", "e:f:1"), rv.libraries.map { it.name })
        assertEquals(3, rv.jvmArgs.size)
        assertEquals(17, rv.javaMajor)
        assertTrue(rv.inherited)
    }

    @Test
    fun lwjglEntriesListedSeveralTimesInOneVersionAllSurvive() {
        // MC 1.14-1.18 style: per module a mac-only 3.2.1 jar, the 3.2.2 jar and an entry with natives. (regression: no LWJGL on the classpath)
        fun lib(version: String, os: String?, natives: Boolean): String {
            val rules = if (os == null) "" else if (os == "!osx") ""","rules":[{"action":"allow"},{"action":"disallow","os":{"name":"osx"}}]""" else ""","rules":[{"action":"allow","os":{"name":"osx"}}]"""
            val nat = if (natives) ""","natives":{"linux":"natives-linux","windows":"natives-windows","osx":"natives-macos"}""" else ""
            return """{"name":"org.lwjgl:lwjgl-glfw:$version","downloads":{"artifact":{"path":"org/lwjgl/lwjgl-glfw/$version/lwjgl-glfw-$version.jar","url":"u","sha1":"s","size":1}}$rules$nat}"""
        }
        val json = """{"id":"1.16.5","type":"release","mainClass":"m","assetIndex":{"id":"1"},"libraries":[
            ${lib("3.2.1", "osx", false)},${lib("3.2.2", "!osx", false)},${lib("3.2.1", "osx", true)},${lib("3.2.2", "!osx", true)}]}"""
        val rv = VersionResolver.resolve(listOf(RemoteJson.decodeFromString<VersionJson>(json)))
        val cp = rv.libraries.mapNotNull { studio.obsifox.launcher.core.mojang.GameInstaller.classpathPath(it) }.distinct()
        if (studio.obsifox.launcher.core.util.Platform.os != studio.obsifox.launcher.core.util.OsName.MACOS) {
            assertEquals(listOf("org/lwjgl/lwjgl-glfw/3.2.2/lwjgl-glfw-3.2.2.jar"), cp)
            assertEquals(2, rv.libraries.size) // artifact entry + natives entry
        }
    }

    @Test
    fun nativesLibrariesKeepTheirMainJarOnTheClasspath() {
        // LWJGL 3.2.2 (MC 1.13-1.17): `natives` AND a regular artifact -> main jar needed (regression: GLFW ClassNotFound on 1.16.5)
        val both = RemoteJson.decodeFromString<studio.obsifox.launcher.core.mojang.Library>(
            """{"name":"org.lwjgl:lwjgl-glfw:3.2.2","downloads":{"artifact":{"path":"org/lwjgl/lwjgl-glfw/3.2.2/lwjgl-glfw-3.2.2.jar","url":"u","sha1":"s","size":1},
               "classifiers":{"natives-linux":{"path":"p","url":"u","sha1":"s","size":1}}},"natives":{"linux":"natives-linux"}}""",
        )
        assertEquals("org/lwjgl/lwjgl-glfw/3.2.2/lwjgl-glfw-3.2.2.jar", studio.obsifox.launcher.core.mojang.GameInstaller.classpathPath(both))
        // LWJGL 2 style: natives-only library -> not on the classpath
        val onlyNatives = RemoteJson.decodeFromString<studio.obsifox.launcher.core.mojang.Library>(
            """{"name":"org.lwjgl.lwjgl:lwjgl-platform:2.9.4-nightly-20150209","downloads":{"classifiers":{"natives-linux":{"path":"p","url":"u","sha1":"s","size":1}}},"natives":{"linux":"natives-linux"}}""",
        )
        assertEquals(null, studio.obsifox.launcher.core.mojang.GameInstaller.classpathPath(onlyNatives))
        // plain library without downloads block (Forge/Fabric style)
        val plain = RemoteJson.decodeFromString<studio.obsifox.launcher.core.mojang.Library>("""{"name":"net.fabricmc:fabric-loader:0.19.5","url":"https://maven.fabricmc.net/"}""")
        assertEquals("net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar", studio.obsifox.launcher.core.mojang.GameInstaller.classpathPath(plain))
    }

    @Test
    fun launchCommandContainsEverything() {
        val tmp = Files.createTempDirectory("obsi-test")
        val paths = LauncherPaths(tmp)
        val v = RemoteJson.decodeFromString<VersionJson>(
            """{"id":"1.21.1","type":"release","mainClass":"net.minecraft.client.main.Main","assetIndex":{"id":"17"},
               "libraries":[],"arguments":{
                 "game":["--username","${'$'}{auth_player_name}","--uuid","${'$'}{auth_uuid}","--userType","${'$'}{user_type}",
                   {"rules":[{"action":"allow","features":{"has_custom_resolution":true}}],"value":["--width","${'$'}{resolution_width}","--height","${'$'}{resolution_height}"]},
                   {"rules":[{"action":"allow","features":{"is_demo_user":true}}],"value":"--demo"}],
                 "jvm":["-Djava.library.path=${'$'}{natives_directory}","-cp","${'$'}{classpath}"]}}""",
        )
        val rv = VersionResolver.resolve(listOf(v))
        val inst = Instance(id = "t", name = "T", mcVersion = "1.21.1", width = 1280, height = 720, maxMemoryMb = 3000, jvmArgs = "-Dfoo=bar")
        val acc = Account("offline-1", "Steve", offlineUuid("Steve").undashed())
        val cmd = LaunchBuilder.build(rv, paths, emptyList(), inst, acc, Settings(), Paths.get("/usr/bin/java"), "0.0.0")
        val a = cmd.args
        assertTrue("-Xmx3000M" in a)
        assertTrue("-Dfoo=bar" in a)
        assertTrue(a.indexOf("net.minecraft.client.main.Main") > a.indexOf("-cp"))
        assertEquals("Steve", a[a.indexOf("--username") + 1])
        assertEquals("legacy", a[a.indexOf("--userType") + 1])
        assertEquals("1280", a[a.indexOf("--width") + 1])
        assertFalse("--demo" in a)
        assertTrue(a.single { it.startsWith("-Djava.library.path=") }.endsWith("natives"))
        // the client jar must be the last classpath entry, as ONE absolute path (regression: Path is Iterable<Path>)
        val cp = a[a.indexOf("-cp") + 1].split(java.io.File.pathSeparator)
        assertEquals(paths.versionJar("1.21.1").toString(), cp.last())
        assertEquals(1, cp.size)
    }
}

class LogFormatTests {
    @Test
    fun log4jEventsBecomePlainLines() {
        val f = studio.obsifox.launcher.core.launch.Log4jXmlFormatter()
        val raw = listOf(
            "<log4j:Event logger=\"fgo\" timestamp=\"1790868300302\" level=\"INFO\" thread=\"Render thread\">",
            "  <log4j:Message><![CDATA[Setting user: Tester]]></log4j:Message>",
            "</log4j:Event>",
            "plain line from a mod",
            "<log4j:Event logger=\"x\" timestamp=\"1790868300302\" level=\"ERROR\" thread=\"main\">",
            "  <log4j:Message><![CDATA[boom]]></log4j:Message>",
            "  <log4j:Throwable><![CDATA[java.lang.Error: x",
            "\tat a.b(C.java:1)]]></log4j:Throwable>",
            "</log4j:Event>",
        )
        val out = raw.flatMap { f.feed(it) }
        assertEquals(5, out.size)
        assertTrue(out[0].endsWith("[Render thread/INFO]: Setting user: Tester"))
        assertEquals("plain line from a mod", out[1])
        assertTrue(out[2].endsWith("[main/ERROR]: boom"))
        assertEquals("java.lang.Error: x", out[3])
    }
}

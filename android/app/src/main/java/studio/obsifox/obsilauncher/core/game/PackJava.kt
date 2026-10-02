package studio.obsifox.obsilauncher.core.game

import android.content.Context
import studio.obsifox.obsilauncher.core.Paths
import studio.obsifox.obsilauncher.core.jni.ObsiBridge
import studio.obsifox.obsilauncher.core.runtime.Pack
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.Semaphore

/**
 * Runs a JVM command through a runtime pack, using the same system-linker
 * trick as the game itself (/system/bin/linker64 + the pack's launcher .so).
 * Used for mod-loader installers (Forge / NeoForge / OptiFine patcher).
 */
object PackJava {

    data class Result(val exitCode: Int, val output: String)

    private val mutex = Semaphore(1, true)

    /**
     * Spawn [args] on the pack's JVM, capture stdout+stderr line by line and
     * block until the process exits. Only one child can run at a time — the
     * JNI bridge exposes exactly one log pipe.
     */
    fun run(context: Context, pack: Pack, args: List<String>, workDir: File, onLine: (String) -> Unit = {}): Result {
        mutex.acquire()
        try {
            ObsiBridge.chdir(workDir.absolutePath)
            val linker = if (android.os.Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) "/system/bin/linker64" else "/system/bin/linker"
            val pid = ObsiBridge.forkAndExec(
                linker,
                pack.launcherSo.absolutePath,
                args.toTypedArray(),
                env(context, pack).toTypedArray(),
            )
            if (pid <= 0) return Result(-1, "failed to spawn the JVM (check the runtime pack)")
            val sb = StringBuilder()
            val reader = Thread {
                val fd = ObsiBridge.takeLogFd()
                if (fd < 0) return@Thread
                runCatching {
                    android.os.ParcelFileDescriptor.adoptFd(fd).use { pfd ->
                        FileInputStream(pfd.fileDescriptor).use { input ->
                            val buf = ByteArray(8 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                if (n > 0) {
                                    val text = String(buf, 0, n)
                                    synchronized(sb) { sb.append(text) }
                                    text.lineSequence().filter { it.isNotBlank() }.forEach(onLine)
                                }
                            }
                        }
                    }
                }
            }.apply { isDaemon = true; start() }
            val code = ObsiBridge.waitPid(pid)
            reader.join(3000)
            return Result(code, synchronized(sb) { sb.toString() })
        } finally {
            mutex.release()
        }
    }

    fun env(context: Context, pack: Pack): List<String> {
        val libPath = pack.libDirs.joinToString(":") { it.absolutePath }
        return listOf(
            "HOME=${Paths.runtimeRoot(context).absolutePath}",
            "JAVA_HOME=${pack.dir.absolutePath}",
            "PATH=${File(pack.dir, "bin").absolutePath}:/system/bin:/system/xbin",
            "LD_LIBRARY_PATH=$libPath",
            "TMPDIR=${Paths.runtimeRoot(context).absolutePath}",
        )
    }
}

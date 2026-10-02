package studio.obsifox.obsilauncher.core.jni

/**
 * JNI bridge for spawning the game JVM.
 *
 * Android 10+ forbids exec() of files inside app data for apps targeting API 29+,
 * so the JVM is started by executing the *system* dynamic linker
 * (`/system/bin/linker64`) with the pack's launcher .so as its payload — the same
 * approach proven by the PoJavLauncher family.
 */
object ObsiBridge {

    init {
        runCatching { System.loadLibrary("obsibridge") }
    }

    /** fork() + execve(linker, [linker, so, ...argv], envp). Returns the child pid, or -1. */
    external fun forkAndExec(linker: String, so: String, argv: Array<String>, envp: Array<String>): Int

    /** file descriptor of the child's combined stdout/stderr pipe, or -1; takes ownership */
    external fun takeLogFd(): Int

    /** SIGKILL the given pid. */
    external fun kill(pid: Int)

    /** waitpid(pid) without auto-reaping races; returns the exit code or -1. */
    external fun waitPid(pid: Int): Int

    external fun chdir(path: String)

    external fun setenv(key: String, value: String)
}

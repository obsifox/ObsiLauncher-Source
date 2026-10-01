package studio.obsifox.launcher.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import studio.obsifox.launcher.core.util.Platform
import studio.obsifox.launcher.core.util.ProgressSink
import studio.obsifox.launcher.core.util.ProgressUpdate
import studio.obsifox.launcher.core.util.sha1Hex
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class DownloadItem(
    val urls: List<String>,
    val dest: Path,
    val sha1: String? = null,
    val size: Long? = null,
    val executable: Boolean = false,
) {
    constructor(url: String, dest: Path, sha1: String? = null, size: Long? = null) : this(listOf(url), dest, sha1, size)
}

/** Parallel, verifying, resumable-by-skipping downloader. */
class Downloader(private val http: Http, var parallelism: Int = 16) {

    /** True when the file is missing or does not match the expected size / hash. */
    fun needsDownload(item: DownloadItem): Boolean {
        val f = item.dest
        if (!Files.isRegularFile(f)) return true
        if (item.size != null && item.size > 0 && Files.size(f) != item.size) return true
        if ((item.size == null || item.size <= 0) && item.sha1 != null) return !sha1Hex(f).equals(item.sha1, true)
        return false
    }

    suspend fun run(items: List<DownloadItem>, stage: String, onProgress: ProgressSink) {
        val unique = items.distinctBy { it.dest.toAbsolutePath().normalize() }
        val todo = unique.filter { needsDownload(it) }
        // permissions may be missing on otherwise valid files
        unique.filter { it.executable && it !in todo }.forEach { markExecutable(it.dest) }
        if (todo.isEmpty()) {
            onProgress(ProgressUpdate(stage, 0, 0, 0, 0))
            return
        }
        val totalBytes = todo.sumOf { it.size ?: 0L }
        val doneBytes = AtomicLong(0)
        val doneFiles = AtomicInteger(0)
        val lastEmit = AtomicLong(0)
        fun emit(force: Boolean = false) {
            val now = System.nanoTime()
            if (force || now - lastEmit.get() > 80_000_000L) {
                lastEmit.set(now)
                onProgress(ProgressUpdate(stage, doneBytes.get().coerceAtLeast(0), totalBytes, doneFiles.get(), todo.size))
            }
        }
        emit(true)
        val sem = Semaphore(parallelism.coerceIn(1, 64))
        coroutineScope {
            todo.map { item ->
                async(Dispatchers.IO) {
                    sem.withPermit {
                        http.download(item.urls, item.dest, item.sha1) { delta ->
                            doneBytes.addAndGet(delta)
                            emit()
                        }
                        if (item.executable) markExecutable(item.dest)
                        doneFiles.incrementAndGet()
                        emit()
                    }
                }
            }.awaitAll()
        }
        emit(true)
    }

    companion object {
        fun markExecutable(path: Path) {
            if (Platform.os == studio.obsifox.launcher.core.util.OsName.WINDOWS) return
            try {
                val perms = Files.getPosixFilePermissions(path).toMutableSet()
                perms += setOf(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_EXECUTE)
                Files.setPosixFilePermissions(path, perms)
            } catch (_: Exception) {
            }
        }
    }
}

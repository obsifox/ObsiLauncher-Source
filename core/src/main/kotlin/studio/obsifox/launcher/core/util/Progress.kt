package studio.obsifox.launcher.core.util

/** Progress callback payload shared by every long running operation. */
data class ProgressUpdate(
    val stage: String,
    val doneBytes: Long = 0,
    val totalBytes: Long = 0,
    val doneFiles: Int = 0,
    val totalFiles: Int = 0,
) {
    /** 0..1, or -1 when the amount of work is unknown. */
    val fraction: Float
        get() = when {
            totalBytes > 0 -> (doneBytes.toDouble() / totalBytes).coerceIn(0.0, 1.0).toFloat()
            totalFiles > 0 -> (doneFiles.toDouble() / totalFiles).coerceIn(0.0, 1.0).toFloat()
            else -> -1f
        }
}

typealias ProgressSink = (ProgressUpdate) -> Unit

val NoProgress: ProgressSink = {}

open class LauncherException(message: String, cause: Throwable? = null) : Exception(message, cause)

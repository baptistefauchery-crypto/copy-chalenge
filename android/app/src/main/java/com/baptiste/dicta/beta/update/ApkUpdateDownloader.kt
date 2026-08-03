package com.baptiste.dicta.beta.update

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

data class UpdateDownloadProgress(val bytesDownloaded: Long, val totalBytes: Long?) {
    val percent: Int? = totalBytes?.takeIf { it > 0 }?.let {
        ((bytesDownloaded.coerceAtMost(it) * 100) / it).toInt()
    }
}

data class DownloadedUpdate(val versionName: String, val apkFile: File)

class UpdateDownloadException(message: String, cause: Throwable? = null) : IOException(message, cause)
class UpdateDownloadAlreadyRunningException : IOException("An update download is already running")

class ApkUpdateDownloader internal constructor(
    cacheDir: File,
    private val connectionFactory: UpdateConnectionFactory,
) {
    constructor(cacheDir: File) : this(cacheDir, RealUpdateConnectionFactory)

    private val updateDirectory = File(cacheDir, "updates")
    private val destination = File(updateDirectory, APK_FILE_NAME)
    // Keep the temporary filename ending in .apk so PackageManager can parse
    // and verify it before the atomic replacement.
    private val partial = File(updateDirectory, "copy-challenge-update.part.apk")
    private val running = AtomicBoolean(false)

    fun download(
        update: AppUpdate,
        onProgress: (UpdateDownloadProgress) -> Unit = {},
        shouldContinue: () -> Boolean = { true },
        validateBeforeCommit: (File) -> Unit = {},
    ): DownloadedUpdate {
        if (!running.compareAndSet(false, true)) throw UpdateDownloadAlreadyRunningException()
        try {
            requireAllowedUrl(URL(update.downloadUrl))
            if (!updateDirectory.exists() && !updateDirectory.mkdirs()) {
                throw UpdateDownloadException("Unable to create private update cache")
            }
            if (partial.exists() && !partial.delete()) throw UpdateDownloadException("Unable to remove stale partial update")

            val finalUrl = downloadFollowingRedirects(URL(update.downloadUrl), onProgress, shouldContinue)
            requireAllowedUrl(finalUrl)
            validateApkContainer(partial)
            validateBeforeCommit(partial)
            replaceAtomically(partial, destination)
            return DownloadedUpdate(update.versionName, destination)
        } catch (error: Throwable) {
            partial.delete()
            throw error
        } finally {
            running.set(false)
        }
    }

    fun cachedApk(): File? = destination.takeIf(File::isFile)

    fun clear() {
        partial.delete()
        destination.delete()
    }

    private fun downloadFollowingRedirects(
        initialUrl: URL,
        onProgress: (UpdateDownloadProgress) -> Unit,
        shouldContinue: () -> Boolean,
    ): URL {
        var current = initialUrl
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            requireAllowedUrl(current)
            val connection = connectionFactory.open(current)
            try {
                when (val status = connection.responseCode) {
                    in 200..299 -> {
                        writeResponse(connection, onProgress, shouldContinue)
                        return current
                    }
                    in 300..399 -> {
                        if (redirectCount == MAX_REDIRECTS) throw UpdateDownloadException("Too many update redirects")
                        val location = connection.header("Location")?.takeIf(String::isNotBlank)
                            ?: throw UpdateDownloadException("Update redirect is missing its destination")
                        current = URL(current, location)
                    }
                    else -> throw UpdateDownloadException("Update download failed with HTTP $status")
                }
            } finally {
                connection.close()
            }
        }
        throw UpdateDownloadException("Too many update redirects")
    }

    private fun writeResponse(
        connection: UpdateConnection,
        onProgress: (UpdateDownloadProgress) -> Unit,
        shouldContinue: () -> Boolean,
    ) {
        val expectedSize = connection.contentLength.takeIf { it >= 0 }
        if (expectedSize != null && expectedSize > MAX_APK_BYTES) throw UpdateDownloadException("Update APK is too large")
        var downloaded = 0L
        connection.inputStream().use { rawInput ->
            BufferedInputStream(rawInput).use { input ->
                FileOutputStream(partial).use { fileOutput ->
                    val output = BufferedOutputStream(fileOutput)
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        if (!shouldContinue()) throw CancellationException("Update download cancelled")
                        val count = input.read(buffer)
                        if (count < 0) break
                        downloaded += count
                        if (downloaded > MAX_APK_BYTES) throw UpdateDownloadException("Update APK is too large")
                        output.write(buffer, 0, count)
                        onProgress(UpdateDownloadProgress(downloaded, expectedSize))
                    }
                    output.flush()
                    fileOutput.fd.sync()
                }
            }
        }
        if (downloaded == 0L) throw UpdateDownloadException("Downloaded update is empty")
        if (expectedSize != null && downloaded != expectedSize) {
            throw UpdateDownloadException("Downloaded update size does not match Content-Length")
        }
    }

    private fun validateApkContainer(file: File) {
        val signature = ByteArray(2)
        val count = FileInputStream(file).use { it.read(signature) }
        if (count != 2 || signature[0] != 'P'.code.toByte() || signature[1] != 'K'.code.toByte()) {
            throw UpdateDownloadException("Downloaded file is not an APK container")
        }
    }

    private fun replaceAtomically(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun requireAllowedUrl(url: URL) {
        val host = url.host.lowercase()
        if (url.protocol.lowercase() != "https" || host !in EXACT_ALLOWED_HOSTS) {
            throw UpdateDownloadException("Update URL is not an allowed GitHub HTTPS address")
        }
    }

    private companion object {
        const val APK_FILE_NAME = "copy-challenge-update.apk"
        const val MAX_REDIRECTS = 6
        const val MAX_APK_BYTES = 256L * 1024 * 1024
        const val BUFFER_SIZE = 64 * 1024
        val EXACT_ALLOWED_HOSTS = setOf(
            "github.com",
            "api.github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
        )
    }
}

internal fun interface UpdateConnectionFactory {
    fun open(url: URL): UpdateConnection
}

internal interface UpdateConnection : Closeable {
    val responseCode: Int
    val contentLength: Long
    fun header(name: String): String?
    fun inputStream(): java.io.InputStream
}

private object RealUpdateConnectionFactory : UpdateConnectionFactory {
    override fun open(url: URL): UpdateConnection {
        val connection = url.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream")
        connection.setRequestProperty("User-Agent", "Copy-Challenge-Android-Updater")
        return object : UpdateConnection {
            override val responseCode get() = connection.responseCode
            override val contentLength get() = connection.contentLengthLong
            override fun header(name: String) = connection.getHeaderField(name)
            override fun inputStream() = connection.inputStream
            override fun close() = connection.disconnect()
        }
    }
}

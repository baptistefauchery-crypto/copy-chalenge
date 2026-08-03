package com.baptiste.dicta.beta.update

import java.io.ByteArrayInputStream
import java.io.File
import java.net.URL
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkUpdateDownloaderTest {
    @Test
    fun followsAllowedHttpsRedirectAndAtomicallyReplacesSingleApk() {
        val cache = Files.createTempDirectory("dicta-update").toFile()
        val apk = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4, 5)
        val factory = FakeFactory(
            mapOf(
                "https://github.com/release.apk" to FakeResponse(302, headers = mapOf("Location" to "https://release-assets.githubusercontent.com/file.apk")),
                "https://release-assets.githubusercontent.com/file.apk" to FakeResponse(200, body = apk),
            ),
        )
        val progress = mutableListOf<Int?>()
        val downloader = ApkUpdateDownloader(cache, factory)

        val result = downloader.download(
            AppUpdate("0.1.0-beta.6", "https://github.com/release.apk", "https://github.com/release"),
            onProgress = { progress += it.percent },
        )

        assertEquals("copy-challenge-update.apk", result.apkFile.name)
        assertArrayEquals(apk, result.apkFile.readBytes())
        assertFalse(File(result.apkFile.parentFile, "copy-challenge-update.part.apk").exists())
        assertEquals(listOf(100), progress)
        assertEquals(2, factory.opened.size)
    }

    @Test
    fun rejectsNonHttpsAndUnexpectedHostsBeforeConnecting() {
        val cache = Files.createTempDirectory("dicta-update-host").toFile()
        val factory = FakeFactory(emptyMap())
        val downloader = ApkUpdateDownloader(cache, factory)

        assertFails<UpdateDownloadException> {
            downloader.download(AppUpdate("2", "http://github.com/file.apk", ""))
        }
        assertFails<UpdateDownloadException> {
            downloader.download(AppUpdate("2", "https://github.com.evil.example/file.apk", ""))
        }
        assertTrue(factory.opened.isEmpty())
    }

    @Test
    fun failedValidationPreservesPreviousCompleteApkAndCleansPartialFile() {
        val cache = Files.createTempDirectory("dicta-update-validation").toFile()
        val updateDirectory = File(cache, "updates").apply { mkdirs() }
        val previous = File(updateDirectory, "copy-challenge-update.apk").apply { writeBytes(byteArrayOf(9, 8, 7)) }
        val factory = FakeFactory(
            mapOf("https://github.com/file.apk" to FakeResponse(200, body = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 1))),
        )
        val downloader = ApkUpdateDownloader(cache, factory)

        assertFails<SecurityException> {
            downloader.download(
                AppUpdate("2", "https://github.com/file.apk", ""),
                validateBeforeCommit = { throw SecurityException("wrong signer") },
            )
        }

        assertArrayEquals(byteArrayOf(9, 8, 7), previous.readBytes())
        assertFalse(File(updateDirectory, "copy-challenge-update.part.apk").exists())
    }

    @Test
    fun rejectsEmptyOrNonApkResponses() {
        val cache = Files.createTempDirectory("dicta-update-content").toFile()
        val downloader = ApkUpdateDownloader(
            cache,
            FakeFactory(mapOf("https://github.com/file.apk" to FakeResponse(200, body = byteArrayOf(1, 2, 3)))),
        )

        assertFails<UpdateDownloadException> {
            downloader.download(AppUpdate("2", "https://github.com/file.apk", ""))
        }
        assertEquals(null, downloader.cachedApk())
    }

    private inline fun <reified T : Throwable> assertFails(block: () -> Unit): T {
        return try {
            block()
            throw AssertionError("Expected ${T::class.java.simpleName}")
        } catch (error: Throwable) {
            if (error !is T) throw error
            error
        }
    }
}

private data class FakeResponse(
    val status: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray = ByteArray(0),
)

private class FakeFactory(private val responses: Map<String, FakeResponse>) : UpdateConnectionFactory {
    val opened = mutableListOf<String>()

    override fun open(url: URL): UpdateConnection {
        opened += url.toString()
        val response = responses[url.toString()] ?: error("Unexpected URL $url")
        return object : UpdateConnection {
            override val responseCode = response.status
            override val contentLength = response.body.size.toLong()
            override fun header(name: String) = response.headers.entries.firstOrNull { it.key.equals(name, true) }?.value
            override fun inputStream() = ByteArrayInputStream(response.body)
            override fun close() = Unit
        }
    }
}

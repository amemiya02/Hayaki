// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** The downloaded APK does not match the hash the release published. */
class ChecksumMismatchException(
    message: String,
) : IOException(message)

/** Streams a release APK into [directory], checking its SHA-256 against the release's `SHA256SUMS.txt`. */
class ApkDownloader(
    private val client: Call.Factory,
    private val checker: UpdateChecker,
    private val directory: File,
) {
    /**
     * Returns the verified APK. On a hash mismatch, any other failure or cancellation the file is deleted.
     * [onProgress] receives bytes read and total bytes (-1 when unknown).
     */
    suspend fun download(
        update: UpdateCheck.Available,
        onProgress: (bytes: Long, total: Long) -> Unit,
    ): File {
        val expected =
            parseSha256Sums(checker.fetchText(update.sumsUrl))[update.apkName]
                ?: throw IOException("$SHA256SUMS_NAME has no entry for ${update.apkName}")
        return withContext(Dispatchers.IO) {
            directory.deleteRecursively()
            directory.mkdirs()
            val file = File(directory, update.apkName)
            try {
                val actual = streamTo(file, update.apkUrl, onProgress)
                if (actual != expected) throw ChecksumMismatchException("Expected $expected, got $actual")
                file
            } catch (e: Throwable) {
                file.delete()
                throw e
            }
        }
    }

    /** Writes [url] to [file] and returns the SHA-256 of what was written, in lowercase hex. */
    private suspend fun streamTo(
        file: File,
        url: String,
        onProgress: (Long, Long) -> Unit,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            val total = response.body.contentLength()
            val buffer = ByteArray(BUFFER_SIZE)
            var bytes = 0L
            response.body.byteStream().use { input ->
                file.outputStream().use { output ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        bytes += n
                        onProgress(bytes, total)
                    }
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}

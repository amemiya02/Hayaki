// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.update

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.security.MessageDigest

class ApkDownloaderTest {
    @get:Rule val temp = TemporaryFolder()

    private val apkName = "Hayaki-0.2.4-universal.apk"
    private val apkBytes = ByteArray(200_000) { (it % 251).toByte() }
    private val apkHash = MessageDigest.getInstance("SHA-256").digest(apkBytes).joinToString("") { "%02x".format(it) }
    private val update = UpdateCheck.Available(AppVersion.parse("0.2.4")!!, "", "https://host/apk", apkName, "https://host/sums")

    private fun downloader(sums: String): ApkDownloader {
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(
                    Interceptor { chain ->
                        val url = chain.request().url.toString()
                        val body =
                            when (url) {
                                update.sumsUrl -> sums.toByteArray()
                                update.apkUrl -> apkBytes
                                else -> error("unexpected $url")
                            }
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(body.toResponseBody("application/octet-stream".toMediaType()))
                            .build()
                    },
                ).build()
        return ApkDownloader(client, UpdateChecker(client, "0.2.3"), temp.root.resolve("updates"))
    }

    @Test
    fun `matching hash returns the file and reports progress`() =
        runBlocking {
            var last = 0L
            val file = downloader("$apkHash  $apkName\n").download(update) { bytes, _ -> last = bytes }
            assertThat(file.readBytes().contentEquals(apkBytes), equalTo(true))
            assertThat(last, equalTo(apkBytes.size.toLong()))
        }

    @Test
    fun `hash mismatch deletes the file and fails`() =
        runBlocking {
            val wrong = "0".repeat(64)
            assertThrows(ChecksumMismatchException::class.java) {
                runBlocking { downloader("$wrong  $apkName\n").download(update) { _, _ -> } }
            }
            assertThat(temp.root.resolve("updates/$apkName").exists(), equalTo(false))
        }

    @Test
    fun `an apk missing from the checksums fails without downloading`() =
        runBlocking {
            assertThrows(IOException::class.java) {
                runBlocking { downloader("$apkHash  other.apk\n").download(update) { _, _ -> } }
            }
            assertThat(temp.root.resolve("updates").exists(), equalTo(false))
        }
}

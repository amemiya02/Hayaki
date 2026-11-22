// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request
import java.io.IOException

/** Asks GitHub for the latest Hayaki release and decides whether it is an update. */
class UpdateChecker(
    private val client: Call.Factory,
    private val appVersionName: String,
) {
    suspend fun check(
        installedName: String,
        installedCode: Long,
    ): UpdateCheck =
        try {
            val text = fetchText(LATEST_RELEASE_URL)
            val release = LatestRelease.parse(text) ?: return UpdateCheck.Failed("Unreadable release JSON")
            decide(installedName, installedCode, release)
        } catch (e: IOException) {
            UpdateCheck.Failed(e.message ?: e.javaClass.simpleName)
        }

    /** Downloads a small text file. Throws [IOException] on any failure. */
    suspend fun fetchText(url: String): String =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("User-Agent", "Hayaki-Android/$appVersionName")
                    .header("Accept", "application/vnd.github+json")
                    .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
                response.body.string()
            }
        }

    companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/amemiya02/Hayaki/releases/latest"
    }
}

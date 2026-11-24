// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.scheduling

import android.content.Context
import anki.zz_hayaki.RwkvStatusResponse
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.web.HttpFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * RWKV scheduling ([ADR-0009](../../../../../../../../docs/adr/0009-opt-in-rwkv-scheduling.md)).
 *
 * The switch is the synced collection config key [CONFIG_KEY]. The Engine keeps the
 * model state and builds the review queue; the app downloads the weights, starts
 * preparation and shows the status.
 */
object RwkvScheduling {
    const val CONFIG_KEY = "hayakiRwkvScheduling"

    private const val WEIGHTS_NAME = "rwkv-sm56-weights.int8.bin"
    private const val WEIGHTS_URL =
        "https://raw.githubusercontent.com/JSchoreels/anki/001440b03395f24ffc1714b18ad3d9d4d89d2067/qt/aqt/rwkv_inference/sm56/weights.int8.bin"
    private const val WEIGHTS_SHA256 = "479b3e2bc6a1c900ba8a4a45d629965c6df2a20cc8ff294583f5aeaeea3b4fdd"
    private const val WEIGHTS_LENGTH = 436_391

    /** Shared by every collection, outside the collection folder. */
    fun weightsDirectory(context: Context): File = File(context.filesDir, "rwkv")

    internal fun isValid(bytes: ByteArray): Boolean =
        bytes.size == WEIGHTS_LENGTH &&
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } == WEIGHTS_SHA256

    /**
     * Downloads the weights here rather than in the Engine, so the download never
     * holds the collection queue.
     */
    private suspend fun ensureWeights(context: Context) =
        withContext(Dispatchers.IO) {
            val dir = weightsDirectory(context)
            val file = File(dir, WEIGHTS_NAME)
            if (file.exists() && isValid(file.readBytes())) return@withContext
            val client = HttpFetcher.getOkHttpBuilder(false).build()
            val bytes =
                client.newCall(Request.Builder().url(WEIGHTS_URL).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("RWKV weights: HTTP ${response.code}")
                    response.body.bytes()
                }
            if (!isValid(bytes)) throw IOException("RWKV weights: size or SHA-256 does not match")
            dir.mkdirs()
            val part = File(dir, "$WEIGHTS_NAME.part")
            part.writeBytes(bytes)
            if (!part.renameTo(file)) throw IOException("RWKV weights: could not save")
        }

    suspend fun status(): RwkvStatusResponse = withCol { backend.rwkvStatus() }

    /** Downloads the weights if needed and starts preparing the open collection. */
    suspend fun prepare(context: Context): RwkvStatusResponse {
        ensureWeights(context)
        val dir = weightsDirectory(context).absolutePath
        return withCol { backend.prepareRwkv(dir) }
    }

    suspend fun setEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        withCol { config.set(CONFIG_KEY, enabled) }
        if (enabled) prepare(context)
    }

    /**
     * With the switch on, starts preparation. Study uses the upstream queue until it
     * is ready, so a failure here only shows in Settings.
     */
    suspend fun prepareIfEnabled(context: Context) {
        val status = status()
        if (status.enabled &&
            status.state != RwkvStatusResponse.State.READY &&
            status.state != RwkvStatusResponse.State.PREPARING
        ) {
            prepare(context)
        }
    }
}

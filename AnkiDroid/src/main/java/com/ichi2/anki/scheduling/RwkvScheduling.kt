// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.scheduling

import android.content.Context
import anki.zz_hayaki.RwkvModel
import anki.zz_hayaki.RwkvStatusResponse
import anki.zz_hayaki.RwkvWeights
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

    /** Shared by every collection, outside the collection folder. */
    fun weightsDirectory(context: Context): File = File(context.filesDir, "rwkv")

    internal fun RwkvWeights.accepts(bytes: ByteArray): Boolean =
        bytes.size.toLong() == size &&
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } == sha256

    /**
     * Downloads [weights], the file of the chosen model, here rather than in the Engine,
     * so the download never holds the collection queue.
     */
    private suspend fun ensureWeights(
        context: Context,
        weights: RwkvWeights,
    ) = withContext(Dispatchers.IO) {
        val dir = weightsDirectory(context)
        val file = File(dir, weights.fileName)
        if (file.exists() && weights.accepts(file.readBytes())) return@withContext
        val client = HttpFetcher.getOkHttpBuilder(false).build()
        val bytes =
            client.newCall(Request.Builder().url(weights.url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("RWKV weights: HTTP ${response.code}")
                response.body.bytes()
            }
        if (!weights.accepts(bytes)) throw IOException("RWKV weights: size or SHA-256 does not match")
        dir.mkdirs()
        val part = File(dir, "${weights.fileName}.part")
        part.writeBytes(bytes)
        if (!part.renameTo(file)) throw IOException("RWKV weights: could not save")
    }

    suspend fun status(): RwkvStatusResponse = withCol { backend.rwkvStatus() }

    /** Downloads the weights if needed and starts preparing the open collection. */
    suspend fun prepare(context: Context): RwkvStatusResponse {
        ensureWeights(context, status().weights)
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

    /** Chooses the model, which syncs with the collection. With the switch on, prepares it. */
    suspend fun setModel(
        context: Context,
        model: RwkvModel,
    ) {
        val status = withCol { backend.setRwkvModel(model) }
        if (status.enabled) prepare(context)
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

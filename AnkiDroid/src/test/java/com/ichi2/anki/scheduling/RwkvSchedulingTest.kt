// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.scheduling

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.zz_hayaki.RwkvStatusResponse.State
import anki.zz_hayaki.rwkvStatusResponse
import anki.zz_hayaki.rwkvWeights
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.scheduling.RwkvScheduling.accepts
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RwkvSchedulingTest {
    @Test
    fun `deck counts wait for an ongoing restore`() =
        runTest {
            val preparing =
                rwkvStatusResponse {
                    enabled = true
                    state = State.PREPARING
                }
            val ready = preparing.toBuilder().setState(State.READY).build()
            mockkObject(RwkvScheduling) {
                coEvery { RwkvScheduling.status() } returnsMany listOf(preparing, ready)
                RwkvScheduling.prepareIfEnabled(mockk())
                coVerify(exactly = 2) { RwkvScheduling.status() }
            }
        }

    private val bytes = "weights".toByteArray()
    private val weights =
        rwkvWeights {
            fileName = "model.bin"
            sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            size = bytes.size.toLong()
        }

    @Test
    fun `weights are accepted when size and hash match`() {
        assertTrue(weights.accepts(bytes))
    }

    @Test
    fun `weights are rejected when the size differs`() {
        assertFalse(weights.accepts(bytes + 0))
    }

    @Test
    fun `weights are rejected when the hash differs`() {
        assertFalse(weights.accepts("weighTs".toByteArray()))
    }
}

@RunWith(AndroidJUnit4::class)
class RwkvDailyListConfigTest : RobolectricTest() {
    @Test
    fun `daily list is off by default`() =
        runTest {
            assertFalse(RwkvScheduling.dailyList())
        }

    @Test
    fun `setting the daily list writes the synced key`() =
        runTest {
            RwkvScheduling.setDailyList(true)
            assertTrue(RwkvScheduling.dailyList())
            assertEquals(true, col.config.get(RwkvScheduling.DAILY_LIST_CONFIG_KEY, false))
            RwkvScheduling.setDailyList(false)
            assertFalse(RwkvScheduling.dailyList())
        }
}

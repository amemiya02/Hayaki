// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.update

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import kotlin.time.Duration.Companion.hours

class UpdateModelTest {
    private val base = 22600303L

    private fun version(text: String) = AppVersion.parse(text)!!

    private fun release(
        tag: String = "v0.2.4",
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: List<String> = ApkVariant.entries.map { it.assetName(version(tag.removePrefix("v"))) } + SHA256SUMS_NAME,
    ) = LatestRelease(tag, "notes", draft, prerelease, assets.map { ReleaseAsset(it, "https://example.com/$it") })

    @Test
    fun `variant is read from the versionCode prefix`() {
        assertThat(ApkVariant.of(base), equalTo(ApkVariant.UNIVERSAL))
        assertThat(ApkVariant.of(100_000_000 + base), equalTo(ApkVariant.ARMEABI_V7A))
        assertThat(ApkVariant.of(200_000_000 + base), equalTo(ApkVariant.X86))
        assertThat(ApkVariant.of(300_000_000 + base), equalTo(ApkVariant.ARM64_V8A))
        assertThat(ApkVariant.of(400_000_000 + base), equalTo(ApkVariant.X86_64))
        assertThat(ApkVariant.of(900_000_000 + base), nullValue())
    }

    @Test
    fun `asset names match the release naming`() {
        assertThat(ApkVariant.ARM64_V8A.assetName(version("v0.2.4")), equalTo("Hayaki-0.2.4-arm64-v8a.apk"))
        assertThat(ApkVariant.UNIVERSAL.assetName(version("0.2.4")), equalTo("Hayaki-0.2.4-universal.apk"))
    }

    @Test
    fun `versions compare numerically`() {
        assertThat(version("0.2.10") > version("0.2.9"), equalTo(true))
        assertThat(version("v0.2.4") > version("0.2.3"), equalTo(true))
        assertThat(version("1.0") == version("1.0.0"), equalTo(true))
        assertThat(version("0.2.3") < version("0.2.3.1"), equalTo(true))
    }

    @Test
    fun `garbage versions do not parse`() {
        listOf("", "v", "abc", "0.2.x", "0.2.3-debug", "0..1", "-1.0").forEach {
            assertThat("'$it'", AppVersion.parse(it), nullValue())
        }
    }

    @Test
    fun `newer release is available with the asset of the installed variant`() {
        val arm = decide("0.2.3", 300_000_000 + base, release()) as UpdateCheck.Available
        assertThat(arm.apkName, equalTo("Hayaki-0.2.4-arm64-v8a.apk"))
        assertThat(arm.apkUrl, equalTo("https://example.com/Hayaki-0.2.4-arm64-v8a.apk"))
        assertThat(arm.sumsUrl, equalTo("https://example.com/$SHA256SUMS_NAME"))
        assertThat(arm.version, equalTo(version("0.2.4")))
        assertThat(arm.notes, equalTo("notes"))

        val universal = decide("0.2.3", base, release()) as UpdateCheck.Available
        assertThat(universal.apkName, equalTo("Hayaki-0.2.4-universal.apk"))
    }

    @Test
    fun `equal or older release is up to date`() {
        assertThat(decide("0.2.4", base, release("v0.2.4")), equalTo(UpdateCheck.UpToDate))
        assertThat(decide("0.2.4", base, release("v0.2.3")), equalTo(UpdateCheck.UpToDate))
    }

    @Test
    fun `drafts and prereleases are ignored`() {
        assertThat(decide("0.2.3", base, release(draft = true)), equalTo(UpdateCheck.UpToDate))
        assertThat(decide("0.2.3", base, release(prerelease = true)), equalTo(UpdateCheck.UpToDate))
    }

    @Test
    fun `missing asset or checksums fail instead of offering an update`() {
        val noArm = release(assets = listOf("Hayaki-0.2.4-universal.apk", SHA256SUMS_NAME))
        assertThat(decide("0.2.3", 300_000_000 + base, noArm), instanceOf(UpdateCheck.Failed::class.java))
        val noSums = release(assets = listOf("Hayaki-0.2.4-universal.apk"))
        assertThat(decide("0.2.3", base, noSums), instanceOf(UpdateCheck.Failed::class.java))
    }

    @Test
    fun `unknown installed build fails`() {
        assertThat(decide("0.2.3-debug", base, release()), instanceOf(UpdateCheck.Failed::class.java))
        assertThat(decide("0.2.3", 900_000_000 + base, release()), instanceOf(UpdateCheck.Failed::class.java))
        assertThat(decide("0.2.3", base, release(tag = "nightly", assets = emptyList())), instanceOf(UpdateCheck.Failed::class.java))
    }

    @Test
    fun `release json is parsed and bad json is not`() {
        val json =
            """{"tag_name":"v0.2.4","body":"- fixes","draft":false,"prerelease":false,"id":1,
            "assets":[{"name":"SHA256SUMS.txt","browser_download_url":"https://x/s","size":3}]}"""
        val parsed = LatestRelease.parse(json)
        assertThat(parsed, notNullValue())
        assertThat(parsed!!.tag, equalTo("v0.2.4"))
        assertThat(parsed.assets.single().url, equalTo("https://x/s"))
        assertThat(LatestRelease.parse("<html>rate limited</html>"), nullValue())
        assertThat(LatestRelease.parse("""{"message":"Not Found"}"""), nullValue())
    }

    @Test
    fun `sha256 sums are parsed by filename`() {
        val a = "a".repeat(64)
        val b = "B".repeat(64)
        val sums = parseSha256Sums("$a  Hayaki-0.2.4-universal.apk\n$b *Hayaki-0.2.4-x86.apk\nnot a hash line\n\n")
        assertThat(
            sums,
            equalTo(mapOf("Hayaki-0.2.4-universal.apk" to a, "Hayaki-0.2.4-x86.apk" to b.lowercase())),
        )
    }

    @Test
    fun `check is due after 24 hours, never checked, or clock moved back`() {
        val day = 24.hours.inWholeMilliseconds
        assertThat(isCheckDue(0, day), equalTo(true))
        assertThat(isCheckDue(1_000, 1_000 + day - 1), equalTo(false))
        assertThat(isCheckDue(1_000, 1_000 + day), equalTo(true))
        assertThat(isCheckDue(5_000, 1_000), equalTo(true))
    }

    @Test
    fun `a failed check is retried after an hour, a successful one after a day`() {
        val now = 100.hours.inWholeMilliseconds
        val failed = nextLastCheckMillis(UpdateCheck.Failed("offline"), now)
        assertThat(isCheckDue(failed, now + 1.hours.inWholeMilliseconds - 1), equalTo(false))
        assertThat(isCheckDue(failed, now + 1.hours.inWholeMilliseconds), equalTo(true))

        val succeeded = nextLastCheckMillis(UpdateCheck.UpToDate, now)
        assertThat(isCheckDue(succeeded, now + 1.hours.inWholeMilliseconds), equalTo(false))
        assertThat(isCheckDue(succeeded, now + 24.hours.inWholeMilliseconds), equalTo(true))
    }

    @Test
    fun `release notes show as plain text`() {
        val notes =
            "## What's new / 更新内容\n\n- Follows the **Deck row opacity** setting.\n\n" +
                "See [SHA256SUMS.txt](https://example.com/s) and `colpkg`."

        assertThat(
            plainNotes(notes),
            equalTo("What's new / 更新内容\n\n- Follows the Deck row opacity setting.\n\nSee SHA256SUMS.txt and colpkg."),
        )
    }
}

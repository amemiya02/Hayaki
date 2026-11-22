// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.hours

/**
 * The APK flavour an installed copy of Hayaki was built as. An update must be the same variant:
 * installing the universal APK over an arm64 one would be a versionCode downgrade and fail.
 *
 * Release builds prefix the base versionCode with the variant, as in `300_000_000 + base`.
 */
enum class ApkVariant(
    val prefix: Int,
    val assetSuffix: String,
) {
    UNIVERSAL(0, "universal"),
    ARMEABI_V7A(1, "armeabi-v7a"),
    X86(2, "x86"),
    ARM64_V8A(3, "arm64-v8a"),
    X86_64(4, "x86_64"),
    ;

    fun assetName(version: AppVersion) = "Hayaki-$version-$assetSuffix.apk"

    companion object {
        fun of(versionCode: Long): ApkVariant? {
            val prefix = versionCode / VARIANT_DIVISOR
            return entries.firstOrNull { it.prefix.toLong() == prefix }
        }

        private const val VARIANT_DIVISOR = 100_000_000L
    }
}

/** A dotted-integer version such as `0.2.10`. */
class AppVersion private constructor(
    private val parts: List<Int>,
) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        for (i in 0 until maxOf(parts.size, other.parts.size)) {
            val c = (parts.getOrNull(i) ?: 0).compareTo(other.parts.getOrNull(i) ?: 0)
            if (c != 0) return c
        }
        return 0
    }

    override fun equals(other: Any?) = other is AppVersion && compareTo(other) == 0

    override fun hashCode() = parts.dropLastWhile { it == 0 }.hashCode()

    override fun toString() = parts.joinToString(".")

    companion object {
        /** Parses `v0.2.4` or `0.2.4`. Returns null for anything else. */
        fun parse(text: String): AppVersion? {
            val numbers =
                text
                    .trim()
                    .removePrefix("v")
                    .removePrefix("V")
                    .split('.')
                    .map { it.toIntOrNull() }
            if (numbers.any { it == null || it < 0 }) return null
            return AppVersion(numbers.filterNotNull())
        }
    }
}

@Serializable
data class ReleaseAsset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
)

/** The subset of GitHub's "latest release" JSON that the updater reads. */
@Serializable
data class LatestRelease(
    @SerialName("tag_name") val tag: String,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<ReleaseAsset> = emptyList(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Null when [text] is not a release JSON object. */
        fun parse(text: String): LatestRelease? = runCatching { json.decodeFromString<LatestRelease>(text) }.getOrNull()
    }
}

sealed interface UpdateCheck {
    data object UpToDate : UpdateCheck

    data class Available(
        val version: AppVersion,
        val notes: String,
        val apkUrl: String,
        val apkName: String,
        val sumsUrl: String,
    ) : UpdateCheck

    data class Failed(
        val reason: String,
    ) : UpdateCheck
}

const val SHA256SUMS_NAME = "SHA256SUMS.txt"

/** Decides whether [release] is an update for the installed build, and which asset installs it. */
fun decide(
    installedName: String,
    installedCode: Long,
    release: LatestRelease,
): UpdateCheck {
    val installed = AppVersion.parse(installedName) ?: return UpdateCheck.Failed("Unrecognised installed version '$installedName'")
    if (release.draft || release.prerelease) return UpdateCheck.UpToDate
    val latest = AppVersion.parse(release.tag) ?: return UpdateCheck.Failed("Unrecognised release tag '${release.tag}'")
    if (latest <= installed) return UpdateCheck.UpToDate
    val variant = ApkVariant.of(installedCode) ?: return UpdateCheck.Failed("Unknown build variant for versionCode $installedCode")
    val apkName = variant.assetName(latest)
    val apk = release.assets.firstOrNull { it.name == apkName } ?: return UpdateCheck.Failed("Release has no $apkName")
    val sums = release.assets.firstOrNull { it.name == SHA256SUMS_NAME } ?: return UpdateCheck.Failed("Release has no $SHA256SUMS_NAME")
    return UpdateCheck.Available(latest, plainNotes(release.body.orEmpty()), apk.url, apkName, sums.url)
}

private val HEADING_MARKS = Regex("(?m)^#{1,6}\\s+")
private val LINK = Regex("\\[([^\\]]+)]\\([^)]*\\)")
private val EMPHASIS = Regex("\\*\\*|`")

/** The release notes' Markdown as plain text for the dialog: no heading marks, emphasis or link targets. */
fun plainNotes(markdown: String): String =
    markdown
        .replace(LINK, "$1")
        .replace(HEADING_MARKS, "")
        .replace(EMPHASIS, "")
        .trim()

private val SUMS_LINE = Regex("([0-9a-fA-F]{64}) [ *](.+)")

/** Parses `shasum` output (`<64 hex>  <filename>`) into filename to lowercase hash. Malformed lines are skipped. */
fun parseSha256Sums(text: String): Map<String, String> =
    buildMap {
        for (line in text.lineSequence()) {
            val match = SUMS_LINE.matchEntire(line.trim()) ?: continue
            put(match.groupValues[2], match.groupValues[1].lowercase())
        }
    }

private val CHECK_INTERVAL_MILLIS = 24.hours.inWholeMilliseconds

private val RETRY_AFTER_FAILURE_MILLIS = 1.hours.inWholeMilliseconds

/**
 * The check time to record after [result]: now, or after a failure (offline, GitHub down) a time
 * that makes the next check due in an hour rather than a day.
 */
fun nextLastCheckMillis(
    result: UpdateCheck,
    nowMillis: Long,
): Long = if (result is UpdateCheck.Failed) nowMillis - CHECK_INTERVAL_MILLIS + RETRY_AFTER_FAILURE_MILLIS else nowMillis

/** True when 24 hours have passed since [lastCheckMillis], or the clock has moved back past it. */
fun isCheckDue(
    lastCheckMillis: Long,
    nowMillis: Long,
): Boolean = nowMillis < lastCheckMillis || nowMillis - lastCheckMillis >= CHECK_INTERVAL_MILLIS

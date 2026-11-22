// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

/**
 * The identity the Fork installs under, so that it sits beside the Official App
 * (`com.ichi2.anki`, and `com.ichi2.anki.debug` for Upstream's debug builds) with its own data.
 *
 * Read by `:AnkiDroid` for its application ID and launcher name, and by `:api` for the
 * authority and permission of the public API, which must match the app's.
 */
object ForkIdentity {
    /** The application ID of a release build. */
    const val APPLICATION_ID = "io.github.amemiya02.hayaki"

    /** Appended to [APPLICATION_ID] for debug builds. */
    const val DEBUG_SUFFIX = ".debug"

    /** The application ID of a debug build. */
    const val DEBUG_APPLICATION_ID = APPLICATION_ID + DEBUG_SUFFIX

    /** The Fork's name, shown by the launcher of a release build. Written the same way in every language. */
    const val NAME = "Hayaki"

    /**
     * The working name shown by the launcher of a debug build, so it is told apart from a release build.
     * It is for development builds only and must never reach another person.
     */
    const val WORKING_NAME = "Hayaki Debug"
}

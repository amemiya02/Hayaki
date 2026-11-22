// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.CommonString
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.web.HttpFetcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException

sealed interface UpdateUiState {
    data object Idle : UpdateUiState

    data class Available(
        val update: UpdateCheck.Available,
    ) : UpdateUiState

    data class Downloading(
        val update: UpdateCheck.Available,
        val bytes: Long,
        val total: Long,
    ) : UpdateUiState {
        val fraction: Float? get() = if (total > 0) bytes.toFloat() / total else null
    }
}

/** The one place the update flow (check, ask, download, install) lives. Hosts only show [UpdateDialogs]. */
class UpdateViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state

    private val _messages = Channel<Int>(Channel.BUFFERED)

    /** String resources to toast: results the user asked for, and failures after they said Update. */
    val messages = _messages.receiveAsFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    private val client by lazy { HttpFetcher.getOkHttpBuilder(false).build() }
    private val checker by lazy { UpdateChecker(client, BuildConfig.VERSION_NAME) }

    /** The deck list's check: only if enabled and 24 hours have passed. Failures are only logged. */
    fun checkAutomatically() {
        if (!Prefs.isUpdateCheckEnabled || !isCheckDue(Prefs.lastUpdateCheckMillis, System.currentTimeMillis())) return
        check(manual = false)
    }

    /** The About screen's "Check for updates". Reports the outcome. */
    fun checkNow() = check(manual = true)

    private fun check(manual: Boolean) {
        if (checkJob?.isActive == true || _state.value != UpdateUiState.Idle) return
        checkJob =
            viewModelScope.launch {
                val result = checker.check(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong())
                Prefs.lastUpdateCheckMillis = nextLastCheckMillis(result, System.currentTimeMillis())
                when (result) {
                    is UpdateCheck.Available -> _state.value = UpdateUiState.Available(result)
                    UpdateCheck.UpToDate -> if (manual) _messages.send(CommonString.update_up_to_date)
                    is UpdateCheck.Failed -> {
                        Timber.w("Update check failed: %s", result.reason)
                        if (manual) _messages.send(CommonString.update_check_failed)
                    }
                }
            }
    }

    fun dismiss() {
        _state.value = UpdateUiState.Idle
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        _state.value = UpdateUiState.Idle
    }

    /** The dialog's "Update": download, verify, then hand the APK to the system installer. */
    fun startUpdate() {
        val update = (_state.value as? UpdateUiState.Available)?.update ?: return
        val context = getApplication<Application>()
        if (!ApkInstaller.canInstall(context)) {
            _messages.trySend(CommonString.update_allow_install)
            ApkInstaller.openInstallPermissionSettings(context)
            return
        }
        _state.value = UpdateUiState.Downloading(update, 0, -1)
        downloadJob =
            viewModelScope.launch {
                try {
                    val downloader = ApkDownloader(client, checker, File(context.cacheDir, "updates"))
                    val apk = downloader.download(update) { bytes, total -> _state.value = UpdateUiState.Downloading(update, bytes, total) }
                    withContext(Dispatchers.IO) { ApkInstaller.install(context, apk) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    Timber.w(e, "Update download failed")
                    _messages.send(
                        if (e is ChecksumMismatchException) CommonString.update_verify_failed else CommonString.update_download_failed,
                    )
                } catch (e: Exception) {
                    // PackageInstaller reports a refused session as SecurityException or IllegalStateException
                    Timber.w(e, "Update install failed")
                    _messages.send(CommonString.update_download_failed)
                }
                _state.value = UpdateUiState.Idle
            }
    }
}

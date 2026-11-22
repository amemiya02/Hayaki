// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.update

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.compose.ui.dialogs.ConfirmationDialog
import com.ichi2.compose.ui.dialogs.DialogAction
import com.ichi2.compose.ui.dialogs.DialogBody
import com.ichi2.compose.ui.dialogs.ProgressDialog

/** The update dialogs and toasts for [viewModel]. Add once to any screen that can start the update flow. */
@Composable
fun UpdateDialogs(viewModel: UpdateViewModel) {
    val context = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { showThemedToast(context, resources.getString(it), shortLength = false) }
    }
    when (val state = viewModel.state.collectAsStateWithLifecycle().value) {
        UpdateUiState.Idle -> Unit
        is UpdateUiState.Available ->
            ConfirmationDialog(
                title = stringResource(CommonString.update_available_title, state.update.version.toString()),
                message = "",
                confirm = DialogAction(TR.importingUpdate(), viewModel::startUpdate),
                dismissText = stringResource(CommonString.update_later),
                onDismissRequest = viewModel::dismiss,
            ) {
                Box(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                    DialogBody(state.update.notes)
                }
            }
        is UpdateUiState.Downloading ->
            ProgressDialog(
                title = stringResource(CommonString.update_downloading),
                progress = state.fraction,
                onCancel = viewModel::cancelDownload,
            )
    }
}

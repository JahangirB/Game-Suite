package com.pippop.ui.screens

import com.pippop.ui.theme.Digits
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pippop.ui.UpdateUiState
import com.pippop.ui.theme.AppTheme

/** Offers a newer release; Android itself shows the final "update this app?" confirmation. */
@Composable
fun UpdateDialog(state: UpdateUiState, onUpdate: () -> Unit, onAllow: () -> Unit, onLater: () -> Unit) {
    val p = AppTheme.palette
    val info = state.available
    ToyDialog(
        title = if (state.needsPermission) "Allow updates" else "Update available",
        body = if (state.needsPermission) {
            "Android needs your permission for Pip & Pop to install updates. " +
                "Tap Allow, switch on \"Allow from this source\", come back and tap Update again."
        } else {
            "Version ${info?.versionName.orEmpty()} is ready (you have ${state.currentVersion}). " +
                "Your saved game, settings and statistics are kept."
        },
        onDismiss = { if (!state.downloading) onLater() },
        dismissible = !state.downloading,
        content = {
            if (state.downloading) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    color = p.primary,
                    trackColor = p.accent,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(style = Digits, text = "Downloading… ${(state.progress * 100).toInt()}%", color = p.muted)
            }
        },
    ) {
        SecondaryButton("Later", onLater, Modifier.weight(1f), enabled = !state.downloading)
        if (state.needsPermission) {
            PrimaryButton("Allow", onAllow, Modifier.weight(1f))
        } else {
            PrimaryButton("Update", onUpdate, Modifier.weight(1f), enabled = !state.downloading)
        }
    }
}

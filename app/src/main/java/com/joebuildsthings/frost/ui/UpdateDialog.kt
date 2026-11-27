package com.joebuildsthings.frost.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.joebuildsthings.frost.BuildConfig
import com.joebuildsthings.frost.update.UpdateDecision
import com.joebuildsthings.frost.update.UpdateViewModel

@Composable
fun UpdateDialog(updates: UpdateViewModel) {
    val context = LocalContext.current
    when (val d = updates.decision) {
        UpdateDecision.None -> Unit

        is UpdateDecision.Optional -> AlertDialog(
            onDismissRequest = { updates.later() },
            title = { Text("Update available") },
            text = {
                ReleaseText(
                    "Frost ${d.latest.version} is out. You have ${BuildConfig.VERSION_NAME}.",
                    d.latest.notes
                )
            },
            confirmButton = {
                TextButton(onClick = { updates.download(context, d.latest) }) {
                    Text("Update", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { updates.skip() }) { Text("Skip") }
            }
        )

        is UpdateDecision.Required -> AlertDialog(
            onDismissRequest = { },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text("Update required") },
            text = {
                ReleaseText(
                    "You are ${d.behind} versions behind. Install Frost ${d.latest.version} to keep using the app. " +
                        "Your library, playlists and favorites stay as they are.",
                    d.latest.notes
                )
            },
            confirmButton = {
                TextButton(onClick = { updates.download(context, d.latest) }) {
                    Text("Update now", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

@Composable
private fun ReleaseText(summary: String, notes: String) {
    Column {
        Text(summary)
        if (notes.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(notes, maxLines = 8, overflow = TextOverflow.Ellipsis)
        }
    }
}

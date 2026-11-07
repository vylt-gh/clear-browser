package dev.clearbrowser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.clearbrowser.core.engine.JsDialog

/**
 * Presents a page's `alert()`, `confirm()`, or `prompt()`.
 *
 * These exist in the app rather than being left to WebView's default handling
 * for a concrete reason: WebView *suppresses* them, and an unanswered
 * `alert()` leaves the promise on the page pending forever. The page looks
 * frozen with no error anywhere, which is close to the worst failure mode a
 * browser has. Answering is mandatory, and so is cancelling: every path out of
 * this dialog calls back into the page.
 */
@Composable
fun JsDialogHost(
    dialog: JsDialog,
    onDismiss: () -> Unit,
) {
    var input by remember(dialog) {
        mutableStateOf(dialog.defaultValue.orEmpty())
    }

    AlertDialog(
        onDismissRequest = {
            dialog.onDismiss()
            onDismiss()
        },
        title = {
            Text(text = dialog.message.ifBlank { "This page is asking for input." })
        },
        text = {
            Column {
                if (dialog.hasInput) {
                    TextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        textStyle = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    dialog.onAccept(if (dialog.hasInput) input else null)
                    onDismiss()
                },
            ) { Text("OK") }
        },
        dismissButton = if (dialog.withCancel) {
            {
                TextButton(
                    onClick = {
                        dialog.onDismiss()
                        onDismiss()
                    },
                ) { Text("Cancel") }
            }
        } else {
            null
        },
    )
}

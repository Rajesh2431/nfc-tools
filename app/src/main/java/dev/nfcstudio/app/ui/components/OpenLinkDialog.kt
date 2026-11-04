package dev.nfcstudio.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.nfcstudio.app.R
import dev.nfcstudio.app.core.ndef.UriPolicy

/**
 * Confirmation before opening any URI that came from a tag or task (spec § 10).
 * Shows scheme and host prominently, flags deceptive patterns, and refuses
 * schemes outside [UriPolicy.allowedSchemes]. Opening uses the normal intent
 * resolver; nothing opens without a tap on the confirm button.
 */
@Composable
fun OpenLinkDialog(uri: String, onDismiss: () -> Unit, onResult: (opened: Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val info = UriPolicy.inspect(uri)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (info != null) R.string.open_uri_confirm_title else R.string.open_uri_blocked_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (info == null) {
                    Text(stringResource(R.string.open_uri_blocked_detail))
                } else {
                    info.host?.let {
                        Text(stringResource(R.string.open_uri_host), style = MaterialTheme.typography.labelMedium)
                        Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    } ?: Text(stringResource(R.string.open_uri_scheme, info.scheme), style = MaterialTheme.typography.titleMedium)
                    if (info.hasUserInfo) Warning(stringResource(R.string.open_uri_warn_userinfo))
                    if (info.hasNonAsciiHost) Warning(stringResource(R.string.open_uri_warn_idn))
                    if (info.isPlainHttp) Warning(stringResource(R.string.open_uri_warn_http))
                }
                Text(stringResource(R.string.open_uri_full), style = MaterialTheme.typography.labelMedium)
                SelectionContainer { Text(uri, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            if (info != null) {
                Button(onClick = {
                    onDismiss()
                    onResult(openUri(context, uri))
                }) { Text(stringResource(R.string.open_uri)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun Warning(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

/** Re-validates and opens via ACTION_VIEW. @return false when blocked or no app handles it. */
fun openUri(context: Context, uri: String): Boolean {
    if (UriPolicy.validate(uri) != null) return false
    val intent = Intent(Intent.ACTION_VIEW, uri.toUri())
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

package dev.nfcstudio.app.ui.components

import android.content.ClipData
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import dev.nfcstudio.app.R
import kotlinx.coroutines.launch

/** Copies [value] to the clipboard on explicit user action only. */
@Composable
fun CopyButton(value: String, label: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    IconButton(onClick = {
        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, value))) }
    }) {
        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.copy_value, label))
    }
}

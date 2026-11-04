package com.tapandgo.feature.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
import com.tapandgo.R
import com.tapandgo.data.library.Difference
import com.tapandgo.data.library.LibraryIcon
import com.tapandgo.data.library.LibraryMeta

fun LibraryIcon.vector(): ImageVector = when (this) {
    LibraryIcon.TAG -> Icons.Filled.Nfc
    LibraryIcon.HOME -> Icons.Filled.Home
    LibraryIcon.WORK -> Icons.Filled.Work
    LibraryIcon.CAR -> Icons.Filled.DirectionsCar
    LibraryIcon.KEY -> Icons.Filled.Key
    LibraryIcon.WIFI -> Icons.Filled.Wifi
    LibraryIcon.LINK -> Icons.Filled.Link
    LibraryIcon.CONTACT -> Icons.Filled.ContactPage
    LibraryIcon.STAR -> Icons.Filled.Star
}

fun iconOf(name: String) = (LibraryIcon.entries.firstOrNull { it.name == name } ?: LibraryIcon.TAG).vector()

fun metaProblemText(p: LibraryMeta.Problem): Int = when (p) {
    LibraryMeta.Problem.NAME_EMPTY -> R.string.template_name_empty
    LibraryMeta.Problem.NAME_TOO_LONG -> R.string.template_name_too_long
    LibraryMeta.Problem.TEXT_TOO_LONG -> R.string.library_text_too_long
    LibraryMeta.Problem.LABELS_INVALID -> R.string.library_labels_invalid
}

fun diffLabel(f: Difference.Field): Int = when (f) {
    Difference.Field.UID -> R.string.tag_uid
    Difference.Field.TECHNOLOGIES -> R.string.tag_technologies
    Difference.Field.CHIP -> R.string.tag_type
    Difference.Field.NDEF_TYPE -> R.string.tag_data_format
    Difference.Field.CAPACITY -> R.string.tag_size
    Difference.Field.WRITABLE -> R.string.tag_writable
    Difference.Field.RECORD_COUNT -> R.string.library_record_count
    Difference.Field.RECORD -> R.string.tag_records_title
}

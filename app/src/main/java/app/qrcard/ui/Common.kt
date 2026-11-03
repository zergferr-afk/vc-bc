package app.qrcard.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.qrcard.data.EntryIcon
import app.qrcard.qr.QrGenerator

fun EntryIcon.vector(): ImageVector? = when (this) {
    EntryIcon.PERSON -> Icons.Filled.Person
    EntryIcon.SERVICES -> Icons.Filled.Build
    EntryIcon.TRAINING -> Icons.Filled.DateRange
    EntryIcon.CV -> Icons.Filled.AccountBox
    EntryIcon.LINKEDIN -> null // drawn as an "in" badge
    EntryIcon.WEBSITE -> Icons.Filled.Home
    EntryIcon.EMAIL -> Icons.Filled.Email
    EntryIcon.PHONE -> Icons.Filled.Phone
    EntryIcon.WIFI -> Icons.Filled.Lock
    EntryIcon.STAR -> Icons.Filled.Star
}

@Composable
fun EntryIconBadge(icon: EntryIcon, size: Dp = 44.dp, selected: Boolean = true) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier.size(size).background(bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val v = icon.vector()
        if (v != null) {
            Icon(v, contentDescription = null, tint = fg, modifier = Modifier.size(size * 0.55f))
        } else {
            Text("in", color = fg, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
        }
    }
}

@Composable
fun rememberQrBitmap(content: String, sizePx: Int = 1024): Bitmap? =
    remember(content, sizePx) { QrGenerator.bitmap(content, sizePx) }

@Composable
fun QrImage(
    content: String,
    modifier: Modifier = Modifier,
    sizePx: Int = 1024,
    emptyText: String = "Fill in the details\nto see the QR code",
) {
    val bmp = rememberQrBitmap(content, sizePx)
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "QR code",
            modifier = modifier,
            filterQuality = FilterQuality.None,
        )
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text(
                emptyText,
                style = MaterialTheme.typography.bodySmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

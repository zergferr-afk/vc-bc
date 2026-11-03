package app.qrcard.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.qrcard.data.CardEntry
import app.qrcard.data.EntryKind
import app.qrcard.qr.QrGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowScreen(
    entries: List<CardEntry>,
    startIndex: Int,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val context = LocalContext.current
    KeepScreenBright()
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))) { entries.size }
    val current = entries.getOrNull(pager.currentPage)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (current != null) {
                        IconButton(onClick = { share(context, current) }) { Icon(Icons.Filled.Share, "Share") }
                        IconButton(onClick = { onEdit(current.id) }) { Icon(Icons.Filled.Edit, "Edit") }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                val e = entries[page]
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    EntryIconBadge(e.icon, size = 56.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(e.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    if (e.subtitle.isNotBlank()) {
                        Text(
                            e.subtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    if (e.kind == EntryKind.FILE && e.qrPayload().isBlank()) {
                        FileWithoutLink(e, onShare = { share(context, e) }, onEdit = { onEdit(e.id) })
                    } else {
                        Box(
                            Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp),
                        ) {
                            QrImage(e.qrPayload(), Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Point your camera at the code",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (entries.size > 1) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(entries.size) { i ->
                        val active = i == pager.currentPage
                        Box(
                            Modifier.padding(4.dp).size(if (active) 10.dp else 8.dp).clip(CircleShape)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                ),
                        )
                    }
                }
            }
        }
    }
}

/** Full brightness and screen kept on while a QR code is showing, so it scans easily. */
@Composable
private fun KeepScreenBright() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(activity) {
        val window = activity.window
        val previous = window.attributes.screenBrightness
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window.attributes = window.attributes.apply { screenBrightness = previous }
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
private fun FileWithoutLink(e: CardEntry, onShare: () -> Unit, onEdit: () -> Unit) {
    Column(
        Modifier.widthIn(max = 420.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (e.filePath.isEmpty()) {
            Text("No file added yet.", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tap Edit to choose your CV or brochure (PDF or Word).",
                textAlign = TextAlign.Center,
            )
            Button(onClick = onEdit) { Text("Add file") }
        } else {
            Text(e.fileName, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                "A QR code can only hold a link, not a file. Share the file directly, " +
                    "or add a OneDrive or Google Drive link in Edit to get a QR code.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onShare) { Text("Share file") }
        }
    }
}

/** Shares the file itself for file entries, otherwise the QR code as a PNG. */
private fun share(context: Context, entry: CardEntry) {
    if (entry.kind == EntryKind.FILE && entry.filePath.isNotEmpty() && java.io.File(entry.filePath).exists()) {
        val uri = app.qrcard.util.Files.shareUri(context, java.io.File(entry.filePath))
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = entry.fileMime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, entry.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${entry.fileName}"))
        return
    }
    val bmp = QrGenerator.bitmap(entry.qrPayload(), 1024) ?: return
    val dir = context.cacheDir.resolve("shared").apply { mkdirs() }
    val file = dir.resolve("qr-${entry.id}.png")
    file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    val uri = app.qrcard.util.Files.shareUri(context, file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, entry.title)
        if (entry.kind == EntryKind.LINK || entry.kind == EntryKind.FILE) putExtra(Intent.EXTRA_TEXT, entry.qrPayload())
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share QR code"))
}

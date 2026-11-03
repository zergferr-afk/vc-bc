package app.qrcard.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.qrcard.data.CardEntry
import app.qrcard.data.EntryIcon
import app.qrcard.data.EntryKind
import app.qrcard.data.FieldType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    initial: CardEntry,
    isNew: Boolean,
    onCancel: () -> Unit,
    onSave: (CardEntry) -> Unit,
    onImportFile: (Uri) -> ImportedFile?,
) {
    var title by remember { mutableStateOf(initial.title) }
    var subtitle by remember { mutableStateOf(initial.subtitle) }
    var kind by remember { mutableStateOf(initial.kind) }
    var icon by remember { mutableStateOf(initial.icon) }
    val values = remember { mutableStateMapOf<String, String>().apply { putAll(initial.values) } }

    val draft = CardEntry(
        id = initial.id,
        title = title.trim().ifEmpty { "Untitled" },
        subtitle = subtitle.trim(),
        kind = kind,
        icon = icon,
        // Only keep the fields that belong to the chosen kind (plus the attached file for files).
        values = kind.fields.associate { it.key to values[it.key].orEmpty() } +
            if (kind == EntryKind.FILE) FILE_KEYS.associateWith { values[it].orEmpty() } else emptyMap(),
    )
    var fileError by remember { mutableStateOf(false) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val f = onImportFile(uri)
            fileError = f == null
            if (f != null) {
                values[CardEntry.FILE_PATH] = f.path
                values[CardEntry.FILE_NAME] = f.name
                values[CardEntry.FILE_MIME] = f.mime
                if (title.isBlank()) title = "My CV"
                if (subtitle.startsWith("Tap edit")) subtitle = ""
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New QR code" else "Edit QR code") },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel") }
                },
                actions = {
                    IconButton(onClick = { onSave(draft) }, enabled = !draft.isEmpty()) {
                        Icon(Icons.Filled.Check, "Save")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).size(180.dp)
                    .clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp),
            ) {
                QrImage(
                    draft.qrPayload(), Modifier.fillMaxSize(), sizePx = 512,
                    emptyText = if (kind == EntryKind.FILE) "Add a link below\nto get a QR code" else "Fill in the details\nto see the QR code",
                )
            }

            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Title (e.g. Consulting services)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = subtitle, onValueChange = { subtitle = it },
                label = { Text("Subtitle (optional)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            Text("Icon", style = MaterialTheme.typography.labelLarge)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EntryIcon.entries.forEach { option ->
                    val selected = option == icon
                    Box(
                        Modifier.clip(CircleShape)
                            .then(
                                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                else Modifier
                            )
                            .clickable { icon = option }
                            .padding(3.dp),
                    ) { EntryIconBadge(option, size = 40.dp, selected = selected) }
                }
            }

            Text("What the QR code opens", style = MaterialTheme.typography.labelLarge)
            KindPicker(kind) { kind = it }

            Spacer(Modifier.height(4.dp))
            if (kind == EntryKind.FILE) {
                val name = values[CardEntry.FILE_NAME].orEmpty()
                Text(
                    if (name.isEmpty()) "No file chosen yet" else "File: $name",
                    style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedButton(onClick = { pickFile.launch(DOC_TYPES) }) {
                    Text(if (name.isEmpty()) "Choose PDF or Word file" else "Replace file")
                }
                if (fileError) Text("Couldn't copy that file. Try again.", color = MaterialTheme.colorScheme.error)
                Text(
                    "The file is kept on your phone. You can share it from the full-screen view and attach it " +
                        "to intro emails. A QR code can only hold a link, so to let people scan it, upload the file " +
                        "to OneDrive or Google Drive and paste the share link here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            kind.fields.forEach { field ->
                val keyboard = when (field.type) {
                    FieldType.URL -> KeyboardType.Uri
                    FieldType.EMAIL -> KeyboardType.Email
                    FieldType.PHONE -> KeyboardType.Phone
                    else -> KeyboardType.Text
                }
                OutlinedTextField(
                    value = values[field.key].orEmpty(),
                    onValueChange = { values[field.key] = it },
                    label = { Text(field.label) },
                    singleLine = field.type != FieldType.MULTILINE,
                    minLines = if (field.type == FieldType.MULTILINE) 3 else 1,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

private val FILE_KEYS = listOf(CardEntry.FILE_PATH, CardEntry.FILE_NAME, CardEntry.FILE_MIME)

private val DOC_TYPES = arrayOf(
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
)

@Composable
private fun KindPicker(kind: EntryKind, onPick: (EntryKind) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(kind.label, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            EntryKind.entries.forEach { k ->
                DropdownMenuItem(text = { Text(k.label) }, onClick = { onPick(k); open = false })
            }
        }
    }
}

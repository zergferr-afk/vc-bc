package app.qrcard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.qrcard.data.CardEntry
import app.qrcard.data.EmailTemplate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    templates: List<EmailTemplate>,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(colors = brandBarColors(), title = { Text("Email templates") }) },
        bottomBar = bottomBar,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New template") })
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "One template per product or service. When you email someone from a scanned card, " +
                        "you pick a template and their name and company are filled in.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(templates, key = { it.id }) { t ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(t.id) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(t.name, style = MaterialTheme.typography.titleMedium)
                        Text(t.subject, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            t.body.lineSequence().filter { it.isNotBlank() }.drop(1).firstOrNull().orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditScreen(
    initial: EmailTemplate,
    isNew: Boolean,
    files: List<CardEntry>,
    onCancel: () -> Unit,
    onSave: (EmailTemplate) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var subject by remember { mutableStateOf(initial.subject) }
    var body by remember { mutableStateOf(initial.body) }
    var attachment by remember { mutableStateOf(initial.attachmentEntryId) }
    var confirmDelete by remember { mutableStateOf(false) }

    val draft = initial.copy(name = name.trim().ifEmpty { "Untitled" }, subject = subject, body = body, attachmentEntryId = attachment)

    Scaffold(
        topBar = {
            TopAppBar(colors = brandBarColors(), 
                title = { Text(if (isNew) "New template" else "Edit template") },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel") } },
                actions = {
                    if (!isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                    IconButton(onClick = { onSave(draft) }) { Icon(Icons.Filled.Check, "Save") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Template name (e.g. Consulting)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = subject, onValueChange = { subject = it }, label = { Text("Subject") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = body, onValueChange = { body = it }, label = { Text("Message") },
                minLines = 12, modifier = Modifier.fillMaxWidth(),
            )
            AttachmentPicker(files, attachment) { attachment = it }
            Text(
                "Placeholders you can use:\n" + EmailTemplate.PLACEHOLDERS.joinToString("  ") { "{$it}" } +
                    "\n\nAdd a fallback after a bar for when the card had no value, e.g. {firstName|Sir or Madam}. " +
                    "Your own details ({myName}, {myPhone} and so on) come from your contact card on the QR codes tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${initial.name}\"?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AttachmentPicker(files: List<CardEntry>, selectedId: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = files.firstOrNull { it.id == selectedId }
    Column {
        Text("Attachment", style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected?.let { "${it.title} (${it.fileName})" } ?: "No attachment", modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("No attachment") }, onClick = { onPick(""); open = false })
                files.forEach { f ->
                    DropdownMenuItem(text = { Text("${f.title} (${f.fileName})") }, onClick = { onPick(f.id); open = false })
                }
            }
        }
        if (files.isEmpty()) {
            Text(
                "To attach your CV, first add the file to the CV entry on the QR codes tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

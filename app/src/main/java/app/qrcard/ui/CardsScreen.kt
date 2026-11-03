package app.qrcard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.qrcard.data.EmailTemplate
import app.qrcard.data.ScannedContact
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(
    contacts: List<ScannedContact>,
    onOpen: (String) -> Unit,
    onEmail: (String) -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onAddManually: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = if (query.isBlank()) contacts else contacts.filter {
        listOf(it.fullName, it.company, it.email, it.jobTitle).any { f -> f.contains(query.trim(), ignoreCase = true) }
    }

    Scaffold(
        topBar = { TopAppBar(colors = brandBarColors(), title = { Text("Scanned cards") }) },
        bottomBar = bottomBar,
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(
                    onClick = { menu = true },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Scan card") },
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Take photo") }, onClick = { menu = false; onTakePhoto() })
                    DropdownMenuItem(text = { Text("Choose from gallery") }, onClick = { menu = false; onPickPhoto() })
                    DropdownMenuItem(text = { Text("Type in manually") }, onClick = { menu = false; onAddManually() })
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (contacts.isNotEmpty()) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Search name, company, email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            if (contacts.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Scan a business card and the name, company, email and phone are read for you. " +
                            "Then tap the card to send an intro email from Outlook.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(shown, key = { it.id }) { c ->
                        Card(Modifier.fillMaxWidth().clickable { onOpen(c.id) }) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Initials(c)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val line2 = listOf(c.jobTitle, c.company).filter { it.isNotBlank() }.joinToString(" · ")
                                    if (line2.isNotBlank()) Text(line2, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (c.lastEmailedAt > 0) {
                                        Text(
                                            "Emailed: ${c.lastTemplateName} · ${formatDate(c.lastEmailedAt)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    } else if (c.email.isBlank()) {
                                        Text("No email address", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                                IconButton(onClick = { onEmail(c.id) }) { Icon(Icons.Filled.Email, "Send intro email") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Initials(c: ScannedContact, size: androidx.compose.ui.unit.Dp = 44.dp) {
    Box(
        Modifier.size(size).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(c.initials, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
    }
}

fun formatDate(millis: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

/** Lets you pick which product or service the intro email is about. */
@Composable
fun TemplatePickerDialog(
    templates: List<EmailTemplate>,
    onPick: (EmailTemplate) -> Unit,
    onManageTemplates: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Which intro email?") },
        text = {
            Column {
                if (templates.isEmpty()) Text("You have no email templates yet.")
                templates.forEachIndexed { i, t ->
                    if (i > 0) HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(t.name) },
                        supportingContent = { Text(t.subject, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.clickable { onPick(t) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onManageTemplates) { Text("Edit templates") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ReadingCardDialog() {
    Dialog(onDismissRequest = {}) {
        Card {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Spacer(Modifier.width(16.dp))
                Text("Reading the card…")
            }
        }
    }
}

package app.qrcard.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.qrcard.data.ScannedContact
import app.qrcard.util.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CardPhoto(path: String, modifier: Modifier = Modifier) {
    if (path.isEmpty()) return
    val bmp by produceState<Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { Files.loadBitmap(path, 1400) }
    }
    bmp?.let {
        Image(
            it.asImageBitmap(), contentDescription = "Photo of the card",
            contentScale = ContentScale.Fit,
            modifier = modifier.clip(RoundedCornerShape(12.dp)),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    contact: ScannedContact,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSendIntro: () -> Unit,
) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(contact.displayName) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardPhoto(contact.imagePath, Modifier.fillMaxWidth().heightIn(max = 260.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(contact.fullName.ifBlank { "No name" }, style = MaterialTheme.typography.titleLarge)
                    if (contact.jobTitle.isNotBlank()) Text(contact.jobTitle)
                    if (contact.company.isNotBlank()) Text(contact.company, style = MaterialTheme.typography.titleSmall)
                    DetailLine("Email", contact.email) { startSafely(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${contact.email}"))) }
                    DetailLine("Phone", contact.phone) { startSafely(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phone.replace(" ", "")}"))) }
                    DetailLine("Website", contact.website) {
                        startSafely(context, Intent(Intent.ACTION_VIEW, Uri.parse(app.qrcard.data.CardEntry.normalizeUrl(contact.website))))
                    }
                    if (contact.notes.isNotBlank()) Text(contact.notes, style = MaterialTheme.typography.bodyMedium)
                    if (contact.lastEmailedAt > 0) {
                        Text(
                            "Intro email opened: ${contact.lastTemplateName} on ${formatDate(contact.lastEmailedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Button(onClick = onSendIntro, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Email, null)
                Spacer(Modifier.width(8.dp))
                Text("Send intro email")
            }
            if (contact.email.isBlank()) {
                Text(
                    "No email address was found on this card. Tap Edit to add one, or type it in Outlook.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedButton(onClick = { saveToPhoneContacts(context, contact) }, modifier = Modifier.fillMaxWidth()) {
                Text("Save to phone contacts")
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this card?") },
            text = { Text("${contact.displayName} and the card photo will be removed from the app.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String, onClick: () -> Unit) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp)) {
        Text("$label: ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

private fun startSafely(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app found to open this", Toast.LENGTH_SHORT).show()
    }
}

private fun saveToPhoneContacts(context: Context, c: ScannedContact) {
    val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
        type = ContactsContract.RawContacts.CONTENT_TYPE
        putExtra(ContactsContract.Intents.Insert.NAME, c.fullName)
        putExtra(ContactsContract.Intents.Insert.COMPANY, c.company)
        putExtra(ContactsContract.Intents.Insert.JOB_TITLE, c.jobTitle)
        putExtra(ContactsContract.Intents.Insert.EMAIL, c.email)
        putExtra(ContactsContract.Intents.Insert.PHONE, c.phone)
        putExtra(ContactsContract.Intents.Insert.NOTES, listOf(c.website, c.notes).filter { it.isNotBlank() }.joinToString("\n"))
    }
    startSafely(context, intent)
}

/** Check and correct what was read from the card, or type a contact in by hand. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactEditScreen(
    initial: ScannedContact,
    isNew: Boolean,
    message: String?,
    onCancel: () -> Unit,
    onSave: (ScannedContact) -> Unit,
) {
    var firstName by remember { mutableStateOf(initial.firstName) }
    var lastName by remember { mutableStateOf(initial.lastName) }
    var jobTitle by remember { mutableStateOf(initial.jobTitle) }
    var company by remember { mutableStateOf(initial.company) }
    var email by remember { mutableStateOf(initial.email) }
    var phone by remember { mutableStateOf(initial.phone) }
    var website by remember { mutableStateOf(initial.website) }
    var notes by remember { mutableStateOf(initial.notes) }
    var showRaw by remember { mutableStateOf(false) }

    val draft = initial.copy(
        firstName = firstName.trim(), lastName = lastName.trim(), jobTitle = jobTitle.trim(),
        company = company.trim(), email = email.trim(), phone = phone.trim(),
        website = website.trim(), notes = notes.trim(),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Check the details" else "Edit card") },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel") } },
                actions = { IconButton(onClick = { onSave(draft) }) { Icon(Icons.Filled.Check, "Save") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (message != null) Text(message, color = MaterialTheme.colorScheme.error)
            CardPhoto(initial.imagePath, Modifier.fillMaxWidth().heightIn(max = 220.dp))
            if (isNew && message == null && initial.imagePath.isNotEmpty()) {
                Text(
                    "Read from the card. Please check each field before saving.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ContactField("First name", firstName) { firstName = it }
            ContactField("Last name", lastName) { lastName = it }
            ContactField("Job title", jobTitle) { jobTitle = it }
            ContactField("Company", company) { company = it }
            ContactField("Email", email, KeyboardType.Email) { email = it }
            ContactField("Phone", phone, KeyboardType.Phone) { phone = it }
            ContactField("Website", website, KeyboardType.Uri) { website = it }
            OutlinedTextField(
                value = notes, onValueChange = { notes = it }, label = { Text("Notes (where you met, interests)") },
                minLines = 3, modifier = Modifier.fillMaxWidth(),
            )
            if (initial.rawText.isNotBlank()) {
                TextButton(onClick = { showRaw = !showRaw }) {
                    Text(if (showRaw) "Hide text read from the card" else "Show all text read from the card")
                }
                if (showRaw) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(initial.rawText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Button(onClick = { onSave(draft) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Save card") }
        }
    }
}

@Composable
private fun ContactField(label: String, value: String, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

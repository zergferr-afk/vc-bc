package app.qrcard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.qrcard.data.CardEntry
import app.qrcard.data.EmailTemplate
import app.qrcard.data.ScannedContact
import app.qrcard.email.EmailLauncher
import java.io.File

/** The filled-in intro email. Tweak it if you like, then it opens in Outlook ready to send. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    contact: ScannedContact,
    template: EmailTemplate,
    me: CardEntry?,
    attachment: CardEntry?,
    onBack: () -> Unit,
    onOpened: () -> Unit,
) {
    val context = LocalContext.current
    val vars = remember(contact, me) { EmailTemplate.variables(contact, me) }
    var to by rememberSaveable { mutableStateOf(contact.email) }
    var subject by rememberSaveable { mutableStateOf(EmailTemplate.fill(template.subject, vars)) }
    var body by rememberSaveable { mutableStateOf(EmailTemplate.fill(template.body, vars)) }
    val hasFile = attachment != null && attachment.filePath.isNotEmpty() && File(attachment.filePath).exists()
    var attach by rememberSaveable { mutableStateOf(hasFile) }
    val outlook = remember { EmailLauncher.isOutlookInstalled(context) }

    Scaffold(
        topBar = {
            TopAppBar(colors = brandBarColors(), 
                title = { Text(template.name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = to, onValueChange = { to = it }, label = { Text("To") }, singleLine = true,
                isError = to.isBlank(),
                supportingText = { if (to.isBlank()) Text("No email address on the card. Type it here or in Outlook.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Message") }, minLines = 10, modifier = Modifier.fillMaxWidth())
            if (hasFile) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = attach, onCheckedChange = { attach = it })
                    Spacer(Modifier.width(12.dp))
                    Text("Attach ${attachment!!.fileName}")
                }
            }
            if (body.contains('[')) {
                Text(
                    "The message still has [bracketed] text from the template. Edit the template once to replace it with your own wording.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = {
                    EmailLauncher.open(
                        context, to, subject, body,
                        attachment = if (attach && hasFile) File(attachment!!.filePath) else null,
                        attachmentMime = attachment?.fileMime,
                    )
                    onOpened()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null)
                Spacer(Modifier.width(8.dp))
                Text(if (outlook) "Open in Outlook" else "Open in email app")
            }
            if (!outlook) {
                Text(
                    "Outlook isn't installed, so you'll be asked which email app to use.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

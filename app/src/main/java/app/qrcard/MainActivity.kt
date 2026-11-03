package app.qrcard

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import app.qrcard.data.CardEntry
import app.qrcard.data.EmailTemplate
import app.qrcard.data.EntryIcon
import app.qrcard.data.EntryKind
import app.qrcard.data.ScannedContact
import app.qrcard.ui.AppTheme
import app.qrcard.ui.AppViewModel
import app.qrcard.ui.CardsScreen
import app.qrcard.ui.ComposeScreen
import app.qrcard.ui.ContactDetailScreen
import app.qrcard.ui.ContactEditScreen
import app.qrcard.ui.EditScreen
import app.qrcard.ui.ListScreen
import app.qrcard.ui.ReadingCardDialog
import app.qrcard.ui.ScanState
import app.qrcard.ui.ShowScreen
import app.qrcard.ui.TemplateEditScreen
import app.qrcard.ui.TemplatePickerDialog
import app.qrcard.ui.TemplatesScreen
import app.qrcard.util.Files
import java.io.File
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light status bar icons over the navy top bar.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { AppTheme { Surface { App() } } }
    }
}

/**
 * Screens are plain strings so they survive rotation and the app being closed by the camera:
 * tabs "codes", "cards", "templates", and sub-screens such as "show:2", "edit:<id>",
 * "contact:<id>", "compose:<contactId>:<templateId>".
 */
private fun parentOf(route: String): String = when (route.substringBefore(':')) {
    "show", "edit", "new" -> "codes"
    "contact", "scan", "newContact" -> "cards"
    "contactEdit", "compose" -> "contact:" + route.split(':')[1]
    "template", "newTemplate" -> "templates"
    else -> "codes"
}

@Composable
private fun App(vm: AppViewModel = viewModel()) {
    val context = LocalContext.current
    var route by rememberSaveable { mutableStateOf("codes") }
    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pickTemplateFor by rememberSaveable { mutableStateOf<String?>(null) }

    fun back() { route = parentOf(route) }

    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = photoPath
        photoPath = null
        if (ok && path != null) vm.readCard(File(path)) { route = "scan:${it.id}" }
        else path?.let { File(it).delete() }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) vm.importCardPhoto(uri) { route = "scan:${it.id}" }
    }
    fun startCamera() {
        val file = vm.newCardPhotoFile()
        photoPath = file.path
        takePhoto.launch(Files.shareUri(context, file))
    }

    val tab = when {
        route == "cards" -> "cards"
        route == "templates" -> "templates"
        else -> "codes"
    }
    val bottomBar: @Composable () -> Unit = {
        NavigationBar {
            NavigationBarItem(
                selected = tab == "codes", onClick = { route = "codes" },
                icon = { Icon(Icons.Filled.Star, null) }, label = { Text("My QR codes") },
            )
            NavigationBarItem(
                selected = tab == "cards", onClick = { route = "cards" },
                icon = { Icon(Icons.Filled.Person, null) }, label = { Text("Scanned cards") },
            )
            NavigationBarItem(
                selected = tab == "templates", onClick = { route = "templates" },
                icon = { Icon(Icons.Filled.Email, null) }, label = { Text("Templates") },
            )
        }
    }

    if (route !in setOf("codes", "cards", "templates")) BackHandler { back() }
    else if (route != "codes") BackHandler { route = "codes" }

    val parts = route.split(':')
    val arg = parts.getOrNull(1).orEmpty()
    when (parts[0]) {
        "show" -> ShowScreen(
            entries = vm.entries,
            startIndex = arg.toIntOrNull() ?: 0,
            onBack = ::back,
            onEdit = { route = "edit:$it" },
        )
        "edit", "new" -> {
            val initial = remember(arg) {
                vm.findEntry(arg) ?: CardEntry(id = arg, title = "", kind = EntryKind.LINK, icon = EntryIcon.STAR)
            }
            EditScreen(
                initial = initial,
                isNew = parts[0] == "new",
                onCancel = ::back,
                onSave = { vm.upsertEntry(it); back() },
                onImportFile = { uri -> vm.importFile(arg, uri) },
            )
        }
        "contact" -> {
            val c = vm.findContact(arg)
            if (c == null) route = "cards" else ContactDetailScreen(
                contact = c,
                onBack = ::back,
                onEdit = { route = "contactEdit:${c.id}" },
                onDelete = { vm.deleteContact(c.id); route = "cards" },
                onSendIntro = { pickTemplateFor = c.id },
            )
        }
        "scan", "newContact", "contactEdit" -> {
            val isNew = parts[0] != "contactEdit"
            val initial = remember(route) {
                vm.findContact(arg) ?: vm.pendingContact?.takeIf { it.id == arg } ?: ScannedContact(id = arg)
            }
            ContactEditScreen(
                initial = initial,
                isNew = isNew,
                message = if (parts[0] == "scan") vm.scanMessage else null,
                onCancel = { if (isNew) vm.discardPending(); back() },
                onSave = { vm.upsertContact(it); route = "contact:${it.id}" },
            )
        }
        "compose" -> {
            val c = vm.findContact(arg)
            val t = vm.findTemplate(parts.getOrNull(2).orEmpty())
            if (c == null || t == null) route = "cards" else ComposeScreen(
                contact = c,
                template = t,
                me = vm.myCard(),
                attachment = vm.findEntry(t.attachmentEntryId),
                onBack = ::back,
                onOpened = { vm.markEmailed(c.id, t.name) },
            )
        }
        "template", "newTemplate" -> {
            val initial = remember(arg) {
                vm.findTemplate(arg) ?: EmailTemplate(id = arg, name = "", subject = "", body = "Dear {firstName|Sir or Madam},\n\n\n\nKind regards,\n{myName}")
            }
            TemplateEditScreen(
                initial = initial,
                isNew = parts[0] == "newTemplate",
                files = vm.fileEntries(),
                onCancel = ::back,
                onSave = { vm.upsertTemplate(it); back() },
                onDelete = { vm.deleteTemplate(arg); back() },
            )
        }
        "cards" -> CardsScreen(
            contacts = vm.contacts,
            onOpen = { route = "contact:$it" },
            onEmail = { pickTemplateFor = it },
            onTakePhoto = ::startCamera,
            onPickPhoto = {
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onAddManually = { route = "newContact:" + UUID.randomUUID() },
            bottomBar = bottomBar,
        )
        "templates" -> TemplatesScreen(
            templates = vm.templates,
            onOpen = { route = "template:$it" },
            onAdd = { route = "newTemplate:" + UUID.randomUUID() },
            bottomBar = bottomBar,
        )
        else -> ListScreen(
            entries = vm.entries,
            onShow = { route = "show:$it" },
            onEdit = { route = "edit:$it" },
            onAdd = { route = "new:" + UUID.randomUUID() },
            onMove = vm::moveEntry,
            onDelete = vm::deleteEntry,
            bottomBar = bottomBar,
        )
    }

    pickTemplateFor?.let { contactId ->
        TemplatePickerDialog(
            templates = vm.templates,
            onPick = { t -> pickTemplateFor = null; route = "compose:$contactId:${t.id}" },
            onManageTemplates = { pickTemplateFor = null; route = "templates" },
            onDismiss = { pickTemplateFor = null },
        )
    }
    if (vm.scanState == ScanState.Reading) ReadingCardDialog()
}

package app.qrcard.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import app.qrcard.data.CardEntry
import app.qrcard.data.EmailTemplate
import app.qrcard.data.EntryKind
import app.qrcard.data.ScannedContact
import app.qrcard.data.Store
import app.qrcard.data.defaultEntries
import app.qrcard.data.defaultTemplates
import app.qrcard.export.ContactExport
import app.qrcard.export.ExportFormat
import app.qrcard.scan.CardParser
import app.qrcard.scan.CardScanner
import app.qrcard.util.Files
import java.io.File
import java.util.UUID

sealed interface ScanState {
    data object Idle : ScanState
    data object Reading : ScanState
}

data class ImportedFile(val path: String, val name: String, val mime: String)

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    val entries = mutableStateListOf<CardEntry>().apply {
        addAll(store.load(ENTRIES, { CardEntry.fromJson(it) }, ::defaultEntries, CardEntry::toJson))
    }
    val contacts = mutableStateListOf<ScannedContact>().apply {
        addAll(store.load(CONTACTS, { ScannedContact.fromJson(it) }, { emptyList() }, ScannedContact::toJson))
    }
    val templates = mutableStateListOf<EmailTemplate>().apply {
        addAll(store.load(TEMPLATES, { EmailTemplate.fromJson(it) }, ::defaultTemplates, EmailTemplate::toJson))
    }

    /** A freshly scanned card waiting for the user to check it and save. */
    var pendingContact by mutableStateOf<ScannedContact?>(null)
    var scanState by mutableStateOf<ScanState>(ScanState.Idle)
    var scanMessage by mutableStateOf<String?>(null)

    // ---- QR entries ----

    fun findEntry(id: String) = entries.firstOrNull { it.id == id }

    fun upsertEntry(entry: CardEntry) {
        val i = entries.indexOfFirst { it.id == entry.id }
        if (i >= 0) {
            val old = entries[i]
            if (old.filePath.isNotEmpty() && old.filePath != entry.filePath) File(old.filePath).parentFile?.deleteRecursively()
            entries[i] = entry
        } else {
            entries.add(entry)
        }
        saveEntries()
    }

    fun deleteEntry(id: String) {
        findEntry(id)?.filePath?.takeIf { it.isNotEmpty() }?.let { File(it).parentFile?.deleteRecursively() }
        entries.removeAll { it.id == id }
        saveEntries()
    }

    fun moveEntry(id: String, delta: Int) {
        val from = entries.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in entries.indices) return
        entries.add(to, entries.removeAt(from))
        saveEntries()
    }

    /** Copies a picked CV or brochure into the app, so it stays available offline. */
    fun importFile(entryId: String, uri: Uri): ImportedFile? {
        val name = Files.safeName(Files.displayName(app, uri) ?: "document")
        val mime = Files.mimeType(app, uri) ?: guessMime(name)
        val dest = File(app.filesDir, "files/$entryId/${UUID.randomUUID().toString().take(8)}/$name")
        return if (Files.copy(app, uri, dest)) ImportedFile(dest.path, name, mime) else null
    }

    /** Your own details, taken from the first contact card entry, for email signatures. */
    fun myCard(): CardEntry? = entries.firstOrNull { it.kind == EntryKind.CONTACT }

    fun fileEntries(): List<CardEntry> = entries.filter { it.kind == EntryKind.FILE && it.filePath.isNotEmpty() }

    // ---- Scanned cards ----

    fun findContact(id: String) = contacts.firstOrNull { it.id == id }

    fun newCardPhotoFile(): File = File(app.filesDir, "cards/${UUID.randomUUID()}.jpg").apply { parentFile?.mkdirs() }

    /** Reads a card photo already saved at [photo], then calls [onReady] with the guessed contact. */
    fun readCard(photo: File, onReady: (ScannedContact) -> Unit) {
        scanState = ScanState.Reading
        scanMessage = null
        CardScanner.readLines(app, Uri.fromFile(photo)) { result ->
            val contact = result.fold(
                onSuccess = { lines ->
                    if (lines.isEmpty()) scanMessage = "No text found on the photo. Fill in the details below."
                    CardParser.parse(lines)
                },
                onFailure = {
                    scanMessage = "Couldn't read the card. Fill in the details below."
                    ScannedContact()
                },
            ).copy(imagePath = photo.path)
            pendingContact = contact
            scanState = ScanState.Idle
            onReady(contact)
        }
    }

    fun importCardPhoto(uri: Uri, onReady: (ScannedContact) -> Unit) {
        val dest = newCardPhotoFile()
        if (Files.copy(app, uri, dest)) readCard(dest, onReady)
        else scanMessage = "Couldn't open that photo."
    }

    fun upsertContact(contact: ScannedContact) {
        val i = contacts.indexOfFirst { it.id == contact.id }
        if (i >= 0) contacts[i] = contact else contacts.add(0, contact)
        if (pendingContact?.id == contact.id) pendingContact = null
        saveContacts()
    }

    fun discardPending() {
        pendingContact?.let { p -> if (findContact(p.id) == null && p.imagePath.isNotEmpty()) File(p.imagePath).delete() }
        pendingContact = null
    }

    fun deleteContact(id: String) {
        findContact(id)?.imagePath?.takeIf { it.isNotEmpty() }?.let { File(it).delete() }
        contacts.removeAll { it.id == id }
        saveContacts()
    }

    /** Writes all scanned cards to a spreadsheet file that can be attached to an email. */
    fun exportContacts(format: ExportFormat): File? = runCatching {
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val file = File(app.cacheDir, "shared/scanned-cards-$stamp.${format.extension}")
        file.parentFile?.mkdirs()
        file.outputStream().use { ContactExport.write(format, contacts.toList(), it) }
        file
    }.getOrNull()

    fun markEmailed(contactId: String, templateName: String) {
        val c = findContact(contactId) ?: return
        upsertContact(c.copy(lastEmailedAt = System.currentTimeMillis(), lastTemplateName = templateName))
    }

    // ---- Email templates ----

    fun findTemplate(id: String) = templates.firstOrNull { it.id == id }

    fun upsertTemplate(t: EmailTemplate) {
        val i = templates.indexOfFirst { it.id == t.id }
        if (i >= 0) templates[i] = t else templates.add(t)
        saveTemplates()
    }

    fun deleteTemplate(id: String) {
        templates.removeAll { it.id == id }
        saveTemplates()
    }

    private fun saveEntries() = store.save(ENTRIES, entries.toList(), CardEntry::toJson)
    private fun saveContacts() = store.save(CONTACTS, contacts.toList(), ScannedContact::toJson)
    private fun saveTemplates() = store.save(TEMPLATES, templates.toList(), EmailTemplate::toJson)

    companion object {
        private const val ENTRIES = "entries.json"
        private const val CONTACTS = "contacts.json"
        private const val TEMPLATES = "templates.json"

        fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> "application/octet-stream"
        }
    }
}

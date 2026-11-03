package app.qrcard.data

import org.json.JSONObject
import java.util.UUID

/**
 * What a QR code encodes. Each kind has its own set of editable fields
 * and its own way of turning them into the text stored in the QR code.
 */
enum class EntryKind(val label: String, val fields: List<Field>) {
    CONTACT(
        "Contact card (vCard)",
        listOf(
            Field("firstName", "First name"),
            Field("lastName", "Last name"),
            Field("org", "Company"),
            Field("jobTitle", "Job title"),
            Field("phone", "Phone", FieldType.PHONE),
            Field("email", "Email", FieldType.EMAIL),
            Field("website", "Website", FieldType.URL),
            Field("linkedin", "LinkedIn URL", FieldType.URL),
            Field("address", "Address"),
            Field("note", "Note", FieldType.MULTILINE),
        )
    ),
    LINK(
        "Link (services, trainings, LinkedIn, ...)",
        listOf(Field("url", "URL", FieldType.URL))
    ),
    FILE(
        "File (CV, brochure: PDF or Word)",
        listOf(Field("url", "Link for the QR code, e.g. OneDrive (optional)", FieldType.URL))
    ),
    EMAIL(
        "Email message",
        listOf(
            Field("to", "To", FieldType.EMAIL),
            Field("subject", "Subject"),
            Field("body", "Message", FieldType.MULTILINE),
        )
    ),
    PHONE("Phone call", listOf(Field("phone", "Phone", FieldType.PHONE))),
    WIFI(
        "Wi-Fi network",
        listOf(
            Field("ssid", "Network name"),
            Field("password", "Password"),
            Field("security", "Security (WPA, WEP or nopass)"),
        )
    ),
    TEXT("Plain text", listOf(Field("text", "Text", FieldType.MULTILINE)));
}

enum class FieldType { TEXT, MULTILINE, URL, EMAIL, PHONE }

data class Field(val key: String, val label: String, val type: FieldType = FieldType.TEXT)

/** Icons the user can pick for an entry, so the list is easy to scan. */
enum class EntryIcon { PERSON, SERVICES, TRAINING, CV, LINKEDIN, WEBSITE, EMAIL, PHONE, WIFI, STAR }

data class CardEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val subtitle: String = "",
    val kind: EntryKind,
    val icon: EntryIcon,
    val values: Map<String, String> = emptyMap(),
) {
    fun value(key: String): String = values[key].orEmpty().trim()

    /** The exact text encoded in the QR code. */
    fun qrPayload(): String = when (kind) {
        EntryKind.CONTACT -> buildVCard()
        EntryKind.LINK, EntryKind.FILE -> normalizeUrl(value("url"))
        EntryKind.EMAIL -> buildString {
            append("mailto:").append(value("to"))
            val params = listOfNotNull(
                value("subject").takeIf { it.isNotEmpty() }?.let { "subject=" + enc(it) },
                value("body").takeIf { it.isNotEmpty() }?.let { "body=" + enc(it) },
            )
            if (params.isNotEmpty()) append("?").append(params.joinToString("&"))
        }
        EntryKind.PHONE -> "tel:" + value("phone").replace(" ", "")
        EntryKind.WIFI -> {
            val sec = value("security").ifEmpty { "WPA" }
            "WIFI:T:$sec;S:${wifiEsc(value("ssid"))};P:${wifiEsc(value("password"))};;"
        }
        EntryKind.TEXT -> value("text")
    }

    fun isEmpty(): Boolean = kind.fields.all { value(it.key).isEmpty() } && filePath.isEmpty()

    /** For FILE entries: the copy of the file kept inside the app. */
    val filePath: String get() = value(FILE_PATH)
    val fileName: String get() = value(FILE_NAME)
    val fileMime: String get() = value(FILE_MIME).ifEmpty { "application/octet-stream" }

    private fun buildVCard(): String {
        val first = value("firstName")
        val last = value("lastName")
        val lines = mutableListOf("BEGIN:VCARD", "VERSION:3.0")
        lines += "N:${vEsc(last)};${vEsc(first)};;;"
        lines += "FN:${vEsc(listOf(first, last).filter { it.isNotEmpty() }.joinToString(" "))}"
        value("org").takeIf { it.isNotEmpty() }?.let { lines += "ORG:${vEsc(it)}" }
        value("jobTitle").takeIf { it.isNotEmpty() }?.let { lines += "TITLE:${vEsc(it)}" }
        value("phone").takeIf { it.isNotEmpty() }?.let { lines += "TEL;TYPE=CELL:$it" }
        value("email").takeIf { it.isNotEmpty() }?.let { lines += "EMAIL;TYPE=WORK:$it" }
        value("website").takeIf { it.isNotEmpty() }?.let { lines += "URL:${normalizeUrl(it)}" }
        value("linkedin").takeIf { it.isNotEmpty() }?.let {
            // iPhones read X-SOCIALPROFILE; Android contacts read the extra URL line.
            lines += "X-SOCIALPROFILE;TYPE=linkedin:${normalizeUrl(it)}"
            lines += "URL;TYPE=LinkedIn:${normalizeUrl(it)}"
        }
        value("address").takeIf { it.isNotEmpty() }?.let { lines += "ADR;TYPE=WORK:;;${vEsc(it)};;;;" }
        value("note").takeIf { it.isNotEmpty() }?.let { lines += "NOTE:${vEsc(it)}" }
        lines += "END:VCARD"
        return lines.joinToString("\r\n")
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("subtitle", subtitle)
        put("kind", kind.name)
        put("icon", icon.name)
        put("values", JSONObject(values))
    }

    companion object {
        const val FILE_PATH = "_file"
        const val FILE_NAME = "_fileName"
        const val FILE_MIME = "_mime"

        fun fromJson(o: JSONObject): CardEntry {
            val v = o.optJSONObject("values") ?: JSONObject()
            val map = v.keys().asSequence().associateWith { v.optString(it) }
            return CardEntry(
                id = o.getString("id"),
                title = o.optString("title"),
                subtitle = o.optString("subtitle"),
                kind = runCatching { EntryKind.valueOf(o.getString("kind")) }.getOrDefault(EntryKind.TEXT),
                icon = runCatching { EntryIcon.valueOf(o.getString("icon")) }.getOrDefault(EntryIcon.STAR),
                values = map,
            )
        }

        fun normalizeUrl(url: String): String {
            val u = url.trim()
            if (u.isEmpty()) return u
            return if (u.contains("://") || u.startsWith("mailto:") || u.startsWith("tel:")) u else "https://$u"
        }

        private fun vEsc(s: String) = s.replace("\\", "\\\\").replace(",", "\\,")
            .replace(";", "\\;").replace("\n", "\\n")

        private fun wifiEsc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;")
            .replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")

        private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    }
}

/** Placeholder entries shown on first launch. Edit them in the app. */
fun defaultEntries(): List<CardEntry> = listOf(
    CardEntry(
        title = "My contact details",
        subtitle = "Saves straight into their contacts",
        kind = EntryKind.CONTACT,
        icon = EntryIcon.PERSON,
        values = mapOf(
            "firstName" to "Your",
            "lastName" to "Name",
            "org" to "Your Company",
            "jobTitle" to "Consultant",
            "phone" to "+1 555 000 0000",
            "email" to "you@example.com",
            "website" to "https://example.com",
            "linkedin" to "https://www.linkedin.com/in/your-profile",
        ),
    ),
    CardEntry(
        title = "Consulting services",
        subtitle = "What we offer",
        kind = EntryKind.LINK,
        icon = EntryIcon.SERVICES,
        values = mapOf("url" to "https://example.com/consulting"),
    ),
    CardEntry(
        title = "Trainings",
        subtitle = "Upcoming courses and workshops",
        kind = EntryKind.LINK,
        icon = EntryIcon.TRAINING,
        values = mapOf("url" to "https://example.com/trainings"),
    ),
    CardEntry(
        title = "My CV",
        subtitle = "Tap edit to add your CV (PDF or Word)",
        kind = EntryKind.FILE,
        icon = EntryIcon.CV,
    ),
    CardEntry(
        title = "LinkedIn",
        subtitle = "Connect with me",
        kind = EntryKind.LINK,
        icon = EntryIcon.LINKEDIN,
        values = mapOf("url" to "https://www.linkedin.com/in/your-profile"),
    ),
)

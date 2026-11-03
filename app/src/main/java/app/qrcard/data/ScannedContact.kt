package app.qrcard.data

import org.json.JSONObject
import java.util.UUID

/** A person whose physical business card was scanned. */
data class ScannedContact(
    val id: String = UUID.randomUUID().toString(),
    val firstName: String = "",
    val lastName: String = "",
    val company: String = "",
    val jobTitle: String = "",
    val email: String = "",
    val phone: String = "",
    val website: String = "",
    val notes: String = "",
    val imagePath: String = "",
    val rawText: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastEmailedAt: Long = 0L,
    val lastTemplateName: String = "",
) {
    val fullName: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")

    val displayName: String
        get() = fullName.ifBlank { email.ifBlank { company.ifBlank { "Unnamed card" } } }

    val initials: String
        get() = listOf(firstName, lastName).mapNotNull { it.trim().firstOrNull()?.uppercaseChar() }
            .joinToString("").ifEmpty { displayName.take(1).uppercase() }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("firstName", firstName)
        put("lastName", lastName)
        put("company", company)
        put("jobTitle", jobTitle)
        put("email", email)
        put("phone", phone)
        put("website", website)
        put("notes", notes)
        put("imagePath", imagePath)
        put("rawText", rawText)
        put("createdAt", createdAt)
        put("lastEmailedAt", lastEmailedAt)
        put("lastTemplateName", lastTemplateName)
    }

    companion object {
        fun fromJson(o: JSONObject) = ScannedContact(
            id = o.getString("id"),
            firstName = o.optString("firstName"),
            lastName = o.optString("lastName"),
            company = o.optString("company"),
            jobTitle = o.optString("jobTitle"),
            email = o.optString("email"),
            phone = o.optString("phone"),
            website = o.optString("website"),
            notes = o.optString("notes"),
            imagePath = o.optString("imagePath"),
            rawText = o.optString("rawText"),
            createdAt = o.optLong("createdAt"),
            lastEmailedAt = o.optLong("lastEmailedAt"),
            lastTemplateName = o.optString("lastTemplateName"),
        )
    }
}

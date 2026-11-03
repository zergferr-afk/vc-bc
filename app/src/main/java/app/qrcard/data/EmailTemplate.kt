package app.qrcard.data

import org.json.JSONObject
import java.util.UUID

/**
 * An introductory email. Subject and body can contain placeholders such as {firstName}
 * or {company}. A fallback can follow a bar: {firstName|Sir or Madam} uses "Sir or Madam"
 * when the card had no name.
 */
data class EmailTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val subject: String,
    val body: String,
    /** Id of a FILE entry (such as the CV) to attach, or empty for none. */
    val attachmentEntryId: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("subject", subject)
        put("body", body)
        put("attachmentEntryId", attachmentEntryId)
    }

    companion object {
        fun fromJson(o: JSONObject) = EmailTemplate(
            id = o.getString("id"),
            name = o.optString("name"),
            subject = o.optString("subject"),
            body = o.optString("body"),
            attachmentEntryId = o.optString("attachmentEntryId"),
        )

        val PLACEHOLDERS = listOf(
            "firstName", "lastName", "fullName", "company", "jobTitle",
            "myName", "myJobTitle", "myCompany", "myPhone", "myEmail", "myWebsite", "myLinkedIn",
        )

        private val TOKEN = Regex("\\{(\\w+)(?:\\|([^}]*))?\\}")

        fun fill(text: String, vars: Map<String, String>): String =
            TOKEN.replace(text) { m ->
                val key = m.groupValues[1]
                if (key !in vars) return@replace m.value
                vars[key].orEmpty().ifBlank { m.groupValues[2] }
            }

        /** Values for the placeholders: the scanned person, plus your own details from your contact card. */
        fun variables(contact: ScannedContact, me: CardEntry?): Map<String, String> {
            fun v(k: String) = me?.value(k).orEmpty()
            return mapOf(
                "firstName" to contact.firstName,
                "lastName" to contact.lastName,
                "fullName" to contact.fullName,
                "company" to contact.company,
                "jobTitle" to contact.jobTitle,
                "myName" to listOf(v("firstName"), v("lastName")).filter { it.isNotEmpty() }.joinToString(" "),
                "myJobTitle" to v("jobTitle"),
                "myCompany" to v("org"),
                "myPhone" to v("phone"),
                "myEmail" to v("email"),
                "myWebsite" to v("website"),
                "myLinkedIn" to v("linkedin"),
            )
        }
    }
}

private const val SIGNATURE = """Kind regards,

{myName}
{myJobTitle}, {myCompany}
{myPhone}
{myEmail}"""

/** Starting templates. Replace the [bracketed] parts with your own wording in the app. */
fun defaultTemplates(): List<EmailTemplate> = listOf(
    EmailTemplate(
        name = "Consulting services",
        subject = "Consulting services from {myCompany}",
        body = """Dear {firstName|Sir or Madam},

It was a pleasure meeting you. As promised, here is a short overview of the consulting services we offer:

- [Service 1: one line on what it is and the problem it solves]
- [Service 2: one line]
- [Service 3: one line]

[One or two sentences on a relevant project or result.]

I would be glad to set up a short call to discuss how we could support {company|your team}. Would sometime next week suit you?

$SIGNATURE""",
    ),
    EmailTemplate(
        name = "Trainings",
        subject = "Training courses from {myCompany}",
        body = """Dear {firstName|Sir or Madam},

Thank you for your time today. I would like to share the training courses we run, which may be of interest to {company|your team}:

- [Course 1: duration, format (on site or online), who it is for]
- [Course 2: duration, format, who it is for]
- [Course 3: duration, format, who it is for]

[Upcoming dates, or a note that courses can be tailored and run on site.]

Please let me know if you would like the full course outline or a quote.

$SIGNATURE""",
    ),
    EmailTemplate(
        name = "Pumpsim / Ventsim / CONTROL",
        subject = "Pumpsim, Ventsim and CONTROL for {company|your operation}",
        body = """Dear {firstName|Sir or Madam},

It was good to meet you. Following our conversation, here is a short introduction to the software we provide:

- Ventsim: [mine ventilation modelling and simulation]
- Pumpsim: [mine water and pumping network simulation]
- CONTROL: [ventilation monitoring and control]

[A sentence on licensing, support, or a demo or trial offer.]

I would be happy to arrange a short online demonstration for you and your team at {company|your site}.

$SIGNATURE""",
    ),
)

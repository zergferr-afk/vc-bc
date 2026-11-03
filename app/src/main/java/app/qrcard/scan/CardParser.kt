package app.qrcard.scan

import app.qrcard.data.ScannedContact

/** One line of text read from a card, with its position and text height in pixels. */
data class OcrLine(val text: String, val top: Int = 0, val height: Int = 0)

/**
 * Turns the text read from a business card into contact fields.
 * It is a best guess, so the app always shows the result for review before saving.
 */
object CardParser {
    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}")
    private val PHONE = Regex("\\+?\\(?\\d[\\d\\s().\\-/]{6,}\\d")
    private val URL = Regex(
        "(?i)\\b(?:https?://)?(?:www\\.)?[a-z0-9][a-z0-9-]*(?:\\.[a-z0-9-]+)*\\." +
            "(?:com|net|org|io|co|biz|info|ai|app|tech|global|au|uk|za|ca|de|fr|ru|kz|cl|pe|mx|br|in|nz|us|eu|ch|nl|se|no|fi|pl|mn|gh|zm|ar)" +
            "(?:\\.[a-z]{2})?(?:/\\S*)?\\b"
    )
    private val MOBILE_LABEL = Regex("(?i)(\\bm\\b|mob|mobile|cell|\\bc\\b|\\bhp\\b|handy|whatsapp)\\W*$")
    private val OFFICE_LABEL = Regex("(?i)(\\bt\\b|tel|ph|phone|office|direct|\\bd\\b|\\bo\\b|\\bw\\b|work)\\W*$")
    private val FAX_LABEL = Regex("(?i)(fax|\\bf\\b)\\W*$")
    private val NAME_WORD = Regex("[\\p{L}][\\p{L}'’.\\-]*")
    private val HONORIFICS = setOf("dr", "dr.", "mr", "mr.", "mrs", "mrs.", "ms", "ms.", "prof", "prof.", "eng", "eng.", "ir", "ir.")

    private val JOB_WORDS = setOf(
        "manager", "director", "engineer", "ceo", "cfo", "coo", "cto", "cio", "founder", "co-founder", "president",
        "vice", "vp", "head", "chief", "officer", "consultant", "specialist", "lead", "partner", "superintendent",
        "supervisor", "geologist", "analyst", "coordinator", "advisor", "adviser", "executive", "owner", "principal",
        "representative", "technician", "metallurgist", "planner", "foreman", "administrator", "assistant",
        "associate", "developer", "architect", "designer", "scientist", "professor", "lecturer", "secretary",
        "accountant", "buyer", "procurement", "senior", "junior", "trainee", "intern", "chairman", "md", "gm",
    )
    private val COMPANY_WORDS = setOf(
        "ltd", "ltd.", "limited", "inc", "inc.", "llc", "llp", "gmbh", "pty", "plc", "corp", "corp.", "corporation",
        "company", "co.", "group", "s.a.", "sa", "ag", "bv", "nv", "srl", "spa", "s.p.a.", "ab", "oy", "holdings",
        "industries", "mining", "mines", "resources", "consulting", "solutions", "technologies", "technology",
        "engineering", "services", "international", "global", "systems", "minerals", "metals", "gold", "copper",
        "jsc", "ojsc", "ooo", "ltda", "s.a.c.", "sac", "university",
    )
    private val GENERIC_DOMAINS = setOf(
        "gmail", "googlemail", "yahoo", "hotmail", "outlook", "live", "icloud", "me", "aol", "mail", "proton",
        "protonmail", "yandex", "gmx", "msn", "bigpond", "qq", "163", "inbox", "list", "bk", "rambler",
    )

    fun parse(input: List<OcrLine>): ScannedContact {
        val lines = input.map { it.copy(text = it.text.trim()) }.filter { it.text.isNotEmpty() }.sortedBy { it.top }
        val raw = lines.joinToString("\n") { it.text }

        // Email: OCR often puts spaces around "@" and ".", so close those gaps first.
        var email = ""
        var emailLine = -1
        for ((i, l) in lines.withIndex()) {
            val compact = if (l.text.contains('@')) l.text.replace(Regex("\\s*([@.])\\s*"), "$1") else l.text
            EMAIL.find(compact)?.let { email = it.value.trimEnd('.').lowercase(); emailLine = i }
            if (email.isNotEmpty()) break
        }
        val emailLocal = email.substringBefore('@')
        val emailDomain = email.substringAfter('@', "")
        val domainLabel = emailDomain.substringBefore('.')
        val companyDomain = domainLabel.takeIf { it.isNotEmpty() && it !in GENERIC_DOMAINS }

        // Phones: prefer mobile, then office; skip fax.
        data class PhoneHit(val number: String, val rank: Int)
        val phones = mutableListOf<PhoneHit>()
        for (l in lines) {
            if (l.text.contains('@')) continue
            for (m in PHONE.findAll(l.text)) {
                val digits = m.value.count { it.isDigit() }
                if (digits < 8 || digits > 15) continue
                val before = l.text.substring(0, m.range.first)
                val rank = when {
                    FAX_LABEL.containsMatchIn(before) -> continue
                    MOBILE_LABEL.containsMatchIn(before) -> 0
                    OFFICE_LABEL.containsMatchIn(before) -> 1
                    else -> 2
                }
                phones += PhoneHit(m.value.trim().replace(Regex("\\s+"), " "), rank)
            }
        }
        val phone = phones.minByOrNull { it.rank }?.number.orEmpty()

        // Website: any domain-looking text that is not part of the email.
        var website = ""
        for (l in lines) {
            val text = EMAIL.replace(l.text.replace(Regex("\\s*@\\s*"), "@"), " ")
            val hit = URL.findAll(text).map { it.value }.firstOrNull { !it.contains('@') }
            if (hit != null) { website = hit.trimEnd('.', ',', '/'); break }
        }

        fun words(s: String) = s.split(Regex("\\s+")).filter { it.isNotEmpty() }
        fun lower(s: String) = s.lowercase().trim(',', ';', ':', '|', '-')
        fun hasJobWord(s: String) = words(s).any { lower(it) in JOB_WORDS }
        fun hasCompanyWord(s: String) = words(s).any { lower(it) in COMPANY_WORDS }
        fun looksLikeContactInfo(s: String) = s.contains('@') || s.any { it.isDigit() } || URL.containsMatchIn(s)

        // Name: a 2 to 4 word line of letters, ideally matching the email address, large and near the top.
        val maxHeight = (lines.maxOfOrNull { it.height } ?: 1).coerceAtLeast(1)
        val localTokens = emailLocal.split('.', '_', '-').map { it.lowercase() }.filter { it.length >= 2 }
        var nameLine = -1
        var bestScore = Double.NEGATIVE_INFINITY
        for ((i, l) in lines.withIndex()) {
            val w = words(l.text).filter { lower(it) !in HONORIFICS }
            if (w.size !in 2..4 || l.text.length > 40) continue
            if (looksLikeContactInfo(l.text) || hasJobWord(l.text) || hasCompanyWord(l.text)) continue
            if (!w.all { NAME_WORD.matches(it) }) continue
            var score = 0.0
            val lw = w.map { it.lowercase().trim('.') }
            if (localTokens.any { t -> lw.any { it == t } }) score += 6
            else if (emailLocal.length >= 3 && lw.any { it.length >= 3 && emailLocal.lowercase().contains(it) }) score += 4
            if (companyDomain != null && lw.joinToString("") == companyDomain.replace("-", "")) score -= 8
            score += 3.0 * l.height / maxHeight
            if (w.all { it.first().isUpperCase() }) score += 1
            score -= i * 0.3
            if (score > bestScore) { bestScore = score; nameLine = i }
        }
        var nameWords = if (nameLine >= 0) words(lines[nameLine].text).filter { lower(it) !in HONORIFICS } else emptyList()
        if (nameWords.isEmpty() && localTokens.size >= 2 && localTokens.all { t -> t.all { it.isLetter() } }) {
            nameWords = localTokens
        }
        nameWords = nameWords.map(::niceCase)

        // Job title: prefer a line right next to the name.
        val titleCandidates = lines.withIndex().filter { (_, l) -> hasJobWord(l.text) && !looksLikeContactInfo(l.text) }
        val title = (titleCandidates.minByOrNull { (i, _) -> if (nameLine >= 0) kotlin.math.abs(i - nameLine) else i })
            ?.value?.text.orEmpty()

        // Company: a line spelling the email domain, then a line with "Ltd", "Group" and so on, then the domain itself.
        val domainMatchLine = companyDomain?.let { d ->
            lines.withIndex().firstOrNull { (i, l) ->
                i != nameLine && l.text.lowercase().replace(Regex("[^a-z0-9]"), "").startsWith(d.replace("-", "")) &&
                    !l.text.contains('@') && !URL.containsMatchIn(l.text)
            }?.value?.text
        }
        val suffixLine = lines.withIndex().firstOrNull { (i, l) ->
            i != nameLine && hasCompanyWord(l.text) && !hasJobWord(l.text) && !l.text.contains('@') && !URL.containsMatchIn(l.text)
        }?.value?.text
        val company = domainMatchLine ?: suffixLine
            ?: companyDomain?.split('-')?.joinToString(" ") { niceCase(it) }.orEmpty()

        return ScannedContact(
            firstName = nameWords.firstOrNull().orEmpty(),
            lastName = nameWords.drop(1).joinToString(" "),
            company = softenCaps(company.trim()),
            jobTitle = softenCaps(title.trim()),
            email = email,
            phone = phone,
            website = website,
            rawText = raw,
        )
    }

    private val SHORT_WORDS = mapOf("LTD" to "Ltd", "INC" to "Inc", "PTY" to "Pty", "CORP" to "Corp", "CO" to "Co", "OF" to "of", "AND" to "and", "THE" to "the")

    /** "ACME MINING LTD" becomes "Acme Mining Ltd"; short acronyms such as "CEO" or "BHP" stay in capitals. */
    private fun softenCaps(s: String): String {
        val letters = s.filter { it.isLetter() }
        if (letters.length < 2 || letters.any { it.isLowerCase() } || !s.contains(' ')) return s
        return s.split(' ').joinToString(" ") { w ->
            val key = w.trimEnd('.', ',')
            SHORT_WORDS[key]?.let { it + w.drop(key.length) }
                ?: if (key.count { it.isLetter() } > 3) w.lowercase().replaceFirstChar { it.titlecase() } else w
        }.replaceFirstChar { it.titlecase() }
    }

    /** "JOHN" or "john" becomes "John"; mixed case such as "McDonald" stays as written. */
    private fun niceCase(w: String): String = when {
        w.length > 1 && w.all { !it.isLetter() || it.isUpperCase() } -> w.lowercase().replaceFirstChar { it.titlecase() }
        w.all { !it.isLetter() || it.isLowerCase() } -> w.replaceFirstChar { it.titlecase() }
        else -> w
    }
}

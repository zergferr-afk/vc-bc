package app.qrcard.export

import app.qrcard.data.ScannedContact
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportFormat(val extension: String, val mime: String, val label: String) {
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Excel"),
    CSV("csv", "text/csv", "CSV"),
}

/** Writes scanned cards as a spreadsheet: one row per card. */
object ContactExport {
    val HEADERS = listOf(
        "First name", "Last name", "Job title", "Company", "Email", "Phone", "Website", "Notes",
        "Scanned on", "Last intro email", "Last intro email on",
    )

    private val dateFormat get() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    fun rows(contacts: List<ScannedContact>): List<List<String>> = contacts.map { c ->
        listOf(
            c.firstName, c.lastName, c.jobTitle, c.company, c.email, c.phone, c.website, c.notes,
            if (c.createdAt > 0) dateFormat.format(Date(c.createdAt)) else "",
            c.lastTemplateName,
            if (c.lastEmailedAt > 0) dateFormat.format(Date(c.lastEmailedAt)) else "",
        )
    }

    fun write(format: ExportFormat, contacts: List<ScannedContact>, out: OutputStream) = when (format) {
        ExportFormat.CSV -> out.write(csv(contacts).toByteArray(Charsets.UTF_8))
        ExportFormat.XLSX -> xlsx(contacts, out)
    }

    /** UTF-8 with a byte order mark, so Excel shows accented names correctly. */
    fun csv(contacts: List<ScannedContact>): String = buildString {
        append('﻿')
        (listOf(HEADERS) + rows(contacts)).forEach { row ->
            append(row.joinToString(",") { csvCell(it) }).append("\r\n")
        }
    }

    private fun csvCell(value: String): String {
        // Stop spreadsheet apps from running text that starts like a formula.
        val v = if (value.startsWith("=") || value.startsWith("@")) "'$value" else value
        return if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }

    /**
     * A minimal Excel workbook. Every cell is stored as text, so phone numbers keep
     * their "+" and leading zeros. The header row is bold and stays visible when scrolling.
     */
    fun xlsx(contacts: List<ScannedContact>, out: OutputStream) {
        val all = listOf(HEADERS) + rows(contacts)
        val sheet = buildString {
            append(XML_HEAD)
            append("""<worksheet xmlns="$MAIN_NS">""")
            append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
            append("<cols>")
            HEADERS.indices.forEach { i ->
                val width = if (HEADERS[i] == "Notes") 40 else 22
                append("""<col min="${i + 1}" max="${i + 1}" width="$width" customWidth="1"/>""")
            }
            append("</cols><sheetData>")
            all.forEachIndexed { r, row ->
                append("""<row r="${r + 1}">""")
                row.forEachIndexed { c, value ->
                    val style = if (r == 0) """ s="1"""" else ""
                    append("""<c r="${column(c)}${r + 1}" t="inlineStr"$style><is><t xml:space="preserve">""")
                    append(xmlEscape(value))
                    append("</t></is></c>")
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }
        ZipOutputStream(out).use { zip ->
            fun put(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", CONTENT_TYPES)
            put("_rels/.rels", ROOT_RELS)
            put("xl/workbook.xml", WORKBOOK)
            put("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            put("xl/styles.xml", STYLES)
            put("xl/worksheets/sheet1.xml", sheet)
        }
    }

    private fun column(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.insert(0, ('A' + rem))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun xmlEscape(s: String): String = buildString {
        for (ch in s) {
            when {
                ch == '&' -> append("&amp;")
                ch == '<' -> append("&lt;")
                ch == '>' -> append("&gt;")
                ch == '"' -> append("&quot;")
                ch == '\n' || ch == '\t' -> append(ch)
                ch == '\r' -> {}
                ch < ' ' -> {} // control characters are not allowed in XML
                else -> append(ch)
            }
        }
    }

    private const val XML_HEAD = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
    private const val MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val PKG_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"

    private const val CONTENT_TYPES = XML_HEAD +
        """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
        """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
        """<Default Extension="xml" ContentType="application/xml"/>""" +
        """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
        """<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" +
        """<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""" +
        "</Types>"

    private const val ROOT_RELS = XML_HEAD +
        """<Relationships xmlns="$PKG_REL_NS">""" +
        """<Relationship Id="rId1" Type="$REL_NS/officeDocument" Target="xl/workbook.xml"/>""" +
        "</Relationships>"

    private const val WORKBOOK = XML_HEAD +
        """<workbook xmlns="$MAIN_NS" xmlns:r="$REL_NS">""" +
        """<sheets><sheet name="Scanned cards" sheetId="1" r:id="rId1"/></sheets>""" +
        "</workbook>"

    private const val WORKBOOK_RELS = XML_HEAD +
        """<Relationships xmlns="$PKG_REL_NS">""" +
        """<Relationship Id="rId1" Type="$REL_NS/worksheet" Target="worksheets/sheet1.xml"/>""" +
        """<Relationship Id="rId2" Type="$REL_NS/styles" Target="styles.xml"/>""" +
        "</Relationships>"

    private const val STYLES = XML_HEAD +
        """<styleSheet xmlns="$MAIN_NS">""" +
        """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>""" +
        """<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>""" +
        """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
        """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
        """<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
        """<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs>""" +
        """<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""" +
        "</styleSheet>"
}

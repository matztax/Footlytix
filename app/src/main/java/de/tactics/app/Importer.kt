package de.tactics.app

import android.content.Context
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class ImportedPlayer(val name: String, val number: Int, val position: String)

data class ImportedMatch(val dateMillis: Long, val opponent: String, val home: Boolean, val summary: String)

/** Einlesen von Spielerlisten (CSV) und Spielplänen (.ics) aus anderen Apps und Verbandsportalen. */
object Importer {

    /** Liest eine Textdatei; erkennt UTF-8 und fällt sonst auf Windows-1252 (typisch für Excel-CSV) zurück. */
    fun readText(ctx: Context, uri: Uri): String {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return ""
        val strict = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try {
            strict.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252"))
        }
        return text.removePrefix("\uFEFF")
    }

    // ------------------------------------------------------------ CSV
    fun parseCsv(text: String): List<List<String>> {
        val first = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return emptyList()
        val delim = listOf(';', ',', '\t').maxByOrNull { d -> first.count { it == d } } ?: ';'
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        cur.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    cur.append(ch)
                }
            } else if (ch == '"') {
                inQuotes = true
            } else if (ch == delim) {
                row.add(cur.toString().trim())
                cur.setLength(0)
            } else if (ch == '\n') {
                row.add(cur.toString().trim())
                cur.setLength(0)
                rows.add(row)
                row = mutableListOf()
            } else if (ch != '\r') {
                cur.append(ch)
            }
            i++
        }
        if (cur.isNotEmpty() || row.isNotEmpty()) {
            row.add(cur.toString().trim())
            rows.add(row)
        }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    private fun List<String>.at(i: Int): String = if (i in indices) this[i] else ""

    private fun toNumber(s: String): Int = s.filter(Char::isDigit).take(2).toIntOrNull() ?: 0

    /** Erkennt Spalten über die Kopfzeile (Name, Vorname/Nachname, Nummer, Position) oder rät die Reihenfolge. */
    fun parsePlayers(rows: List<List<String>>): List<ImportedPlayer> {
        if (rows.isEmpty()) return emptyList()
        val header = rows[0].map { it.lowercase().trim() }
        fun find(vararg keys: String): Int = header.indexOfFirst { h -> keys.any { k -> h == k || h.contains(k) } }
        val iFirst = find("vorname")
        val iLast = find("nachname", "familienname")
        val iName = header.indexOfFirst { it == "name" || it == "spieler" || it == "spielername" }
        val iNum = find("trikot", "nummer", "rückennummer", "rueckennummer", "nr", "#")
        val iPos = find("position", "pos")
        val hasHeader = iFirst >= 0 || iLast >= 0 || iName >= 0 || iNum >= 0 || iPos >= 0

        val out = mutableListOf<ImportedPlayer>()
        if (hasHeader) {
            rows.drop(1).forEach { r ->
                val name = when {
                    iName >= 0 -> r.at(iName)
                    iFirst >= 0 || iLast >= 0 -> (r.at(iFirst) + " " + r.at(iLast)).trim()
                    else -> r.firstOrNull { it.isNotBlank() && !it.all { c -> c.isDigit() } } ?: ""
                }
                if (name.isNotBlank()) out += ImportedPlayer(name, toNumber(r.at(iNum)), r.at(iPos))
            }
        } else {
            rows.forEach { r ->
                val cells = r.filter { it.isNotBlank() }
                val numCell = cells.firstOrNull { it.length <= 2 && it.all { c -> c.isDigit() } }
                val textCells = cells.filter { !it.all { c -> c.isDigit() } }
                val name = textCells.firstOrNull() ?: ""
                val pos = textCells.getOrNull(1) ?: ""
                if (name.isNotBlank()) out += ImportedPlayer(name, toNumber(numCell ?: ""), pos)
            }
        }
        return out
    }

    // ------------------------------------------------------------ Kalender (.ics)
    private fun unescape(s: String): String =
        s.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\").trim()

    private fun parseIcsDate(value: String): Long {
        val v = value.trim()
        return runCatching {
            when {
                v.length == 8 ->
                    (SimpleDateFormat("yyyyMMdd", Locale.US).parse(v)?.time ?: 0L) + 12 * 3600_000L
                v.endsWith("Z") ->
                    SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
                        .apply { timeZone = TimeZone.getTimeZone("UTC") }
                        .parse(v)?.time ?: 0L
                else -> SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).parse(v)?.time ?: 0L
            }
        }.getOrDefault(0L)
    }

    private fun toMatch(start: Long, summary: String, ownName: String): ImportedMatch {
        val clean = summary.removePrefix("Spiel:").removePrefix("Spiel ").trim()
        val own = ownName.lowercase().trim()
        for (sep in listOf(" - ", " – ", " — ", " : ", " vs. ", " vs ")) {
            if (clean.contains(sep)) {
                val a = clean.substringBefore(sep).trim()
                val b = clean.substringAfter(sep).trim()
                return when {
                    own.isNotBlank() && a.lowercase().contains(own) -> ImportedMatch(start, b, true, summary)
                    own.isNotBlank() && b.lowercase().contains(own) -> ImportedMatch(start, a, false, summary)
                    else -> ImportedMatch(start, b, true, summary)
                }
            }
        }
        val opp = clean.removePrefix("gegen ").removePrefix("Gegen ").trim()
        return ImportedMatch(start, opp, true, summary)
    }

    /** Liest alle Termine einer .ics-Datei; [ownName] hilft bei Heim/Auswärts ("A - B"). */
    fun parseIcs(text: String, ownName: String): List<ImportedMatch> {
        val lines = mutableListOf<String>()
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { l ->
            if ((l.startsWith(" ") || l.startsWith("\t")) && lines.isNotEmpty()) {
                lines[lines.size - 1] = lines.last() + l.substring(1)
            } else {
                lines.add(l)
            }
        }
        val out = mutableListOf<ImportedMatch>()
        var inEvent = false
        var start = 0L
        var summary = ""
        for (l in lines) {
            when {
                l.startsWith("BEGIN:VEVENT") -> { inEvent = true; start = 0L; summary = "" }
                l.startsWith("END:VEVENT") -> {
                    if (inEvent && start > 0 && summary.isNotBlank()) out += toMatch(start, summary, ownName)
                    inEvent = false
                }
                inEvent && l.startsWith("DTSTART") -> start = parseIcsDate(l.substringAfter(':'))
                inEvent && l.startsWith("SUMMARY") -> summary = unescape(l.substringAfter(':'))
            }
        }
        return out.sortedBy { it.dateMillis }
    }
}

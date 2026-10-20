package de.tactics.app

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImporterTest {

    @Test
    fun csvWithSemicolonsQuotesAndHeader() {
        val csv = "Vorname;Nachname;Trikotnummer;Position\r\n" +
            "Max;Mustermann;7;Sturm\r\n" +
            "\"Anna\";\"Schmidt; jun.\";10;\"Mittelfeld\"\r\n" +
            "\r\n"
        val rows = Importer.parseCsv(csv)
        assertEquals(3, rows.size)
        assertEquals("Schmidt; jun.", rows[2][1])

        val players = Importer.parsePlayers(rows)
        assertEquals(2, players.size)
        assertEquals(ImportedPlayer("Max Mustermann", 7, "Sturm"), players[0])
        assertEquals(ImportedPlayer("Anna Schmidt; jun.", 10, "Mittelfeld"), players[1])
    }

    @Test
    fun csvWithCommasAndEscapedQuotes() {
        val rows = Importer.parseCsv("Name,Nr\n\"Der \"\"Bomber\"\"\",9\n")
        assertEquals(listOf("Der \"Bomber\"", "9"), rows[1])
        val players = Importer.parsePlayers(rows)
        assertEquals(ImportedPlayer("Der \"Bomber\"", 9, ""), players.single())
    }

    @Test
    fun listWithoutHeaderIsGuessed() {
        val rows = Importer.parseCsv("7;Max Mustermann;Sturm\n10;Anna Schmidt;Abwehr\n")
        val players = Importer.parsePlayers(rows)
        assertEquals(2, players.size)
        assertEquals(ImportedPlayer("Max Mustermann", 7, "Sturm"), players[0])
        assertEquals(ImportedPlayer("Anna Schmidt", 10, "Abwehr"), players[1])
    }

    @Test
    fun emptyInput() {
        assertTrue(Importer.parseCsv("").isEmpty())
        assertTrue(Importer.parsePlayers(emptyList()).isEmpty())
    }

    @Test
    fun icsHomeAndAwayAndFoldedLines() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            DTSTART:20261024T130000Z
            SUMMARY:FC Musterstadt - SV Beispiel
            END:VEVENT
            BEGIN:VEVENT
            DTSTART;VALUE=DATE:20261017
            SUMMARY:TSV Gegnerdorf - FC Muster
             stadt
            END:VEVENT
            BEGIN:VEVENT
            SUMMARY:Ohne Datum - wird ignoriert
            END:VEVENT
            END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n")
        val matches = Importer.parseIcs(ics, "FC Musterstadt")
        assertEquals(2, matches.size)

        // sortiert nach Datum: 17.10. zuerst
        val away = matches[0]
        assertEquals("TSV Gegnerdorf", away.opponent)
        assertFalse(away.home)

        val home = matches[1]
        assertEquals("SV Beispiel", home.opponent)
        assertTrue(home.home)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = home.dateMillis }
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, cal.get(Calendar.MONTH))
        assertEquals(24, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(13, cal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun icsEscapesAreDecoded() {
        val ics = "BEGIN:VEVENT\nDTSTART:20261101T150000\nSUMMARY:Spiel: gegen SV Komma\\, Punkt\nEND:VEVENT\n"
        val m = Importer.parseIcs(ics, "").single()
        assertEquals("SV Komma, Punkt", m.opponent)
        assertTrue(m.home)
    }
}

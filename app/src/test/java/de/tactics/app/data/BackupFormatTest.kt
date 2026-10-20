package de.tactics.app.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Test

/** Das Backup-Format muss stabil bleiben: alte Dateien müssen sich weiter einlesen lassen. */
class BackupFormatTest {

    @Test
    fun roundTrip() {
        val bundle = BackupBundle(
            players = listOf(Player(id = 1, name = "Max", number = 7, position = "Sturm", clubId = 1)),
            clubs = listOf(Club(id = 1, name = "Mein Team", isOwn = true)),
            seasons = listOf(Season(id = 1, name = "Saison 2026/27", isActive = true))
        )
        val json = AppJson.encodeToString(bundle)
        val back = AppJson.decodeFromString<BackupBundle>(json)
        assertEquals(bundle, back)
    }

    @Test
    fun oldBackupWithoutNewFieldsStillLoads() {
        // Backup aus einer frühen Version: keine Vereine, kein version-Feld, unbekanntes Zusatzfeld
        val json = """{"players":[{"id":3,"name":"Alt","number":4,"position":"Abwehr"}],"unbekannt":true}"""
        val b = AppJson.decodeFromString<BackupBundle>(json)
        assertEquals(1, b.players.size)
        assertEquals("Alt", b.players[0].name)
        assertEquals(0, b.clubs.size)
    }
}

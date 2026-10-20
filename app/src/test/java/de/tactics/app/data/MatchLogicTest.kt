package de.tactics.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchLogicTest {
    private val match = Match(
        id = 5, seasonId = 1, dateMillis = 0, ownClubId = 1, opponentClubId = 2, opponentName = "",
        home = true, competition = "Liga", goalsFor = 0, goalsAgainst = 0, played = false, formationId = 0, notes = ""
    )

    private fun ev(id: Long, minute: Int, type: String, player: Long, other: Long = 0) =
        MatchEvent(id = id, matchId = 5, minute = minute, type = type, playerId = player, otherPlayerId = other)

    private fun starter(player: Long) = MatchPlayer(matchId = 5, playerId = player, started = true)

    @Test
    fun goalsCardsAndMinutes() {
        val rows = listOf(starter(1), starter(2), starter(3))
        val events = listOf(
            ev(1, 10, "goal", 1, 2),        // Tor 1, Vorlage 2
            ev(2, 30, "yellow", 3),
            ev(3, 60, "sub", 2, 4),          // 2 raus, 4 rein
            ev(4, 70, "conceded", 0),
            ev(5, 75, "red", 3),             // 3 fliegt
            ev(6, 80, "goal", 4),
            ev(7, 85, "injury", 1),
            // Ereignis eines anderen Spiels darf nicht zählen
            MatchEvent(id = 8, matchId = 99, minute = 5, type = "goal", playerId = 1, otherPlayerId = 0)
        )
        val out = computeMatchOutcome(match, events, rows, endMinute = 90)

        assertTrue(out.match.played)
        assertEquals(2, out.match.goalsFor)
        assertEquals(1, out.match.goalsAgainst)

        val byPlayer = out.rows.associateBy { it.playerId }
        assertEquals(setOf(1L, 2L, 3L, 4L), byPlayer.keys)

        with(byPlayer.getValue(1)) {
            assertEquals(90, minutes); assertEquals(1, goals); assertEquals(0, assists); assertTrue(injured)
        }
        with(byPlayer.getValue(2)) {
            assertEquals(60, minutes); assertEquals(1, assists)
        }
        with(byPlayer.getValue(3)) {
            assertEquals(75, minutes); assertEquals(1, yellow); assertEquals(1, red)
        }
        with(byPlayer.getValue(4)) {
            assertFalse(started); assertEquals(30, minutes); assertEquals(1, goals)
        }
        out.rows.forEach { assertEquals(0L, it.id); assertEquals(5L, it.matchId) }
    }

    @Test
    fun benchPlayerWithoutEventsKeepsManualMinutes() {
        val rows = listOf(MatchPlayer(matchId = 5, playerId = 9, started = false, minutes = 12))
        val out = computeMatchOutcome(match, emptyList(), rows, endMinute = 90)
        assertEquals(12, out.rows.single().minutes)
        assertEquals(0, out.match.goalsFor)
    }

    @Test
    fun earlyEndCapsMinutes() {
        val out = computeMatchOutcome(match, listOf(ev(1, 50, "sub", 1, 2)), listOf(starter(1)), endMinute = 40)
        val byPlayer = out.rows.associateBy { it.playerId }
        assertEquals(40, byPlayer.getValue(1).minutes)
        assertEquals(0, byPlayer.getValue(2).minutes) // eingewechselt nach Spielende
    }

    @Test
    fun playerStatsCountOnlyPlayedMatchesOfSeason() {
        val m1 = match.copy(id = 1, seasonId = 1, played = true)
        val m2 = match.copy(id = 2, seasonId = 2, played = true)
        val m3 = match.copy(id = 3, seasonId = 1, played = false)
        val rows = listOf(
            MatchPlayer(matchId = 1, playerId = 7, started = true, minutes = 90, goals = 2),
            MatchPlayer(matchId = 2, playerId = 7, started = false, minutes = 20, goals = 1),
            MatchPlayer(matchId = 3, playerId = 7, started = true, minutes = 90, goals = 5)
        )
        val season1 = computePlayerStats(7, 1, listOf(m1, m2, m3), rows)
        assertEquals(1, season1.games); assertEquals(90, season1.minutes); assertEquals(2, season1.goals)
        val all = computePlayerStats(7, null, listOf(m1, m2, m3), rows)
        assertEquals(2, all.games); assertEquals(1, all.starts); assertEquals(110, all.minutes); assertEquals(3, all.goals)
    }
}

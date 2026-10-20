package de.tactics.app.data

import androidx.room.withTransaction
import de.tactics.app.ImportedMatch

/** Saisons, Spiele, Einsätze und Spieltag-Ereignisse. */
class MatchRepository(private val db: AppDatabase) {
    private val dao = db.dao()

    /** Es soll immer mindestens einen eigenen Verein und eine Saison geben. */
    suspend fun ensureDefaults() = db.withTransaction {
        if (dao.countOwnClubs() == 0) dao.upsertClub(Club(name = "Mein Team", isOwn = true))
        if (dao.countSeasons() == 0) {
            val cal = java.util.Calendar.getInstance()
            val y = cal.get(java.util.Calendar.YEAR)
            val start = if (cal.get(java.util.Calendar.MONTH) >= java.util.Calendar.JULY) y else y - 1
            dao.upsertSeason(Season(name = "Saison $start/${(start + 1) % 100}", isActive = true))
        }
    }

    suspend fun saveSeason(s: Season) { dao.upsertSeason(s) }

    /** Speichert das Spiel und ersetzt (falls übergeben) die Einsatzzeilen. @return ID des Spiels */
    suspend fun saveMatch(m: Match, rows: List<MatchPlayer>?): Long = db.withTransaction {
        val id = dao.upsertMatch(m)
        val realId = if (m.id > 0) m.id else id
        if (rows != null) {
            dao.deleteMatchPlayersFor(realId)
            dao.upsertMatchPlayers(rows.map { it.copy(id = 0, matchId = realId) })
        }
        realId
    }

    suspend fun deleteMatch(m: Match) = db.withTransaction {
        dao.deleteMatch(m)
        dao.deleteMatchPlayersFor(m.id)
        dao.deleteMatchEventsFor(m.id)
        dao.deleteAnalysesFor("match", m.id)
    }

    suspend fun addMatchEvent(e: MatchEvent) { dao.upsertMatchEvent(e) }
    suspend fun deleteMatchEvent(e: MatchEvent) { dao.deleteMatchEvent(e) }

    /** Legt die Startelf aus der verknüpften Aufstellung an, falls noch keine Einsätze existieren. */
    suspend fun ensureLineup(match: Match) = db.withTransaction {
        val has = dao.getMatchPlayers().any { it.matchId == match.id }
        if (has || match.formationId <= 0) return@withTransaction
        val f = dao.getFormations().firstOrNull { it.id == match.formationId } ?: return@withTransaction
        val ph = decodePhases(f.phasesJson).firstOrNull() ?: return@withTransaction
        val rows = ph.tokens.filter { !it.opponent && it.playerId >= 0 }
            .map { MatchPlayer(matchId = match.id, playerId = it.playerId, started = true) }
        if (rows.isNotEmpty()) dao.upsertMatchPlayers(rows)
    }

    /** Wertet die Ereignisse aus (siehe [computeMatchOutcome]) und speichert Ergebnis und Einsätze. */
    suspend fun finishMatch(match: Match, endMinute: Int) = db.withTransaction {
        val outcome = computeMatchOutcome(match, dao.getMatchEvents(), dao.getMatchPlayers(), endMinute)
        dao.upsertMatch(outcome.match)
        dao.deleteMatchPlayersFor(match.id)
        dao.upsertMatchPlayers(outcome.rows)
    }

    /** Import eines Spielplans; Dubletten (gleicher Tag + Gegner) werden übersprungen. @return (neu, übersprungen) */
    suspend fun importMatches(
        seasonId: Long,
        ownClubId: Long,
        items: List<ImportedMatch>,
        createClubs: Boolean
    ): Pair<Int, Int> = db.withTransaction {
        val clubList = dao.getClubs().toMutableList()
        val seen = dao.getMatches().map { m ->
            val name = clubList.firstOrNull { it.id == m.opponentClubId }?.name ?: m.opponentName
            dayKey(m.dateMillis) + "|" + name.lowercase().trim()
        }.toMutableSet()
        val toAdd = mutableListOf<Match>()
        var skipped = 0
        for (im in items) {
            val key = im.opponent.lowercase().trim()
            if (key.isEmpty()) { skipped++; continue }
            val dup = dayKey(im.dateMillis) + "|" + key
            if (dup in seen) { skipped++; continue }
            seen += dup
            var club = clubList.firstOrNull { !it.isOwn && it.name.lowercase().trim() == key }
            if (club == null && createClubs) {
                val newId = dao.upsertClub(Club(name = im.opponent.trim()))
                val created = Club(id = newId, name = im.opponent.trim())
                clubList += created
                club = created
            }
            toAdd += Match(
                seasonId = seasonId,
                dateMillis = im.dateMillis,
                ownClubId = ownClubId,
                opponentClubId = club?.id ?: 0L,
                opponentName = if (club == null) im.opponent.trim() else "",
                home = im.home
            )
        }
        if (toAdd.isNotEmpty()) dao.upsertMatches(toAdd)
        toAdd.size to skipped
    }

    private fun dayKey(ms: Long): String =
        java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date(ms))
}

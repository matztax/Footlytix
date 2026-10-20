package de.tactics.app.data

/** Ergebnis der Spieltag-Auswertung: aktualisiertes Spiel und die neuen Einsatzzeilen. */
data class MatchOutcome(val match: Match, val rows: List<MatchPlayer>)

/**
 * Rechnet Ergebnis, Minuten, Tore, Vorlagen, Karten und Verletzungen aus den Ereignissen eines Spiels aus.
 * Reine Funktion ohne Datenbankzugriff, damit sie sich mit Unit-Tests prüfen lässt.
 *
 * @param events alle Ereignisse (werden auf [match] gefiltert)
 * @param existingRows alle Einsatzzeilen (werden auf [match] gefiltert)
 * @param endMinute Spielminute, zu der das Spiel beendet wurde
 */
fun computeMatchOutcome(
    match: Match,
    events: List<MatchEvent>,
    existingRows: List<MatchPlayer>,
    endMinute: Int
): MatchOutcome {
    val ev = events.filter { it.matchId == match.id }
        .sortedWith(compareBy<MatchEvent>({ it.minute }, { it.id }))
    val rows = existingRows.filter { it.matchId == match.id }.associateBy { it.playerId }.toMutableMap()
    val inAt = mutableMapOf<Long, Int>()
    val offAt = mutableMapOf<Long, Int>()
    ev.forEach { e ->
        when (e.type) {
            "sub" -> {
                offAt[e.playerId] = e.minute
                if (e.otherPlayerId > 0) inAt[e.otherPlayerId] = e.minute
            }
            "red" -> offAt[e.playerId] = e.minute
        }
        listOf(e.playerId, e.otherPlayerId).filter { it > 0 }.forEach { pid ->
            if (!rows.containsKey(pid)) rows[pid] = MatchPlayer(matchId = match.id, playerId = pid)
        }
    }
    val result = rows.values.map { r ->
        val start: Int? = if (r.started) 0 else inAt[r.playerId]
        val minutes = if (start == null) r.minutes
        else (minOf(offAt[r.playerId] ?: endMinute, endMinute) - start).coerceAtLeast(0)
        r.copy(
            id = 0,
            matchId = match.id,
            minutes = minutes,
            goals = ev.count { it.type == "goal" && it.playerId == r.playerId },
            assists = ev.count { it.type == "goal" && it.otherPlayerId == r.playerId },
            yellow = ev.count { it.type == "yellow" && it.playerId == r.playerId },
            red = ev.count { it.type == "red" && it.playerId == r.playerId },
            injured = r.injured || ev.any { it.type == "injury" && it.playerId == r.playerId }
        )
    }
    val updated = match.copy(
        played = true,
        goalsFor = ev.count { it.type == "goal" },
        goalsAgainst = ev.count { it.type == "conceded" }
    )
    return MatchOutcome(updated, result)
}

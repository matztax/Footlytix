package de.tactics.app.data

import androidx.room.withTransaction
import de.tactics.app.ImportedPlayer

/** Kader und Datenbank: Spieler, Vereine, Transfers, Attribute, Analysen, Verfügbarkeit, Bewertungen. */
class SquadRepository(private val db: AppDatabase) {
    private val dao = db.dao()

    // Spieler
    suspend fun savePlayer(p: Player) { dao.upsertPlayer(p) }

    /** Löscht den Spieler samt allen abhängigen Einträgen – alles oder nichts. */
    suspend fun deletePlayer(p: Player) = db.withTransaction {
        dao.deletePlayer(p)
        dao.deleteLinksFor("player", p.id)
        dao.deleteAnalysesFor("player", p.id)
        dao.deleteTransfersFor(p.id)
        dao.deleteMatchPlayersOfPlayer(p.id)
        dao.deleteAvailabilitiesOfPlayer(p.id)
        dao.deleteAttendancesOfPlayer(p.id)
        dao.deleteEvaluationsOfPlayer(p.id)
        dao.detachMarkersOfPlayer(p.id)
    }

    // Vereine & Transfers
    suspend fun saveClub(c: Club) { dao.upsertClub(c) }

    suspend fun deleteClub(c: Club) = db.withTransaction {
        dao.deleteClub(c)
        dao.detachPlayers(c.id)
        dao.clearOpponentRefs(c.id)
        dao.clearOwnRefs(c.id)
        dao.deleteLinksFor("club", c.id)
        dao.deleteAnalysesFor("club", c.id)
    }

    /** Transfer: nur der Verein wechselt, Attribute, Analysen und Bewertungen bleiben am Spieler. */
    suspend fun transferPlayer(p: Player, toClubId: Long, note: String) = db.withTransaction {
        val date = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY).format(java.util.Date())
        dao.upsertTransfer(Transfer(playerId = p.id, fromClubId = p.clubId, toClubId = toClubId, date = date, note = note))
        dao.setPlayerClub(p.id, toClubId)
    }

    // Attribute & Analysen
    suspend fun saveAttributeDef(a: AttributeDef) { dao.upsertAttributeDef(a) }

    suspend fun deleteAttributeDef(a: AttributeDef) = db.withTransaction {
        dao.deleteAttributeDef(a)
        dao.deleteLinksOfAttribute(a.id)
    }

    suspend fun saveLink(l: AttributeLink) { dao.upsertAttributeLink(l) }
    suspend fun deleteLink(l: AttributeLink) { dao.deleteAttributeLink(l) }

    /** Neues Attribut im Katalog anlegen (falls noch nicht vorhanden) und direkt zuordnen. */
    suspend fun createAttributeAndLink(name: String, positive: Boolean, targetType: String, targetId: Long) =
        db.withTransaction {
            val existing = dao.getAttributeDefs().firstOrNull { it.name.equals(name, ignoreCase = true) && it.positive == positive }
            val defId = existing?.id ?: dao.upsertAttributeDef(AttributeDef(name = name, positive = positive))
            val already = dao.getAttributeLinks().any {
                it.targetType == targetType && it.targetId == targetId && it.attributeId == defId
            }
            if (!already) dao.upsertAttributeLink(AttributeLink(targetType = targetType, targetId = targetId, attributeId = defId))
        }

    suspend fun saveAnalysis(a: Analysis) { dao.upsertAnalysis(a) }
    suspend fun deleteAnalysis(a: Analysis) { dao.deleteAnalysis(a) }

    // Verfügbarkeit & Bewertungen
    suspend fun saveAvailability(a: Availability) { dao.upsertAvailability(a) }
    suspend fun deleteAvailability(a: Availability) { dao.deleteAvailability(a) }
    suspend fun saveEvaluation(e: Evaluation) { dao.upsertEvaluation(e) }
    suspend fun deleteEvaluation(e: Evaluation) { dao.deleteEvaluation(e) }

    /** Import einer Spielerliste; Namen, die es im Verein schon gibt, werden übersprungen. @return (neu, übersprungen) */
    suspend fun importPlayers(clubId: Long, items: List<ImportedPlayer>): Pair<Int, Int> = db.withTransaction {
        val existing = dao.getPlayers().filter { it.clubId == clubId }.map { it.name.lowercase().trim() }.toMutableSet()
        val toAdd = mutableListOf<Player>()
        var skipped = 0
        items.forEach { ip ->
            val key = ip.name.lowercase().trim()
            if (key in existing) {
                skipped++
            } else {
                existing += key
                toAdd += Player(name = ip.name.trim(), number = ip.number, position = ip.position.trim(), clubId = clubId)
            }
        }
        if (toAdd.isNotEmpty()) dao.upsertPlayers(toAdd)
        toAdd.size to skipped
    }
}

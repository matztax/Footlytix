package de.tactics.app.data

import androidx.room.withTransaction

/** Aufstellungen, Standards und Briefings. */
class TacticsRepository(private val db: AppDatabase) {
    private val dao = db.dao()

    suspend fun createFormation(
        name: String,
        ownSystem: String,
        opponentSystem: String,
        ownClubId: Long,
        opponentClubId: Long
    ): Long {
        val tokens = buildTokens(ownSystem, false) + buildTokens(opponentSystem, true)
        val phases = listOf(Phase("Phase 1", tokens))
        return dao.upsertFormation(
            Formation(
                name = name,
                phasesJson = encodePhases(phases),
                ownClubId = ownClubId,
                opponentClubId = opponentClubId
            )
        )
    }

    suspend fun saveFormation(f: Formation) { dao.upsertFormation(f) }

    suspend fun deleteFormation(f: Formation) = db.withTransaction {
        dao.deleteFormation(f)
        dao.clearMatchFormation(f.id)
    }

    suspend fun saveSetPiece(s: SetPiece) { dao.upsertSetPiece(s) }
    suspend fun deleteSetPiece(s: SetPiece) { dao.deleteSetPiece(s) }
    suspend fun saveBriefing(b: Briefing) { dao.upsertBriefing(b) }
    suspend fun deleteBriefing(b: Briefing) { dao.deleteBriefing(b) }
}

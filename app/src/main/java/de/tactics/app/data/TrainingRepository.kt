package de.tactics.app.data

import androidx.room.withTransaction

/** Übungen, Trainingseinheiten und Anwesenheit. */
class TrainingRepository(private val db: AppDatabase) {
    private val dao = db.dao()

    suspend fun saveExercise(e: Exercise) { dao.upsertExercise(e) }
    suspend fun deleteExercise(e: Exercise) { dao.deleteExercise(e) }
    suspend fun saveSession(s: TrainingSession) { dao.upsertSession(s) }

    suspend fun deleteSession(s: TrainingSession) = db.withTransaction {
        dao.deleteSession(s)
        dao.deleteAttendancesOfSession(s.id)
    }

    /** status = "" entfernt den Eintrag. */
    suspend fun setAttendance(sessionId: Long, playerId: Long, status: String) = db.withTransaction {
        val existing = dao.getAttendances().firstOrNull { it.sessionId == sessionId && it.playerId == playerId }
        if (status.isEmpty()) {
            if (existing != null) dao.deleteAttendance(existing)
        } else {
            dao.upsertAttendance(Attendance(id = existing?.id ?: 0L, sessionId = sessionId, playerId = playerId, status = status))
        }
    }

    suspend fun setAttendanceForAll(sessionId: Long, playerIds: List<Long>, status: String) = db.withTransaction {
        val all = dao.getAttendances().filter { it.sessionId == sessionId }
        val rows = playerIds.map { pid ->
            val existing = all.firstOrNull { it.playerId == pid }
            Attendance(id = existing?.id ?: 0L, sessionId = sessionId, playerId = pid, status = status)
        }
        dao.upsertAttendances(rows)
    }
}

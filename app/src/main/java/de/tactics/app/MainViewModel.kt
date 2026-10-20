package de.tactics.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.tactics.app.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Zentrale Schnittstelle der Oberfläche. Hält die beobachteten Daten (StateFlows) und reicht
 * Änderungen an die fachlichen Repositories weiter; mehrteilige Änderungen laufen dort in Transaktionen.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.dao()

    private val squad = SquadRepository(db)
    private val matchRepo = MatchRepository(db)
    private val training = TrainingRepository(db)
    private val tactics = TacticsRepository(db)
    private val video = VideoRepository(db)
    private val backup = BackupRepository(app, db)

    private fun io(block: suspend () -> Unit) { viewModelScope.launch { block() } }

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val players = dao.observePlayers().state(emptyList())
    val formations = dao.observeFormations().state(emptyList())
    val setPieces = dao.observeSetPieces().state(emptyList())
    val annotations = dao.observeAnnotations().state(emptyList())
    val briefings = dao.observeBriefings().state(emptyList())
    val exercises = dao.observeExercises().state(emptyList())
    val sessions = dao.observeSessions().state(emptyList())
    val videos = dao.observeVideos().state(emptyList())
    val clubs = dao.observeClubs().state(emptyList())
    val transfers = dao.observeTransfers().state(emptyList())
    val attributeDefs = dao.observeAttributeDefs().state(emptyList())
    val attributeLinks = dao.observeAttributeLinks().state(emptyList())
    val analyses = dao.observeAnalyses().state(emptyList())
    val seasons = dao.observeSeasons().state(emptyList())
    val matches = dao.observeMatches().state(emptyList())
    val matchPlayers = dao.observeMatchPlayers().state(emptyList())
    val availabilities = dao.observeAvailabilities().state(emptyList())
    val attendances = dao.observeAttendances().state(emptyList())
    val evaluations = dao.observeEvaluations().state(emptyList())
    val markers = dao.observeMarkers().state(emptyList())
    val matchEvents = dao.observeMatchEvents().state(emptyList())

    /** Spieler der eigenen Vereine (Kader für Aufstellung und Standards). */
    val ownPlayers = combine(players, clubs) { p, c ->
        val own = c.filter { it.isOwn }.map { it.id }.toSet()
        p.filter { it.clubId in own }
    }.state(emptyList())

    init {
        viewModelScope.launch {
            matchRepo.ensureDefaults()
            // Tägliche automatische Sicherung
            delay(3000)
            backup.runAutoBackupIfDue()
        }
    }

    // ------------------------------------------------------------ Backup
    /** Sichert sofort (intern und, falls gewählt, in den Backup-Ordner). */
    suspend fun backupNow(): java.io.File? = backup.backupNow()

    /** Unverschlüsselter Gesamtstand (z. B. für interne Zwecke). */
    suspend fun exportJson(): String = backup.exportJson()

    /** Gesamtstand für eine Datei außerhalb der App; mit Backup-Passwort verschlüsselt. */
    suspend fun exportForSharing(): String = backup.exportForSharing()

    /**
     * Ersetzt alle Daten durch das Backup; vorher wird eine Sicherheitskopie angelegt.
     * Wirft [BackupCrypto.PasswordRequiredException] / [BackupCrypto.WrongPasswordException],
     * wenn ein (anderes) Passwort nötig ist, und [BackupRepository.InvalidBackupException] bei ungültigen Dateien.
     */
    suspend fun restoreBackup(text: String, password: String? = null): java.io.File? = backup.restore(text, password)

    // Kader
    fun savePlayer(p: Player) = io { squad.savePlayer(p) }
    fun deletePlayer(p: Player) = io { squad.deletePlayer(p) }

    // Vereine & Transfers
    fun saveClub(c: Club) = io { squad.saveClub(c) }
    fun deleteClub(c: Club) = io { squad.deleteClub(c) }

    /** Transfer: nur der Verein wechselt, Attribute, Analysen und Bewertungen bleiben am Spieler. */
    fun transferPlayer(p: Player, toClubId: Long, note: String) = io { squad.transferPlayer(p, toClubId, note) }

    // Attribute & Analysen
    fun saveAttributeDef(a: AttributeDef) = io { squad.saveAttributeDef(a) }
    fun deleteAttributeDef(a: AttributeDef) = io { squad.deleteAttributeDef(a) }
    fun saveLink(l: AttributeLink) = io { squad.saveLink(l) }
    fun deleteLink(l: AttributeLink) = io { squad.deleteLink(l) }

    /** Neues Attribut im Katalog anlegen (falls noch nicht vorhanden) und direkt zuordnen. */
    fun createAttributeAndLink(name: String, positive: Boolean, targetType: String, targetId: Long) =
        io { squad.createAttributeAndLink(name, positive, targetType, targetId) }

    fun saveAnalysis(a: Analysis) = io { squad.saveAnalysis(a) }
    fun deleteAnalysis(a: Analysis) = io { squad.deleteAnalysis(a) }

    // Aufstellungen
    fun createFormation(
        name: String,
        ownSystem: String,
        opponentSystem: String,
        ownClubId: Long,
        opponentClubId: Long,
        onCreated: (Long) -> Unit
    ) = io { onCreated(tactics.createFormation(name, ownSystem, opponentSystem, ownClubId, opponentClubId)) }

    fun saveFormation(f: Formation) = io { tactics.saveFormation(f) }
    fun deleteFormation(f: Formation) = io { tactics.deleteFormation(f) }

    // Standards
    fun saveSetPiece(s: SetPiece) = io { tactics.saveSetPiece(s) }
    fun deleteSetPiece(s: SetPiece) = io { tactics.deleteSetPiece(s) }

    // Video-Zeichnungen
    fun saveAnnotation(a: VideoAnnotation) = io { video.saveAnnotation(a) }
    fun deleteAnnotation(a: VideoAnnotation) = io { video.deleteAnnotation(a) }

    // Briefings
    fun saveBriefing(b: Briefing) = io { tactics.saveBriefing(b) }
    fun deleteBriefing(b: Briefing) = io { tactics.deleteBriefing(b) }

    // Video-Bibliothek
    fun saveVideo(v: VideoItem) = io { video.saveVideo(v) }
    fun deleteVideo(v: VideoItem) = io { video.deleteVideo(v) }

    /** Video mit einer neu ausgewählten Datei verknüpfen (Zeichnungen und Marker bleiben erhalten). */
    fun relinkVideo(v: VideoItem, newUri: String) = io { video.relinkVideo(v, newUri) }

    // Übungen & Trainingseinheiten
    fun saveExercise(e: Exercise) = io { training.saveExercise(e) }
    fun deleteExercise(e: Exercise) = io { training.deleteExercise(e) }
    fun saveSession(s: TrainingSession) = io { training.saveSession(s) }
    fun deleteSession(s: TrainingSession) = io { training.deleteSession(s) }

    // Saisons & Spiele
    fun saveSeason(s: Season) = io { matchRepo.saveSeason(s) }

    fun saveMatch(m: Match, rows: List<MatchPlayer>? = null, onSaved: (Long) -> Unit = {}) =
        io { onSaved(matchRepo.saveMatch(m, rows)) }

    fun deleteMatch(m: Match) = io { matchRepo.deleteMatch(m) }

    // Verfügbarkeit
    fun saveAvailability(a: Availability) = io { squad.saveAvailability(a) }
    fun deleteAvailability(a: Availability) = io { squad.deleteAvailability(a) }

    // Anwesenheit: status = "" entfernt den Eintrag
    fun setAttendance(sessionId: Long, playerId: Long, status: String) =
        io { training.setAttendance(sessionId, playerId, status) }

    fun setAttendanceForAll(sessionId: Long, playerIds: List<Long>, status: String) =
        io { training.setAttendanceForAll(sessionId, playerIds, status) }

    // Bewertungsverlauf
    fun saveEvaluation(e: Evaluation) = io { squad.saveEvaluation(e) }
    fun deleteEvaluation(e: Evaluation) = io { squad.deleteEvaluation(e) }

    // Video-Marker
    fun saveMarker(m: VideoMarker) = io { video.saveMarker(m) }
    fun deleteMarker(m: VideoMarker) = io { video.deleteMarker(m) }

    // ------------------------------------------------------------ Live-Modus (Spieltag)
    var liveMatchId by mutableLongStateOf(0L)
        private set
    var liveRunning by mutableStateOf(false)
        private set
    private var liveBaseMs = 0L
    private var liveStartedAt = 0L

    fun liveElapsedMs(): Long = liveBaseMs + (if (liveRunning) System.currentTimeMillis() - liveStartedAt else 0L)

    fun liveEnter(matchId: Long) {
        if (liveMatchId != matchId) {
            liveMatchId = matchId
            liveRunning = false
            liveBaseMs = 0L
        }
    }

    fun liveToggle() {
        if (liveRunning) {
            liveBaseMs = liveElapsedMs()
            liveRunning = false
        } else {
            liveStartedAt = System.currentTimeMillis()
            liveRunning = true
        }
    }

    fun liveAdjustMinutes(delta: Int) {
        liveBaseMs = (liveElapsedMs() + delta * 60_000L).coerceAtLeast(0L)
        if (liveRunning) liveStartedAt = System.currentTimeMillis()
    }

    fun addMatchEvent(e: MatchEvent) = io { matchRepo.addMatchEvent(e) }
    fun deleteMatchEvent(e: MatchEvent) = io { matchRepo.deleteMatchEvent(e) }

    /** Legt die Startelf aus der verknüpften Aufstellung an, falls noch keine Einsätze existieren. */
    fun ensureLineup(match: Match) = io { matchRepo.ensureLineup(match) }

    /** Rechnet Ergebnis, Minuten, Tore, Vorlagen, Karten und Verletzungen aus den Ereignissen aus. */
    fun finishMatch(match: Match, endMinute: Int, onDone: () -> Unit) = io {
        matchRepo.finishMatch(match, endMinute)
        liveRunning = false
        onDone()
    }

    // ------------------------------------------------------------ Import
    fun importPlayers(clubId: Long, items: List<ImportedPlayer>, onDone: (Int, Int) -> Unit) = io {
        val (added, skipped) = squad.importPlayers(clubId, items)
        onDone(added, skipped)
    }

    fun importMatches(
        seasonId: Long,
        ownClubId: Long,
        items: List<ImportedMatch>,
        createClubs: Boolean,
        onDone: (Int, Int) -> Unit
    ) = io {
        val (added, skipped) = matchRepo.importMatches(seasonId, ownClubId, items, createClubs)
        onDone(added, skipped)
    }
}

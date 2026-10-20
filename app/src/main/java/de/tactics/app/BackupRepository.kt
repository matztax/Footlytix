package de.tactics.app

import android.content.Context
import androidx.room.withTransaction
import de.tactics.app.data.AppDatabase
import de.tactics.app.data.AppJson
import de.tactics.app.data.BackupBundle
import de.tactics.app.data.Club
import de.tactics.app.data.Season
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** Export, Import und automatische Sicherung aller Daten. */
class BackupRepository(context: Context, private val db: AppDatabase) {
    private val app = context.applicationContext
    private val dao = db.dao()
    private val prefs get() = AppPrefs(app)

    class InvalidBackupException(message: String) : Exception(message)

    /** Vollständiger Stand als JSON (unverschlüsselt). Läuft in einer Transaktion, damit der Stand in sich stimmig ist. */
    suspend fun exportJson(): String {
        val bundle = db.withTransaction {
            BackupBundle(
                version = AppDatabase.VERSION,
                players = dao.getPlayers(),
                formations = dao.getFormations(),
                setPieces = dao.getSetPieces(),
                annotations = dao.getAnnotations(),
                briefings = dao.getBriefings(),
                exercises = dao.getExercises(),
                sessions = dao.getSessions(),
                videos = dao.getVideos(),
                clubs = dao.getClubs(),
                transfers = dao.getTransfers(),
                attributeDefs = dao.getAttributeDefs(),
                attributeLinks = dao.getAttributeLinks(),
                analyses = dao.getAnalyses(),
                seasons = dao.getSeasons(),
                matches = dao.getMatches(),
                matchPlayers = dao.getMatchPlayers(),
                availabilities = dao.getAvailabilities(),
                attendances = dao.getAttendances(),
                evaluations = dao.getEvaluations(),
                markers = dao.getMarkers(),
                matchEvents = dao.getMatchEvents()
            )
        }
        return withContext(Dispatchers.Default) { AppJson.encodeToString(bundle) }
    }

    /** Stand für eine Datei, die das Gerät verlässt: verschlüsselt, wenn ein Backup-Passwort gesetzt ist. */
    suspend fun exportForSharing(): String {
        val json = exportJson()
        val password = prefs.backupPassword ?: return json
        return withContext(Dispatchers.Default) { BackupCrypto.encrypt(json, password) }
    }

    /**
     * Prüft und entschlüsselt eine Backup-Datei, ohne etwas zu verändern.
     * @param password ausdrücklich eingegebenes Passwort; ohne wird das gespeicherte Backup-Passwort versucht
     * @throws BackupCrypto.PasswordRequiredException / BackupCrypto.WrongPasswordException
     * @throws InvalidBackupException wenn die Datei kein (passendes) Futlytix-Backup ist
     */
    suspend fun parse(text: String, password: String? = null): BackupBundle = withContext(Dispatchers.Default) {
        val json = if (BackupCrypto.isEncrypted(text)) BackupCrypto.decrypt(text, password ?: prefs.backupPassword) else text
        val bundle = try {
            AppJson.decodeFromString<BackupBundle>(json)
        } catch (e: Exception) {
            throw InvalidBackupException("Die Datei ist kein gültiges Futlytix-Backup.")
        }
        if (bundle.version > AppDatabase.VERSION) {
            throw InvalidBackupException("Das Backup stammt aus einer neueren App-Version. Bitte Futlytix zuerst aktualisieren.")
        }
        val empty = with(bundle) {
            players.isEmpty() && clubs.isEmpty() && seasons.isEmpty() && matches.isEmpty() &&
                formations.isEmpty() && exercises.isEmpty() && sessions.isEmpty() && briefings.isEmpty() &&
                videos.isEmpty() && setPieces.isEmpty()
        }
        if (empty) throw InvalidBackupException("Die Datei enthält keine Futlytix-Daten.")
        bundle
    }

    /**
     * Ersetzt alle Daten durch das Backup. Vorher wird der aktuelle Stand als Sicherheitskopie abgelegt,
     * damit sich eine versehentliche Wiederherstellung rückgängig machen lässt.
     * @return die Sicherheitskopie (oder null, wenn sie nicht geschrieben werden konnte)
     */
    suspend fun restore(text: String, password: String? = null): File? {
        val bundle = parse(text, password)
        val safety = runCatching {
            val current = exportJson()
            withContext(Dispatchers.IO) { AutoBackup.writeSafetyCopy(app, current) }
        }.getOrNull()
        replaceAll(bundle)
        return safety
    }

    private suspend fun replaceAll(b: BackupBundle) {
        db.withTransaction {
            dao.clearPlayers(); dao.clearFormations(); dao.clearSetPieces()
            dao.clearAnnotations(); dao.clearBriefings()
            dao.clearExercises(); dao.clearSessions(); dao.clearVideos()
            dao.clearClubs(); dao.clearTransfers(); dao.clearAttributeDefs()
            dao.clearAttributeLinks(); dao.clearAnalyses()
            dao.clearSeasons(); dao.clearMatches(); dao.clearMatchPlayers(); dao.clearAvailabilities()
            dao.clearAttendances(); dao.clearEvaluations(); dao.clearMarkers(); dao.clearMatchEvents()
            dao.upsertPlayers(b.players)
            dao.upsertFormations(b.formations)
            dao.upsertSetPieces(b.setPieces)
            dao.upsertAnnotations(b.annotations)
            dao.upsertBriefings(b.briefings)
            dao.upsertExercises(b.exercises)
            dao.upsertSessions(b.sessions)
            dao.upsertVideos(b.videos)
            dao.upsertClubs(b.clubs)
            dao.upsertTransfers(b.transfers)
            dao.upsertAttributeDefs(b.attributeDefs)
            dao.upsertAttributeLinks(b.attributeLinks)
            dao.upsertAnalyses(b.analyses)
            dao.upsertSeasons(b.seasons)
            dao.upsertMatches(b.matches)
            dao.upsertMatchPlayers(b.matchPlayers)
            dao.upsertAvailabilities(b.availabilities)
            dao.upsertAttendances(b.attendances)
            dao.upsertEvaluations(b.evaluations)
            dao.upsertMarkers(b.markers)
            dao.upsertMatchEvents(b.matchEvents)
            if (dao.countSeasons() == 0) dao.upsertSeason(Season(name = "Saison", isActive = true))
            if (dao.countOwnClubs() == 0) {
                val ownId = dao.upsertClub(Club(name = "Mein Team", isOwn = true))
                // Ältere Backups kennen keine Vereine: alle Spieler gehören dann zum eigenen Team
                if (b.clubs.isEmpty()) dao.assignFreePlayers(ownId)
            }
        }
    }

    /** Sichert sofort (intern und, falls gewählt, in den Backup-Ordner). */
    suspend fun backupNow(): File? = runCatching {
        val json = exportJson()
        val file = withContext(Dispatchers.IO) { AutoBackup.write(app, json) }
        prefs.lastBackupDay = AutoBackup.today()
        prefs.lastBackupTime = System.currentTimeMillis()
        file
    }.getOrNull()

    suspend fun runAutoBackupIfDue() {
        val p = prefs
        if (p.autoBackup && p.lastBackupDay != AutoBackup.today()) backupNow()
    }
}

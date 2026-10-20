package de.tactics.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface TacticsDao {
    // Spieler
    @Query("SELECT * FROM players ORDER BY number") fun observePlayers(): Flow<List<Player>>
    @Query("SELECT * FROM players") suspend fun getPlayers(): List<Player>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPlayer(p: Player): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPlayers(p: List<Player>)
    @Delete suspend fun deletePlayer(p: Player)
    @Query("DELETE FROM players") suspend fun clearPlayers()

    // Aufstellungen
    @Query("SELECT * FROM formations ORDER BY id") fun observeFormations(): Flow<List<Formation>>
    @Query("SELECT * FROM formations") suspend fun getFormations(): List<Formation>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertFormation(f: Formation): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertFormations(f: List<Formation>)
    @Delete suspend fun deleteFormation(f: Formation)
    @Query("DELETE FROM formations") suspend fun clearFormations()

    // Standards
    @Query("SELECT * FROM set_pieces ORDER BY id") fun observeSetPieces(): Flow<List<SetPiece>>
    @Query("SELECT * FROM set_pieces") suspend fun getSetPieces(): List<SetPiece>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSetPiece(s: SetPiece): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSetPieces(s: List<SetPiece>)
    @Delete suspend fun deleteSetPiece(s: SetPiece)
    @Query("DELETE FROM set_pieces") suspend fun clearSetPieces()

    // Video-Zeichnungen
    @Query("SELECT * FROM video_annotations ORDER BY timestampMs") fun observeAnnotations(): Flow<List<VideoAnnotation>>
    @Query("SELECT * FROM video_annotations") suspend fun getAnnotations(): List<VideoAnnotation>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAnnotation(a: VideoAnnotation): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAnnotations(a: List<VideoAnnotation>)
    @Delete suspend fun deleteAnnotation(a: VideoAnnotation)
    @Query("DELETE FROM video_annotations") suspend fun clearAnnotations()

    // Video-Bibliothek
    @Query("SELECT * FROM video_items ORDER BY createdAt DESC") fun observeVideos(): Flow<List<VideoItem>>
    @Query("SELECT * FROM video_items") suspend fun getVideos(): List<VideoItem>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertVideo(v: VideoItem): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertVideos(v: List<VideoItem>)
    @Delete suspend fun deleteVideo(v: VideoItem)
    @Query("DELETE FROM video_items") suspend fun clearVideos()
    @Query("SELECT COUNT(*) FROM video_items WHERE uri = :uri") suspend fun countVideosWithUri(uri: String): Int
    @Query("DELETE FROM video_annotations WHERE videoUri = :uri") suspend fun deleteAnnotationsForUri(uri: String)
    @Query("UPDATE video_items SET uri = :newUri WHERE uri = :oldUri") suspend fun moveVideoUri(oldUri: String, newUri: String)
    @Query("UPDATE video_annotations SET videoUri = :newUri WHERE videoUri = :oldUri") suspend fun moveAnnotationUri(oldUri: String, newUri: String)

    // Vereine
    @Query("SELECT * FROM clubs ORDER BY isOwn DESC, name COLLATE NOCASE") fun observeClubs(): Flow<List<Club>>
    @Query("SELECT * FROM clubs") suspend fun getClubs(): List<Club>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertClub(c: Club): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertClubs(c: List<Club>)
    @Delete suspend fun deleteClub(c: Club)
    @Query("DELETE FROM clubs") suspend fun clearClubs()
    @Query("SELECT COUNT(*) FROM clubs WHERE isOwn = 1") suspend fun countOwnClubs(): Int
    @Query("UPDATE players SET clubId = :clubId WHERE id = :id") suspend fun setPlayerClub(id: Long, clubId: Long)
    @Query("UPDATE players SET clubId = :clubId WHERE clubId = 0") suspend fun assignFreePlayers(clubId: Long)
    @Query("UPDATE players SET clubId = 0 WHERE clubId = :clubId") suspend fun detachPlayers(clubId: Long)
    @Query("UPDATE formations SET opponentClubId = 0 WHERE opponentClubId = :clubId") suspend fun clearOpponentRefs(clubId: Long)
    @Query("UPDATE formations SET ownClubId = 0 WHERE ownClubId = :clubId") suspend fun clearOwnRefs(clubId: Long)

    // Transfers
    @Query("SELECT * FROM transfers ORDER BY id DESC") fun observeTransfers(): Flow<List<Transfer>>
    @Query("SELECT * FROM transfers") suspend fun getTransfers(): List<Transfer>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTransfer(t: Transfer): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTransfers(t: List<Transfer>)
    @Query("DELETE FROM transfers") suspend fun clearTransfers()
    @Query("DELETE FROM transfers WHERE playerId = :playerId") suspend fun deleteTransfersFor(playerId: Long)

    // Attribute (Katalog)
    @Query("SELECT * FROM attribute_defs ORDER BY name COLLATE NOCASE") fun observeAttributeDefs(): Flow<List<AttributeDef>>
    @Query("SELECT * FROM attribute_defs") suspend fun getAttributeDefs(): List<AttributeDef>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAttributeDef(a: AttributeDef): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAttributeDefs(a: List<AttributeDef>)
    @Delete suspend fun deleteAttributeDef(a: AttributeDef)
    @Query("DELETE FROM attribute_defs") suspend fun clearAttributeDefs()

    // Attribut-Zuordnungen
    @Query("SELECT * FROM attribute_links") fun observeAttributeLinks(): Flow<List<AttributeLink>>
    @Query("SELECT * FROM attribute_links") suspend fun getAttributeLinks(): List<AttributeLink>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAttributeLink(l: AttributeLink): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAttributeLinks(l: List<AttributeLink>)
    @Delete suspend fun deleteAttributeLink(l: AttributeLink)
    @Query("DELETE FROM attribute_links") suspend fun clearAttributeLinks()
    @Query("DELETE FROM attribute_links WHERE targetType = :type AND targetId = :id") suspend fun deleteLinksFor(type: String, id: Long)
    @Query("DELETE FROM attribute_links WHERE attributeId = :attributeId") suspend fun deleteLinksOfAttribute(attributeId: Long)

    // Analysen
    @Query("SELECT * FROM analyses ORDER BY createdAt DESC") fun observeAnalyses(): Flow<List<Analysis>>
    @Query("SELECT * FROM analyses") suspend fun getAnalyses(): List<Analysis>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAnalysis(a: Analysis): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAnalyses(a: List<Analysis>)
    @Delete suspend fun deleteAnalysis(a: Analysis)
    @Query("DELETE FROM analyses") suspend fun clearAnalyses()
    @Query("DELETE FROM analyses WHERE targetType = :type AND targetId = :id") suspend fun deleteAnalysesFor(type: String, id: Long)


    // Saisons
    @Query("SELECT * FROM seasons ORDER BY id DESC") fun observeSeasons(): Flow<List<Season>>
    @Query("SELECT * FROM seasons") suspend fun getSeasons(): List<Season>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSeason(s: Season): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSeasons(s: List<Season>)
    @Query("DELETE FROM seasons") suspend fun clearSeasons()
    @Query("SELECT COUNT(*) FROM seasons") suspend fun countSeasons(): Int

    // Spiele
    @Query("SELECT * FROM matches ORDER BY dateMillis DESC") fun observeMatches(): Flow<List<Match>>
    @Query("SELECT * FROM matches") suspend fun getMatches(): List<Match>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMatch(m: Match): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMatches(m: List<Match>)
    @Delete suspend fun deleteMatch(m: Match)
    @Query("DELETE FROM matches") suspend fun clearMatches()

    @Query("SELECT * FROM match_players") fun observeMatchPlayers(): Flow<List<MatchPlayer>>
    @Query("SELECT * FROM match_players") suspend fun getMatchPlayers(): List<MatchPlayer>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMatchPlayers(m: List<MatchPlayer>)
    @Query("DELETE FROM match_players") suspend fun clearMatchPlayers()
    @Query("DELETE FROM match_players WHERE matchId = :matchId") suspend fun deleteMatchPlayersFor(matchId: Long)
    @Query("DELETE FROM match_players WHERE playerId = :playerId") suspend fun deleteMatchPlayersOfPlayer(playerId: Long)

    // Spielereignisse
    @Query("SELECT * FROM match_events ORDER BY minute, id") fun observeMatchEvents(): Flow<List<MatchEvent>>
    @Query("SELECT * FROM match_events") suspend fun getMatchEvents(): List<MatchEvent>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMatchEvent(e: MatchEvent): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMatchEvents(e: List<MatchEvent>)
    @Delete suspend fun deleteMatchEvent(e: MatchEvent)
    @Query("DELETE FROM match_events") suspend fun clearMatchEvents()
    @Query("DELETE FROM match_events WHERE matchId = :matchId") suspend fun deleteMatchEventsFor(matchId: Long)
    @Query("UPDATE matches SET formationId = 0 WHERE formationId = :formationId") suspend fun clearMatchFormation(formationId: Long)

    // Verfügbarkeit
    @Query("SELECT * FROM availabilities") fun observeAvailabilities(): Flow<List<Availability>>
    @Query("SELECT * FROM availabilities") suspend fun getAvailabilities(): List<Availability>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAvailability(a: Availability): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAvailabilities(a: List<Availability>)
    @Delete suspend fun deleteAvailability(a: Availability)
    @Query("DELETE FROM availabilities") suspend fun clearAvailabilities()
    @Query("DELETE FROM availabilities WHERE playerId = :playerId") suspend fun deleteAvailabilitiesOfPlayer(playerId: Long)

    // Anwesenheit
    @Query("SELECT * FROM attendances") fun observeAttendances(): Flow<List<Attendance>>
    @Query("SELECT * FROM attendances") suspend fun getAttendances(): List<Attendance>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAttendance(a: Attendance): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAttendances(a: List<Attendance>)
    @Delete suspend fun deleteAttendance(a: Attendance)
    @Query("DELETE FROM attendances") suspend fun clearAttendances()
    @Query("DELETE FROM attendances WHERE sessionId = :sessionId") suspend fun deleteAttendancesOfSession(sessionId: Long)
    @Query("DELETE FROM attendances WHERE playerId = :playerId") suspend fun deleteAttendancesOfPlayer(playerId: Long)

    // Bewertungsverlauf
    @Query("SELECT * FROM evaluations ORDER BY dateMillis") fun observeEvaluations(): Flow<List<Evaluation>>
    @Query("SELECT * FROM evaluations") suspend fun getEvaluations(): List<Evaluation>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertEvaluation(e: Evaluation): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertEvaluations(e: List<Evaluation>)
    @Delete suspend fun deleteEvaluation(e: Evaluation)
    @Query("DELETE FROM evaluations") suspend fun clearEvaluations()
    @Query("DELETE FROM evaluations WHERE playerId = :playerId") suspend fun deleteEvaluationsOfPlayer(playerId: Long)

    // Video-Marker
    @Query("SELECT * FROM video_markers ORDER BY timestampMs") fun observeMarkers(): Flow<List<VideoMarker>>
    @Query("SELECT * FROM video_markers") suspend fun getMarkers(): List<VideoMarker>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMarker(m: VideoMarker): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMarkers(m: List<VideoMarker>)
    @Delete suspend fun deleteMarker(m: VideoMarker)
    @Query("DELETE FROM video_markers") suspend fun clearMarkers()
    @Query("DELETE FROM video_markers WHERE videoId = :videoId") suspend fun deleteMarkersOfVideo(videoId: Long)
    @Query("UPDATE video_markers SET playerId = 0 WHERE playerId = :playerId") suspend fun detachMarkersOfPlayer(playerId: Long)

    // Briefings
    @Query("SELECT * FROM briefings ORDER BY createdAt DESC") fun observeBriefings(): Flow<List<Briefing>>
    @Query("SELECT * FROM briefings") suspend fun getBriefings(): List<Briefing>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBriefing(b: Briefing): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBriefings(b: List<Briefing>)
    @Delete suspend fun deleteBriefing(b: Briefing)
    @Query("DELETE FROM briefings") suspend fun clearBriefings()

    // Übungen
    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE") fun observeExercises(): Flow<List<Exercise>>
    @Query("SELECT * FROM exercises") suspend fun getExercises(): List<Exercise>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertExercise(e: Exercise): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertExercises(e: List<Exercise>)
    @Delete suspend fun deleteExercise(e: Exercise)
    @Query("DELETE FROM exercises") suspend fun clearExercises()

    // Trainingseinheiten
    @Query("SELECT * FROM training_sessions ORDER BY id DESC") fun observeSessions(): Flow<List<TrainingSession>>
    @Query("SELECT * FROM training_sessions") suspend fun getSessions(): List<TrainingSession>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSession(s: TrainingSession): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSessions(s: List<TrainingSession>)
    @Delete suspend fun deleteSession(s: TrainingSession)
    @Query("DELETE FROM training_sessions") suspend fun clearSessions()
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `video_items` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `uri` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `players` ADD COLUMN `clubId` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `formations` ADD COLUMN `ownClubId` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `formations` ADD COLUMN `opponentClubId` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `clubs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `isOwn` INTEGER NOT NULL, `system` TEXT NOT NULL, `notes` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `transfers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`playerId` INTEGER NOT NULL, `fromClubId` INTEGER NOT NULL, `toClubId` INTEGER NOT NULL, " +
                "`date` TEXT NOT NULL, `note` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `attribute_defs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `positive` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `attribute_links` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`targetType` TEXT NOT NULL, `targetId` INTEGER NOT NULL, `attributeId` INTEGER NOT NULL, " +
                "`level` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `analyses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`targetType` TEXT NOT NULL, `targetId` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                "`text` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        // Bisherige Spieler gehören zum Standard-Verein "Mein Team"
        db.execSQL("INSERT INTO `clubs` (`name`, `isOwn`, `system`, `notes`) VALUES ('Mein Team', 1, '', '')")
        db.execSQL("UPDATE `players` SET `clubId` = (SELECT `id` FROM `clubs` WHERE `isOwn` = 1 LIMIT 1)")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `set_pieces` ADD COLUMN `diagramJson` TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `seasons` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `isActive` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `matches` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`seasonId` INTEGER NOT NULL, `dateMillis` INTEGER NOT NULL, `ownClubId` INTEGER NOT NULL, " +
                "`opponentClubId` INTEGER NOT NULL, `opponentName` TEXT NOT NULL, `home` INTEGER NOT NULL, " +
                "`competition` TEXT NOT NULL, `goalsFor` INTEGER NOT NULL, `goalsAgainst` INTEGER NOT NULL, " +
                "`played` INTEGER NOT NULL, `formationId` INTEGER NOT NULL, `notes` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `match_players` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`matchId` INTEGER NOT NULL, `playerId` INTEGER NOT NULL, `started` INTEGER NOT NULL, " +
                "`minutes` INTEGER NOT NULL, `goals` INTEGER NOT NULL, `assists` INTEGER NOT NULL, " +
                "`yellow` INTEGER NOT NULL, `red` INTEGER NOT NULL, `injured` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `availabilities` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`playerId` INTEGER NOT NULL, `type` TEXT NOT NULL, `untilMillis` INTEGER NOT NULL, " +
                "`note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `attendances` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sessionId` INTEGER NOT NULL, `playerId` INTEGER NOT NULL, `status` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `evaluations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`playerId` INTEGER NOT NULL, `dateMillis` INTEGER NOT NULL, `technik` INTEGER NOT NULL, " +
                "`taktik` INTEGER NOT NULL, `athletik` INTEGER NOT NULL, `mental` INTEGER NOT NULL, " +
                "`note` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `video_markers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`videoId` INTEGER NOT NULL, `timestampMs` INTEGER NOT NULL, `lengthMs` INTEGER NOT NULL, " +
                "`type` TEXT NOT NULL, `playerId` INTEGER NOT NULL, `note` TEXT NOT NULL)"
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `match_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`matchId` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `type` TEXT NOT NULL, " +
                "`playerId` INTEGER NOT NULL, `otherPlayerId` INTEGER NOT NULL)"
        )
    }
}

/** Alle Migrationen in Reihenfolge; neue Migrationen hier anhängen (wird auch von den Tests genutzt). */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)

@Database(
    entities = [
        Player::class, Formation::class, SetPiece::class, VideoAnnotation::class,
        Briefing::class, Exercise::class, TrainingSession::class, VideoItem::class,
        Club::class, Transfer::class, AttributeDef::class, AttributeLink::class, Analysis::class,
        Season::class, Match::class, MatchPlayer::class, Availability::class, Attendance::class,
        Evaluation::class, VideoMarker::class, MatchEvent::class
    ],
    version = 6, // muss AppDatabase.VERSION entsprechen
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): TacticsDao

    companion object {
        /** Aktuelle Schema-Version. Bei jeder Änderung an einer Tabelle erhöhen UND eine Migration ergänzen. */
        const val VERSION = 6

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tactics.db"
                ).addMigrations(*ALL_MIGRATIONS)
                    // Nur die Entwicklungsversion 1 darf (wie bisher) zurückgesetzt werden.
                    // Für alle anderen Versionen gilt: Fehlt eine Migration, bricht die App ab,
                    // statt still alle Daten zu löschen. Die Daten bleiben dann auf dem Gerät erhalten
                    // und ein korrigiertes Update kann sie wieder öffnen.
                    .fallbackToDestructiveMigrationFrom(1)
                    .build().also { instance = it }
            }
    }
}

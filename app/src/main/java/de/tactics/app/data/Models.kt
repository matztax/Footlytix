package de.tactics.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

// ---------------------------------------------------------------- Kader
@Serializable
@Entity(tableName = "players")
data class Player(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val number: Int = 0,
    val position: String = "",
    // Trainer-Profil
    val strengths: String = "",
    val weaknesses: String = "",
    val goals: String = "",
    val notes: String = "",
    // Bewertungen 0 = nicht bewertet, 1..5 Sterne
    val ratingTechnik: Int = 0,
    val ratingTaktik: Int = 0,
    val ratingAthletik: Int = 0,
    val ratingMental: Int = 0,
    // Verein (0 = vereinslos); bleibt bei Transfers samt allen Attributen erhalten
    val clubId: Long = 0
) {
    fun averageRating(): Float? {
        val r = listOf(ratingTechnik, ratingTaktik, ratingAthletik, ratingMental).filter { it > 0 }
        return if (r.isEmpty()) null else r.average().toFloat()
    }
}

// ---------------------------------------------------------------- Vereine & Datenbank
@Serializable
@Entity(tableName = "clubs")
data class Club(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isOwn: Boolean = false,
    val system: String = "",
    val notes: String = ""
)

@Serializable
@Entity(tableName = "transfers")
data class Transfer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playerId: Long,
    val fromClubId: Long,
    val toClubId: Long,
    val date: String,
    val note: String = ""
)

/** Frei definierbares Attribut (positiv oder negativ) in der Datenbank. */
@Serializable
@Entity(tableName = "attribute_defs")
data class AttributeDef(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val positive: Boolean
)

/** Zuordnung eines Attributs zu einem Spieler ("player") oder Verein ("club"), Ausprägung 1..5. */
@Serializable
@Entity(tableName = "attribute_links")
data class AttributeLink(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetType: String,
    val targetId: Long,
    val attributeId: Long,
    val level: Int = 3
)

/** Gespeicherte Analyse zu einem Spieler ("player") oder Verein ("club"). */
@Serializable
@Entity(tableName = "analyses")
data class Analysis(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetType: String,
    val targetId: Long,
    val title: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)

// ---------------------------------------------------------------- Taktik
/** Spielfigur auf dem Taktikfeld. x/y sind normiert (0..1). playerId = -1: kein Spieler zugewiesen. */
@Serializable
data class Token(
    val label: String,
    val x: Float,
    val y: Float,
    val opponent: Boolean = false,
    val playerId: Long = -1L
)

@Serializable
data class Phase(
    val name: String,
    val tokens: List<Token>,
    val arrows: List<Scribble> = emptyList()
)

@Serializable
@Entity(tableName = "formations")
data class Formation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phasesJson: String,
    val ownClubId: Long = 0,
    val opponentClubId: Long = 0
)

@Serializable
data class RoleAssignment(val role: String, val player: String)

@Serializable
@Entity(tableName = "set_pieces")
data class SetPiece(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val rolesJson: String,
    val notes: String = "",
    /** Diagramm auf dem Spielfeld als Phase-JSON (Figuren und Pfeile); leer = noch keins */
    val diagramJson: String = ""
)

// ---------------------------------------------------------------- Video
/** Ein Strich der Zeichnung; points = x1,y1,x2,y2,... (normiert 0..1). */
@Serializable
data class Scribble(val points: List<Float>)

@Serializable
@Entity(tableName = "video_annotations")
data class VideoAnnotation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoUri: String,
    val timestampMs: Long,
    val scribblesJson: String,
    val note: String = ""
)

/** Ein Video in der Bibliothek (nur Verweis auf die Datei + frei wählbarer Name). */
@Serializable
@Entity(tableName = "video_items")
data class VideoItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val uri: String,
    val createdAt: Long = System.currentTimeMillis()
)

// ---------------------------------------------------------------- Briefing
@Serializable
@Entity(tableName = "briefings")
data class Briefing(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)

// ---------------------------------------------------------------- Training
/** Element im Übungsaufbau (Material, Spieler, ...). x/y normiert (0..1). */
@Serializable
data class ExItem(
    val type: String,
    val x: Float,
    val y: Float,
    val label: String = "",
    val rotated: Boolean = false
)

data class ItemType(val key: String, val label: String, val isMaterial: Boolean)

val ITEM_TYPES = listOf(
    ItemType("cone", "Hütchen", true),
    ItemType("pole", "Stange", true),
    ItemType("wall", "Mauer", true),
    ItemType("ladder", "Koordinationsleiter", true),
    ItemType("hurdle", "Hürde", true),
    ItemType("goal", "Tor", true),
    ItemType("minigoal", "Minitor", true),
    ItemType("ball", "Ball", true),
    ItemType("player", "Spieler", false),
    ItemType("gk", "Torwart", false),
    ItemType("opp", "Gegner", false)
)

val EXERCISE_CATEGORIES = listOf(
    "Aufwärmen", "Technik", "Passspiel", "Taktik", "Spielform",
    "Abschluss", "Athletik", "Torwart", "Auslaufen"
)

@Serializable
@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String = "Technik",
    val durationMin: Int = 10,
    val playersCount: Int = 0,
    val description: String = "",
    val extraMaterial: String = "",
    val itemsJson: String = "[]",
    val scribblesJson: String = "[]"
)

@Serializable
data class SessionEntry(val exerciseId: Long, val minutes: Int)

@Serializable
@Entity(tableName = "training_sessions")
data class TrainingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val date: String = "",
    val goal: String = "",
    val notes: String = "",
    val entriesJson: String = "[]"
)

// ---------------------------------------------------------------- Saison & Spiele
@Serializable
@Entity(tableName = "seasons")
data class Season(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isActive: Boolean = false
)

@Serializable
@Entity(tableName = "matches")
data class Match(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seasonId: Long = 0,
    val dateMillis: Long,
    val ownClubId: Long = 0,
    val opponentClubId: Long = 0,
    val opponentName: String = "",
    val home: Boolean = true,
    val competition: String = "Liga",
    val goalsFor: Int = 0,
    val goalsAgainst: Int = 0,
    val played: Boolean = false,
    val formationId: Long = 0,
    val notes: String = ""
)

/** Einsatz eines Spielers in einem Spiel samt Statistik. */
@Serializable
@Entity(tableName = "match_players")
data class MatchPlayer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchId: Long = 0,
    val playerId: Long,
    val started: Boolean = false,
    val minutes: Int = 0,
    val goals: Int = 0,
    val assists: Int = 0,
    val yellow: Int = 0,
    val red: Int = 0,
    val injured: Boolean = false
)

/** Ereignis im Spiel (Live-Modus): goal, conceded, yellow, red, sub, injury. */
@Serializable
@Entity(tableName = "match_events")
data class MatchEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchId: Long,
    val minute: Int,
    val type: String,
    /** Torschütze, Karte, Verletzter bzw. bei "sub" der ausgewechselte Spieler */
    val playerId: Long = 0,
    /** Vorlagengeber bei "goal" bzw. eingewechselter Spieler bei "sub" */
    val otherPlayerId: Long = 0
)

// ---------------------------------------------------------------- Verfügbarkeit, Anwesenheit, Entwicklung
@Serializable
@Entity(tableName = "availabilities")
data class Availability(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playerId: Long,
    val type: String,
    /** Ende des Zeitraums (Tagesende) in ms; 0 = unbefristet */
    val untilMillis: Long = 0,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** Anwesenheit im Training: status = "present", "excused" oder "absent". */
@Serializable
@Entity(tableName = "attendances")
data class Attendance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val playerId: Long,
    val status: String
)

/** Datierte Bewertung eines Spielers (Momentaufnahme der Sterne). */
@Serializable
@Entity(tableName = "evaluations")
data class Evaluation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playerId: Long,
    val dateMillis: Long,
    val technik: Int = 0,
    val taktik: Int = 0,
    val athletik: Int = 0,
    val mental: Int = 0,
    val note: String = ""
) {
    fun average(): Float? {
        val r = listOf(technik, taktik, athletik, mental).filter { it > 0 }
        return if (r.isEmpty()) null else r.average().toFloat()
    }
}

/** Marker oder Clip in einem Video, optional einer Person zugeordnet. */
@Serializable
@Entity(tableName = "video_markers")
data class VideoMarker(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: Long,
    val timestampMs: Long,
    val lengthMs: Long = 0,
    val type: String,
    val playerId: Long = 0,
    val note: String = ""
)

val MARKER_TYPES = listOf("Pass", "Tor", "Ballverlust", "Chance", "Foul", "Standard", "Sonstiges")

val AVAIL_TYPES = listOf(
    "injured" to "Verletzt",
    "sick" to "Krank",
    "suspended" to "Gesperrt",
    "vacation" to "Urlaub",
    "other" to "Nicht verfügbar"
)

fun availLabel(key: String): String = AVAIL_TYPES.firstOrNull { it.first == key }?.second ?: key

/** Aktuell gültiger Status eines Spielers (abgelaufene Zeiträume zählen nicht). */
fun activeAvailability(list: List<Availability>, playerId: Long, now: Long = System.currentTimeMillis()): Availability? =
    list.filter { it.playerId == playerId && (it.untilMillis == 0L || it.untilMillis >= now) }
        .maxByOrNull { it.createdAt }

data class PlayerStats(
    val games: Int,
    val starts: Int,
    val minutes: Int,
    val goals: Int,
    val assists: Int,
    val yellow: Int,
    val red: Int
)

/** Statistik eines Spielers; seasonId = null summiert über alle Saisons. */
fun computePlayerStats(playerId: Long, seasonId: Long?, matches: List<Match>, rows: List<MatchPlayer>): PlayerStats {
    val valid = matches.filter { it.played && (seasonId == null || it.seasonId == seasonId) }.map { it.id }.toSet()
    val mine = rows.filter { it.playerId == playerId && it.matchId in valid }
    return PlayerStats(
        games = mine.count { it.started || it.minutes > 0 },
        starts = mine.count { it.started },
        minutes = mine.sumOf { it.minutes },
        goals = mine.sumOf { it.goals },
        assists = mine.sumOf { it.assists },
        yellow = mine.sumOf { it.yellow },
        red = mine.sumOf { it.red }
    )
}

// ---------------------------------------------------------------- Backup
@Serializable
data class BackupBundle(
    val version: Int = 6,
    val players: List<Player> = emptyList(),
    val formations: List<Formation> = emptyList(),
    val setPieces: List<SetPiece> = emptyList(),
    val annotations: List<VideoAnnotation> = emptyList(),
    val briefings: List<Briefing> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    val sessions: List<TrainingSession> = emptyList(),
    val videos: List<VideoItem> = emptyList(),
    val clubs: List<Club> = emptyList(),
    val transfers: List<Transfer> = emptyList(),
    val attributeDefs: List<AttributeDef> = emptyList(),
    val attributeLinks: List<AttributeLink> = emptyList(),
    val analyses: List<Analysis> = emptyList(),
    val seasons: List<Season> = emptyList(),
    val matches: List<Match> = emptyList(),
    val matchPlayers: List<MatchPlayer> = emptyList(),
    val availabilities: List<Availability> = emptyList(),
    val attendances: List<Attendance> = emptyList(),
    val evaluations: List<Evaluation> = emptyList(),
    val markers: List<VideoMarker> = emptyList(),
    val matchEvents: List<MatchEvent> = emptyList()
)

// ---------------------------------------------------------------- JSON-Helfer
fun decodePhases(json: String): List<Phase> =
    runCatching { AppJson.decodeFromString<List<Phase>>(json) }.getOrDefault(emptyList())

fun encodePhases(phases: List<Phase>): String = AppJson.encodeToString(phases)

fun decodePhase(json: String): Phase =
    runCatching { AppJson.decodeFromString<Phase>(json) }.getOrNull() ?: Phase("Standard", emptyList())

fun encodePhase(phase: Phase): String = AppJson.encodeToString(phase)

fun decodeRoles(json: String): List<RoleAssignment> =
    runCatching { AppJson.decodeFromString<List<RoleAssignment>>(json) }.getOrDefault(emptyList())

fun encodeRoles(roles: List<RoleAssignment>): String = AppJson.encodeToString(roles)

fun decodeScribbles(json: String): List<Scribble> =
    runCatching { AppJson.decodeFromString<List<Scribble>>(json) }.getOrDefault(emptyList())

fun encodeScribbles(s: List<Scribble>): String = AppJson.encodeToString(s)

fun decodeItems(json: String): List<ExItem> =
    runCatching { AppJson.decodeFromString<List<ExItem>>(json) }.getOrDefault(emptyList())

fun encodeItems(items: List<ExItem>): String = AppJson.encodeToString(items)

fun decodeEntries(json: String): List<SessionEntry> =
    runCatching { AppJson.decodeFromString<List<SessionEntry>>(json) }.getOrDefault(emptyList())

fun encodeEntries(e: List<SessionEntry>): String = AppJson.encodeToString(e)

// ---------------------------------------------------------------- Material
fun materialCounts(items: List<ExItem>): Map<String, Int> {
    val materialKeys = ITEM_TYPES.filter { it.isMaterial }.map { it.key }.toSet()
    return items.filter { it.type in materialKeys }.groupingBy { it.type }.eachCount()
}

fun materialText(counts: Map<String, Int>): String =
    ITEM_TYPES.filter { (counts[it.key] ?: 0) > 0 }
        .joinToString(", ") { "${counts[it.key]}× ${it.label}" }

// ---------------------------------------------------------------- Aufstellungen erzeugen
/** "4-2-3-1" -> [4,2,3,1]; bei ungültiger Eingabe 4-4-2. */
fun parseSystem(s: String): List<Int> {
    val parts = s.split('-', ' ', ',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..6 }
    return if (parts.isNotEmpty() && parts.sum() == 10) parts else listOf(4, 4, 2)
}

fun buildTokens(system: String, opponent: Boolean): List<Token> {
    val rows = listOf(1) + parseSystem(system)
    val tokens = mutableListOf<Token>()
    var n = 1
    rows.forEachIndexed { r, count ->
        val own = 0.93f - r * (0.43f / (rows.size - 1))
        val y = if (opponent) 1f - own else own
        for (k in 0 until count) {
            tokens += Token(label = n.toString(), x = (k + 1f) / (count + 1f), y = y, opponent = opponent)
            n++
        }
    }
    return tokens
}

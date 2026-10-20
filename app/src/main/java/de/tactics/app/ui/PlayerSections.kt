@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTextApi::class)

package de.tactics.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.tactics.app.MainViewModel
import de.tactics.app.data.*

// ------------------------------------------------------------------ Verfügbarkeit
@Composable
fun AvailabilitySection(vm: MainViewModel, playerId: Long) {
    val ctx = LocalContext.current
    val all by vm.availabilities.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    val mine = all.filter { it.playerId == playerId }.sortedByDescending { it.createdAt }
    var adding by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Verfügbarkeit", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { adding = true }) { Text("+ Status") }
        }
        val active = activeAvailability(all, playerId, now)
        Text(
            if (active == null) "Aktuell verfügbar" else "Aktuell: " + availLabel(active.type),
            color = if (active == null) PositiveColor else NegativeColor,
            style = MaterialTheme.typography.titleSmall
        )
        mine.forEach { a ->
            val isActive = a.untilMillis == 0L || a.untilMillis >= now
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        availLabel(a.type) + " · " + (if (a.untilMillis == 0L) "unbefristet" else "bis " + formatDate(a.untilMillis)) +
                            (if (isActive) "" else " (abgelaufen)"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (a.note.isNotBlank()) Text(a.note, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { vm.deleteAvailability(a) }) { Text(if (isActive) "Wieder fit" else "✕") }
            }
        }
    }

    if (adding) {
        var type by remember { mutableStateOf(AVAIL_TYPES[0].first) }
        var until by remember { mutableStateOf(0L) }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Status eintragen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AVAIL_TYPES.forEach { (key, label) ->
                            FilterChip(selected = type == key, onClick = { type = key }, label = { Text(label) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = until == 0L, onClick = { until = 0L }, label = { Text("unbefristet") })
                        OutlinedButton(onClick = {
                            showDatePicker(ctx, System.currentTimeMillis(), endOfDay = true) { until = it }
                        }) { Text(if (until == 0L) "Bis Datum wählen" else "Bis " + formatDate(until)) }
                    }
                    OutlinedTextField(
                        note, { note = it }, label = { Text("Notiz (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveAvailability(Availability(playerId = playerId, type = type, untilMillis = until, note = note.trim()))
                    adding = false
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Abbrechen") } }
        )
    }
}

// ------------------------------------------------------------------ Statistik & Trainingsbeteiligung
@Composable
fun PlayerStatsSection(vm: MainViewModel, playerId: Long) {
    val matches by vm.matches.collectAsStateWithLifecycle()
    val rows by vm.matchPlayers.collectAsStateWithLifecycle()
    val seasons by vm.seasons.collectAsStateWithLifecycle()
    val attendances by vm.attendances.collectAsStateWithLifecycle()
    val season = seasons.firstOrNull { it.isActive } ?: seasons.firstOrNull()
    val cur = if (season != null) computePlayerStats(playerId, season.id, matches, rows) else null
    val total = computePlayerStats(playerId, null, matches, rows)
    val mine = attendances.filter { it.playerId == playerId }
    val present = mine.count { it.status == "present" }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Statistik", style = MaterialTheme.typography.titleMedium)
        if (cur != null && season != null) {
            Text(season.name + ": " + statLine(cur), style = MaterialTheme.typography.bodyMedium)
        }
        Text("Gesamt: " + statLine(total), style = MaterialTheme.typography.bodyMedium)
        Text(
            if (mine.isEmpty()) "Training: noch keine Anwesenheit erfasst"
            else "Training: $present von ${mine.size} anwesend (${present * 100 / mine.size} %)",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun statLine(s: PlayerStats): String =
    "${s.games} Einsätze, ${s.minutes} Min, ${s.goals} Tore, ${s.assists} Vorlagen, ${s.yellow} Gelb, ${s.red} Rot"

// ------------------------------------------------------------------ Entwicklung über Zeit
@Composable
fun DevelopmentSection(vm: MainViewModel, playerId: Long, technik: Int, taktik: Int, athletik: Int, mental: Int) {
    val all by vm.evaluations.collectAsStateWithLifecycle()
    val mine = all.filter { it.playerId == playerId }.sortedBy { it.dateMillis }
    var saving by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Entwicklung", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Button(onClick = { saving = true }) { Text("Bewertung festhalten") }
        }
        if (mine.isEmpty()) {
            Text(
                "Stelle oben die Sterne ein und tippe auf „Bewertung festhalten“. Jede Bewertung wird mit Datum gespeichert und ergibt Netzdiagramm und Verlauf.",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            RadarChart(mine.last(), if (mine.size > 1) mine[mine.size - 2] else null)
            if (mine.size > 1) TrendChart(mine)
            mine.reversed().forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            formatDate(e.dateMillis) + " · Ø " + (e.average()?.let { "%.1f".format(it) } ?: "–") +
                                "  (T${e.technik} Ta${e.taktik} A${e.athletik} M${e.mental})",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (e.note.isNotBlank()) Text(e.note, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { vm.deleteEvaluation(e) }) { Text("✕") }
                }
            }
        }
    }

    if (saving) {
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { saving = false },
            title = { Text("Bewertung festhalten") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Technik $technik · Taktik $taktik · Athletik $athletik · Mentalität $mental")
                    OutlinedTextField(
                        note, { note = it }, label = { Text("Notiz (z. B. nach Trainingslager)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveEvaluation(
                        Evaluation(
                            playerId = playerId, dateMillis = System.currentTimeMillis(),
                            technik = technik, taktik = taktik, athletik = athletik, mental = mental, note = note.trim()
                        )
                    )
                    saving = false
                }) { Text("Festhalten") }
            },
            dismissButton = { TextButton(onClick = { saving = false }) { Text("Abbrechen") } }
        )
    }
}

/** Netzdiagramm mit vier Achsen; die neueste Bewertung gefüllt, die vorherige als Umriss. */
@Composable
private fun RadarChart(latest: Evaluation, previous: Evaluation?) {
    val measurer = rememberTextMeasurer()
    val style = remember { TextStyle(color = Color(0xFFB8C8D6), fontSize = 12.sp) }
    Canvas(Modifier.fillMaxWidth().height(230.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = minOf(size.width, size.height) / 2 - 36.dp.toPx()
        val labels = listOf("Technik", "Taktik", "Athletik", "Mentalität")
        // Achsen: oben, rechts, unten, links
        val dirs = listOf(Offset(0f, -1f), Offset(1f, 0f), Offset(0f, 1f), Offset(-1f, 0f))

        for (ring in 1..5) {
            val path = Path()
            dirs.forEachIndexed { i, d ->
                val p = c + d * (r * ring / 5f)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            drawPath(path, Color.White.copy(alpha = 0.18f), style = Stroke(1.dp.toPx()))
        }
        dirs.forEach { d -> drawLine(Color.White.copy(alpha = 0.18f), c, c + d * r, 1.dp.toPx()) }

        fun poly(e: Evaluation): Path {
            val v = listOf(e.technik, e.taktik, e.athletik, e.mental)
            val path = Path()
            dirs.forEachIndexed { i, d ->
                val p = c + d * (r * v[i] / 5f)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            return path
        }
        if (previous != null) {
            drawPath(poly(previous), Color(0xFFFF7A7A).copy(alpha = 0.9f), style = Stroke(2.dp.toPx()))
        }
        val lp = poly(latest)
        drawPath(lp, PositiveColor.copy(alpha = 0.30f), style = Fill)
        drawPath(lp, PositiveColor, style = Stroke(2.5f.dp.toPx()))

        labels.forEachIndexed { i, text ->
            val layout = measurer.measure(text, style)
            val p = c + dirs[i] * (r + 8.dp.toPx())
            val tl = when (i) {
                0 -> Offset(p.x - layout.size.width / 2f, p.y - layout.size.height.toFloat() - 2.dp.toPx())
                1 -> Offset(p.x, p.y - layout.size.height / 2f)
                2 -> Offset(p.x - layout.size.width / 2f, p.y)
                else -> Offset(p.x - layout.size.width.toFloat(), p.y - layout.size.height / 2f)
            }
            drawText(layout, topLeft = tl)
        }
    }
    Text(
        if (previous == null) "Netzdiagramm der letzten Bewertung (grün)" else "Letzte Bewertung (grün) und vorherige (rot)",
        style = MaterialTheme.typography.bodySmall
    )
}

/** Verlauf des Durchschnitts über alle festgehaltenen Bewertungen. */
@Composable
private fun TrendChart(list: List<Evaluation>) {
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val pad = 12.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        for (g in 0..5) {
            val y = pad + h - h * g / 5f
            drawLine(Color.White.copy(alpha = 0.12f), Offset(pad, y), Offset(pad + w, y), 1.dp.toPx())
        }
        val pts = list.mapIndexedNotNull { i, e ->
            val a = e.average() ?: return@mapIndexedNotNull null
            val x = pad + if (list.size == 1) w / 2 else w * i / (list.size - 1)
            Offset(x, pad + h - h * a / 5f)
        }
        val path = Path()
        pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        drawPath(path, PositiveColor, style = Stroke(2.5f.dp.toPx()))
        pts.forEach { drawCircle(PositiveColor, 4.dp.toPx(), it) }
    }
    Text(
        formatDate(list.first().dateMillis) + "  →  " + formatDate(list.last().dateMillis) + "  (Ø der vier Bereiche)",
        style = MaterialTheme.typography.bodySmall
    )
}

// ------------------------------------------------------------------ Anwesenheit im Training
@Composable
fun AttendanceSection(vm: MainViewModel, sessionId: Long, squad: List<Player>) {
    val all by vm.attendances.collectAsStateWithLifecycle()
    val availabilities by vm.availabilities.collectAsStateWithLifecycle()
    val forSession = all.filter { it.sessionId == sessionId }
    val present = forSession.count { it.status == "present" }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Anwesenheit ($present/${squad.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.setAttendanceForAll(sessionId, squad.map { it.id }, "present") }) { Text("Alle da") }
        }
        if (squad.isEmpty()) Text("Dein Team hat noch keine Spieler.", style = MaterialTheme.typography.bodySmall)
        squad.forEach { p ->
            val status = forSession.firstOrNull { it.playerId == p.id }?.status ?: ""
            val avail = activeAvailability(availabilities, p.id)
            Column {
                Text(
                    "${p.number} ${p.name}" + (avail?.let { " – " + availLabel(it.type) } ?: ""),
                    color = if (avail != null) NegativeColor else Color.Unspecified
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = status == "present",
                        onClick = { vm.setAttendance(sessionId, p.id, if (status == "present") "" else "present") },
                        label = { Text("Da") }
                    )
                    FilterChip(
                        selected = status == "excused",
                        onClick = { vm.setAttendance(sessionId, p.id, if (status == "excused") "" else "excused") },
                        label = { Text("Entschuldigt") }
                    )
                    FilterChip(
                        selected = status == "absent",
                        onClick = { vm.setAttendance(sessionId, p.id, if (status == "absent") "" else "absent") },
                        label = { Text("Fehlt") }
                    )
                }
            }
        }
    }
}

@file:OptIn(ExperimentalMaterial3Api::class)

package de.tactics.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.tactics.app.Exporter
import de.tactics.app.MainViewModel
import de.tactics.app.data.*

/** Tab "Training": Einheiten planen und Übungen bauen. */
@Composable
fun TrainingHost(vm: MainViewModel, onOpenExercise: (Long) -> Unit, onOpenSession: (Long) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Einheiten") })
            FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Übungen") })
        }
        Box(Modifier.weight(1f)) {
            if (mode == 0) SessionListScreen(vm, onOpenSession) else ExerciseListScreen(vm, onOpenExercise)
        }
    }
}

@Composable
fun SessionListScreen(vm: MainViewModel, onOpen: (Long) -> Unit) {
    val list by vm.sessions.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        if (list.isEmpty()) {
            Text(
                "Noch keine Trainingseinheiten.\nMit + eine Einheit planen und Übungen hinzufügen.",
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            items(list, key = { it.id }) { s ->
                val entries = decodeEntries(s.entriesJson)
                val total = entries.sumOf { it.minutes }
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clickable { onOpen(s.id) }
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(s.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(
                                s.date.ifBlank { null },
                                "${entries.size} Übungen",
                                "$total Min"
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (s.goal.isNotBlank()) Text("Ziel: ${s.goal}", style = MaterialTheme.typography.bodySmall)
                        val names = entries.mapNotNull { e -> exercises.firstOrNull { it.id == e.exerciseId }?.name }
                        if (names.isNotEmpty()) Text(names.joinToString(" → "), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { onOpen(0L) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "Einheit hinzufügen") }
    }
}

private fun sessionMaterial(entries: List<SessionEntry>, exercises: List<Exercise>): String {
    val maxCounts = mutableMapOf<String, Int>()
    val extras = mutableListOf<String>()
    entries.forEach { e ->
        val ex = exercises.firstOrNull { it.id == e.exerciseId }
        if (ex != null) {
            materialCounts(decodeItems(ex.itemsJson)).forEach { (k, v) ->
                maxCounts[k] = maxOf(maxCounts[k] ?: 0, v)
            }
            if (ex.extraMaterial.isNotBlank()) extras += ex.extraMaterial
        }
    }
    val base = materialText(maxCounts)
    return listOf(base, extras.distinct().joinToString(", ")).filter { it.isNotBlank() }.joinToString(", ")
}

private fun shareSession(ctx: Context, title: String, date: String, goal: String, notes: String, lines: List<String>, material: String) {
    val text = buildString {
        append(title)
        if (date.isNotBlank()) append(" – ").append(date)
        append("\n")
        if (goal.isNotBlank()) append("Ziel: ").append(goal).append("\n")
        append("\n")
        lines.forEach { append(it).append("\n") }
        if (material.isNotBlank()) append("\nMaterial: ").append(material).append("\n")
        if (notes.isNotBlank()) append("\nNotizen: ").append(notes).append("\n")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    ctx.startActivity(Intent.createChooser(intent, "Trainingsplan teilen"))
}

@Composable
fun SessionEditorScreen(vm: MainViewModel, id: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val ownPlayers by vm.ownPlayers.collectAsStateWithLifecycle()
    val existing = sessions.firstOrNull { it.id == id }
    val key = existing?.id ?: 0L

    var title by remember(key) { mutableStateOf(existing?.title ?: "") }
    var date by remember(key) { mutableStateOf(existing?.date ?: "") }
    var goal by remember(key) { mutableStateOf(existing?.goal ?: "") }
    var notes by remember(key) { mutableStateOf(existing?.notes ?: "") }
    val entries = remember(key) {
        mutableStateListOf<SessionEntry>().apply { existing?.let { addAll(decodeEntries(it.entriesJson)) } }
    }
    var showPicker by remember { mutableStateOf(false) }

    val total = entries.sumOf { it.minutes }
    val material = sessionMaterial(entries.toList(), exercises)

    fun pickDate() {
        val cal = java.util.Calendar.getInstance()
        android.app.DatePickerDialog(
            ctx,
            { _, y, m, d -> date = "%02d.%02d.%04d".format(d, m + 1, y) },
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH),
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
    }

    val dirty = if (existing != null) {
        title.trim() != existing.title || date != existing.date || goal.trim() != existing.goal ||
            notes.trim() != existing.notes || entries.toList() != decodeEntries(existing.entriesJson)
    } else {
        title.isNotBlank() || goal.isNotBlank() || notes.isNotBlank() || entries.isNotEmpty()
    }
    val goBack = rememberBackGuard(dirty, onBack)
    var confirmDel by remember { mutableStateOf(false) }
    if (confirmDel && existing != null) {
        ConfirmDialog(
            title = "Einheit löschen?",
            text = "„${existing.title}“ samt Anwesenheit wird gelöscht.",
            onConfirm = { vm.deleteSession(existing); confirmDel = false; onBack() },
            onDismiss = { confirmDel = false }
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = goBack) { Text("← Zurück") }
            Spacer(Modifier.weight(1f))
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    vm.saveSession(
                        TrainingSession(
                            id = if (id > 0) id else 0L,
                            title = title.trim(),
                            date = date,
                            goal = goal.trim(),
                            notes = notes.trim(),
                            entriesJson = encodeEntries(entries.toList())
                        )
                    )
                    onBack()
                }
            ) { Text("Speichern") }
        }

        OutlinedTextField(
            title, { title = it }, label = { Text("Titel (z. B. Di – Passspiel & Abschluss)") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickDate() }) { Text(if (date.isBlank()) "Datum wählen" else date) }
            Text("Gesamt: $total Min", style = MaterialTheme.typography.titleSmall)
        }
        OutlinedTextField(
            goal, { goal = it }, label = { Text("Trainingsziel / Schwerpunkt") },
            minLines = 2, modifier = Modifier.fillMaxWidth()
        )

        Text("Ablauf", style = MaterialTheme.typography.titleMedium)
        if (entries.isEmpty()) Text("Noch keine Übungen. Mit „Übung hinzufügen“ den Ablauf zusammenstellen.")

        entries.forEachIndexed { i, e ->
            val ex = exercises.firstOrNull { it.id == e.exerciseId }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${i + 1}. ${ex?.name ?: "(Übung gelöscht)"}", style = MaterialTheme.typography.titleSmall)
                    if (ex != null) Text(ex.category, style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { if (e.minutes > 1) entries[i] = e.copy(minutes = e.minutes - 1) }) { Text("−") }
                        Text("${e.minutes} Min")
                        OutlinedButton(onClick = { entries[i] = e.copy(minutes = e.minutes + 1) }) { Text("+") }
                        Spacer(Modifier.weight(1f))
                        TextButton(enabled = i > 0, onClick = {
                            val x = entries.removeAt(i); entries.add(i - 1, x)
                        }) { Text("↑") }
                        TextButton(enabled = i < entries.size - 1, onClick = {
                            val x = entries.removeAt(i); entries.add(i + 1, x)
                        }) { Text("↓") }
                        TextButton(onClick = { entries.removeAt(i) }) { Text("✕") }
                    }
                }
            }
        }

        Button(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) { Text("Übung hinzufügen") }

        Text("Material (Maximum je Übung)", style = MaterialTheme.typography.titleMedium)
        Text(if (material.isBlank()) "Kein Material geplant." else material)

        if (existing != null) {
            AttendanceSection(vm, existing.id, ownPlayers)
        } else {
            Text("Die Anwesenheit kannst du eintragen, sobald die Einheit gespeichert ist.", style = MaterialTheme.typography.bodySmall)
        }

        OutlinedTextField(
            notes, { notes = it }, label = { Text("Notizen zur Einheit") },
            minLines = 2, modifier = Modifier.fillMaxWidth()
        )

        OutlinedButton(
            enabled = title.isNotBlank(),
            onClick = {
                val lines = entries.mapIndexed { i, e ->
                    val ex = exercises.firstOrNull { it.id == e.exerciseId }
                    "${i + 1}. ${ex?.name ?: "Übung"} (${e.minutes} Min)" +
                        (if (ex != null && ex.description.isNotBlank()) " – ${ex.description}" else "")
                }
                shareSession(ctx, title.trim(), date, goal.trim(), notes.trim(), lines, material)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Plan teilen") }

        OutlinedButton(
            enabled = title.isNotBlank(),
            onClick = {
                Exporter.attempt(ctx) {
                    val s = TrainingSession(id = id, title = title.trim(), date = date, goal = goal.trim(), notes = notes.trim())
                    val f = Exporter.sessionPdf(ctx, s, entries.toList(), exercises, material)
                    Exporter.share(ctx, f, "application/pdf", "Trainingsplan teilen")
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Als PDF teilen (mit Übungsskizzen)") }

        if (existing != null) {
            OutlinedButton(
                onClick = { confirmDel = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Einheit löschen") }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Übung wählen") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (exercises.isEmpty()) Text("Lege zuerst Übungen im Bereich „Übungen“ an.")
                    exercises.forEach { ex ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    entries.add(SessionEntry(ex.id, ex.durationMin))
                                    showPicker = false
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(ex.name, style = MaterialTheme.typography.titleSmall)
                            Text("${ex.category} · ${ex.durationMin} Min", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Abbrechen") } }
        )
    }
}

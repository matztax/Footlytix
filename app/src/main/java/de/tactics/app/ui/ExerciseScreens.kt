@file:OptIn(ExperimentalMaterial3Api::class)

package de.tactics.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import de.tactics.app.Exporter
import de.tactics.app.MainViewModel
import de.tactics.app.data.*

private val ROTATABLE = setOf("wall", "ladder", "hurdle", "goal", "minigoal")

@Composable
fun ExerciseListScreen(vm: MainViewModel, onOpen: (Long) -> Unit) {
    val list by vm.exercises.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        if (list.isEmpty()) {
            Text(
                "Noch keine Übungen.\nMit + eine Übung mit Aufbau und Material anlegen.",
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            items(list, key = { it.id }) { e ->
                val mat = materialText(materialCounts(decodeItems(e.itemsJson)))
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clickable { onOpen(e.id) }
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(e.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${e.category} · ${e.durationMin} Min" +
                                (if (e.playersCount > 0) " · ${e.playersCount} Spieler" else ""),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (mat.isNotBlank()) Text("Material: $mat", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { onOpen(0L) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "Übung hinzufügen") }
    }
}

@Composable
fun ExerciseEditorScreen(vm: MainViewModel, id: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val all by vm.exercises.collectAsStateWithLifecycle()
    val existing = all.firstOrNull { it.id == id }
    val key = existing?.id ?: 0L

    var name by remember(key) { mutableStateOf(existing?.name ?: "") }
    var category by remember(key) { mutableStateOf(existing?.category ?: EXERCISE_CATEGORIES[0]) }
    var duration by remember(key) { mutableStateOf((existing?.durationMin ?: 10).toString()) }
    var playersCount by remember(key) {
        mutableStateOf(existing?.playersCount?.takeIf { it > 0 }?.toString() ?: "")
    }
    var description by remember(key) { mutableStateOf(existing?.description ?: "") }
    var extra by remember(key) { mutableStateOf(existing?.extraMaterial ?: "") }
    var items by remember(key) { mutableStateOf<List<ExItem>>(existing?.let { decodeItems(it.itemsJson) } ?: emptyList()) }
    var scribbles by remember(key) {
        mutableStateOf<List<Scribble>>(existing?.let { decodeScribbles(it.scribblesJson) } ?: emptyList())
    }
    var drawMode by remember { mutableStateOf(false) }
    var selected by remember { mutableIntStateOf(-1) }

    fun addItem(type: String) {
        val n = items.count { it.type == type } + 1
        val label = if (type == "player" || type == "gk" || type == "opp") n.toString() else ""
        val k = items.size
        items = items + ExItem(type, 0.5f + ((k % 5) - 2) * 0.07f, 0.5f + ((k % 3) - 1) * 0.07f, label)
        selected = items.size - 1
        drawMode = false
    }

    val materials = materialText(materialCounts(items))

    val dirty = if (existing != null) {
        name.trim() != existing.name || category != existing.category ||
            duration != existing.durationMin.toString() ||
            playersCount != (if (existing.playersCount > 0) existing.playersCount.toString() else "") ||
            description.trim() != existing.description || extra.trim() != existing.extraMaterial ||
            items != decodeItems(existing.itemsJson) || scribbles != decodeScribbles(existing.scribblesJson)
    } else {
        name.isNotBlank() || description.isNotBlank() || items.isNotEmpty() || scribbles.isNotEmpty()
    }
    val goBack = rememberBackGuard(dirty, onBack)
    var confirmDel by remember { mutableStateOf(false) }
    if (confirmDel && existing != null) {
        ConfirmDialog(
            title = "Übung löschen?",
            text = "„${existing.name}“ wird gelöscht.",
            onConfirm = { vm.deleteExercise(existing); confirmDel = false; onBack() },
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
                enabled = name.isNotBlank(),
                onClick = {
                    vm.saveExercise(
                        Exercise(
                            id = if (id > 0) id else 0L,
                            name = name.trim(),
                            category = category,
                            durationMin = duration.toIntOrNull() ?: 10,
                            playersCount = playersCount.toIntOrNull() ?: 0,
                            description = description.trim(),
                            extraMaterial = extra.trim(),
                            itemsJson = encodeItems(items),
                            scribblesJson = encodeScribbles(scribbles)
                        )
                    )
                    onBack()
                }
            ) { Text("Speichern") }
        }

        OutlinedTextField(
            name, { name = it }, label = { Text("Name der Übung") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EXERCISE_CATEGORIES.forEach { c ->
                FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                duration, { duration = it.filter(Char::isDigit).take(3) },
                label = { Text("Minuten") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                playersCount, { playersCount = it.filter(Char::isDigit).take(2) },
                label = { Text("Spieler") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            description, { description = it },
            label = { Text("Ablauf & Coaching-Punkte") },
            minLines = 3, modifier = Modifier.fillMaxWidth()
        )

        Text("Aufbau", style = MaterialTheme.typography.titleMedium)
        Text(
            "Material unten antippen, dann auf dem Feld verschieben. Zum Scrollen außerhalb des Feldes wischen.",
            style = MaterialTheme.typography.bodySmall
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = !drawMode, onClick = { drawMode = false }, label = { Text("Verschieben") })
            FilterChip(selected = drawMode, onClick = { drawMode = true }, label = { Text("Wege zeichnen") })
        }

        ExerciseBoard(
            items = items,
            scribbles = scribbles,
            drawMode = drawMode,
            selected = selected,
            onItemsChange = { items = it },
            onScribblesChange = { scribbles = it },
            onSelect = { selected = it },
            modifier = Modifier.fillMaxWidth().aspectRatio(1.2f)
        )

        Text("Hinzufügen:", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ITEM_TYPES.forEach { t ->
                AssistChip(onClick = { addItem(t.key) }, label = { Text(t.label) })
            }
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = selected in items.indices && items[selected].type in ROTATABLE,
                onClick = {
                    items = items.toMutableList().also { it[selected] = it[selected].copy(rotated = !it[selected].rotated) }
                }
            ) { Text("Drehen") }
            OutlinedButton(
                enabled = selected in items.indices,
                onClick = {
                    items = items.toMutableList().also { it.removeAt(selected) }
                    selected = -1
                }
            ) { Text("Entfernen") }
            OutlinedButton(
                enabled = scribbles.isNotEmpty(),
                onClick = { scribbles = scribbles.dropLast(1) }
            ) { Text("Strich zurück") }
            OutlinedButton(
                enabled = scribbles.isNotEmpty(),
                onClick = { scribbles = emptyList() }
            ) { Text("Striche löschen") }
        }

        Text("Material (aus dem Aufbau)", style = MaterialTheme.typography.titleMedium)
        Text(if (materials.isBlank()) "Noch kein Material platziert." else materials)
        OutlinedTextField(
            extra, { extra = it },
            label = { Text("Weiteres Material (z. B. 10 Leibchen)") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                Exporter.attempt(ctx) {
                    val ex = Exercise(
                        id = id, name = name.trim().ifBlank { "Übung" }, category = category,
                        durationMin = duration.toIntOrNull() ?: 10, playersCount = playersCount.toIntOrNull() ?: 0,
                        description = description.trim(), extraMaterial = extra.trim(),
                        itemsJson = encodeItems(items), scribblesJson = encodeScribbles(scribbles)
                    )
                    Exporter.share(ctx, Exporter.exercisePdf(ctx, ex), "application/pdf", "Übung teilen")
                }
            }) { Text("Als PDF") }
            OutlinedButton(onClick = {
                Exporter.attempt(ctx) {
                    val ex = Exercise(
                        id = id, name = name.trim().ifBlank { "Übung" }, category = category,
                        durationMin = duration.toIntOrNull() ?: 10, playersCount = playersCount.toIntOrNull() ?: 0,
                        description = description.trim(), extraMaterial = extra.trim(),
                        itemsJson = encodeItems(items), scribblesJson = encodeScribbles(scribbles)
                    )
                    Exporter.share(ctx, Exporter.exercisePng(ctx, ex), "image/png", "Übung teilen")
                }
            }) { Text("Als Bild") }
        }

        if (existing != null) {
            OutlinedButton(
                onClick = { confirmDel = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Übung löschen") }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@file:OptIn(ExperimentalMaterial3Api::class)

package de.tactics.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.tactics.app.MainViewModel
import de.tactics.app.data.*

val PositiveColor = Color(0xFF5DE4AE)
val NegativeColor = Color(0xFFFF7A7A)

/** Öffnet den Datumsdialog des Systems; endOfDay = true liefert 23:59 Uhr des Tages. */
fun showDatePicker(ctx: android.content.Context, initialMillis: Long, endOfDay: Boolean = false, onPicked: (Long) -> Unit) {
    val cal = java.util.Calendar.getInstance()
    if (initialMillis > 0) cal.timeInMillis = initialMillis
    android.app.DatePickerDialog(
        ctx,
        { _, y, m, d ->
            val c = java.util.Calendar.getInstance()
            c.set(y, m, d, if (endOfDay) 23 else 12, if (endOfDay) 59 else 0, 0)
            onPicked(c.timeInMillis)
        },
        cal.get(java.util.Calendar.YEAR),
        cal.get(java.util.Calendar.MONTH),
        cal.get(java.util.Calendar.DAY_OF_MONTH)
    ).show()
}

fun formatDate(ms: Long): String =
    java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY).format(java.util.Date(ms))

/** Attribute (positiv/negativ) eines Spielers oder Vereins, frei aus dem Katalog wählbar oder neu anlegbar. */
@Composable
fun AttributeSection(vm: MainViewModel, targetType: String, targetId: Long) {
    val defs by vm.attributeDefs.collectAsStateWithLifecycle()
    val links by vm.attributeLinks.collectAsStateWithLifecycle()
    val mine = links.filter { it.targetType == targetType && it.targetId == targetId }
    var adding by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Attribute", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { adding = true }) { Text("+ Attribut") }
        }
        if (mine.isEmpty()) {
            Text("Noch keine Attribute. Mit „+ Attribut“ positive oder negative Eigenschaften hinzufügen.", style = MaterialTheme.typography.bodySmall)
        }
        mine.forEach { l ->
            val d = defs.firstOrNull { it.id == l.attributeId }
            if (d != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (d.positive) "＋" else "−",
                        color = if (d.positive) PositiveColor else NegativeColor,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.width(24.dp)
                    )
                    Text(d.name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    for (i in 1..5) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Stufe $i",
                            tint = if (i <= l.level) (if (d.positive) PositiveColor else NegativeColor) else Color(0x33FFFFFF),
                            modifier = Modifier.size(26.dp).clickable { vm.saveLink(l.copy(level = i)) }
                        )
                    }
                    TextButton(onClick = { vm.deleteLink(l) }) { Text("✕") }
                }
            }
        }
    }

    if (adding) {
        AddAttributeDialog(
            defs = defs,
            linkedIds = mine.map { it.attributeId }.toSet(),
            onDismiss = { adding = false },
            onPick = { def ->
                vm.saveLink(AttributeLink(targetType = targetType, targetId = targetId, attributeId = def.id))
            },
            onCreate = { name, positive ->
                vm.createAttributeAndLink(name, positive, targetType, targetId)
            }
        )
    }
}

@Composable
private fun AddAttributeDialog(
    defs: List<AttributeDef>,
    linkedIds: Set<Long>,
    onDismiss: () -> Unit,
    onPick: (AttributeDef) -> Unit,
    onCreate: (String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val free = defs.filter { it.id !in linkedIds }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Attribut hinzufügen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    name, { name = it.take(40) },
                    label = { Text("Neues Attribut, z. B. Kopfballstark") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = name.isNotBlank(),
                        onClick = { onCreate(name.trim(), true); name = "" }
                    ) { Text("＋ Positiv") }
                    OutlinedButton(
                        enabled = name.isNotBlank(),
                        onClick = { onCreate(name.trim(), false); name = "" }
                    ) { Text("− Negativ") }
                }
                if (free.isNotEmpty()) {
                    Text("Aus der Datenbank:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                }
                free.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(d) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (d.positive) "＋" else "−",
                            color = if (d.positive) PositiveColor else NegativeColor,
                            modifier = Modifier.width(28.dp)
                        )
                        Text(d.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fertig") } }
    )
}

/** Gespeicherte Analysen zu einem Spieler oder Verein, die bei späteren Spielen wieder abrufbar sind. */
@Composable
fun AnalysisSection(vm: MainViewModel, targetType: String, targetId: Long) {
    val all by vm.analyses.collectAsStateWithLifecycle()
    val mine = all.filter { it.targetType == targetType && it.targetId == targetId }
    var editing by remember { mutableStateOf<Analysis?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Analysen", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { creating = true }) { Text("+ Analyse") }
        }
        if (mine.isEmpty()) {
            Text("Noch keine Analysen. Gespeicherte Analysen kannst du bei späteren Spielen wieder abrufen.", style = MaterialTheme.typography.bodySmall)
        }
        mine.forEach { a ->
            Card(Modifier.fillMaxWidth().clickable { editing = a }) {
                Column(Modifier.padding(12.dp)) {
                    Text(a.title, style = MaterialTheme.typography.titleSmall)
                    Text(formatDate(a.createdAt), style = MaterialTheme.typography.bodySmall)
                    if (a.text.isNotBlank()) Text(a.text, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    if (creating || editing != null) {
        AnalysisDialog(
            initial = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { title, text ->
                val base = editing ?: Analysis(targetType = targetType, targetId = targetId, title = "", text = "")
                vm.saveAnalysis(base.copy(title = title, text = text))
                creating = false; editing = null
            },
            onDelete = editing?.let { a -> { vm.deleteAnalysis(a); editing = null } }
        )
    }
}

@Composable
private fun AnalysisDialog(
    initial: Analysis?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: (() -> Unit)?
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var text by remember { mutableStateOf(initial?.text ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Neue Analyse" else "Analyse bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    title, { title = it }, label = { Text("Titel (z. B. Spiel am 12.10.)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    text, { text = it }, label = { Text("Analyse") },
                    minLines = 5, modifier = Modifier.fillMaxWidth()
                )
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Analyse löschen") }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = { onSave(title.trim(), text.trim()) }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

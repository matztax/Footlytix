@file:OptIn(ExperimentalMaterial3Api::class)

package de.tactics.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import de.tactics.app.MainViewModel
import de.tactics.app.data.Club
import de.tactics.app.data.MARKER_TYPES
import de.tactics.app.data.Player
import de.tactics.app.data.Scribble
import de.tactics.app.data.VideoAnnotation
import de.tactics.app.data.VideoItem
import de.tactics.app.data.VideoMarker
import de.tactics.app.data.decodeScribbles
import de.tactics.app.data.encodeScribbles

internal fun fmt(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

/** true, wenn die App die Videodatei (noch) öffnen darf. */
internal fun isVideoReadable(ctx: Context, uri: String): Boolean = runCatching {
    ctx.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false
}.getOrDefault(false)

private fun displayName(ctx: Context, uri: Uri): String {
    val name = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
    return name?.substringBeforeLast('.')?.ifBlank { null } ?: "Neues Video"
}

/** Bibliothek: mehrere Videos, jeweils mit eigenem Namen. */
@Composable
fun VideoListScreen(vm: MainViewModel, onOpen: (Long) -> Unit) {
    val ctx = LocalContext.current
    val videos by vm.videos.collectAsStateWithLifecycle()
    val annotations by vm.annotations.collectAsStateWithLifecycle()
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var renaming by remember { mutableStateOf<VideoItem?>(null) }
    var deleting by remember { mutableStateOf<VideoItem?>(null) }
    var relinking by remember { mutableStateOf<VideoItem?>(null) }

    // Videos, deren Datei nicht mehr lesbar ist (z. B. nach Wiederherstellung auf einem neuen Gerät)
    val missing by produceState(emptySet<Long>(), videos) {
        value = withContext(Dispatchers.IO) {
            videos.filter { !isVideoReadable(ctx, it.uri) }.map { it.id }.toSet()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            pendingUri = uri
        }
    }

    val relinkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = relinking
        relinking = null
        if (uri != null && target != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            vm.relinkVideo(target, uri.toString())
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (videos.isEmpty()) {
            Text(
                "Noch keine Videos.\nMit + ein Video vom Gerät hinzufügen und benennen. Die Datei bleibt auf dem Gerät, die App speichert nur den Verweis.",
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            items(videos, key = { it.id }) { v ->
                val scenes = annotations.count { it.videoUri == v.uri }
                val date = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY)
                    .format(java.util.Date(v.createdAt))
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clickable { onOpen(v.id) }
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(v.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "$scenes Szenen · hinzugefügt am $date",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (v.id in missing) {
                            Text(
                                "Videodatei nicht gefunden. Szenen und Marker sind noch da – verknüpfe die Datei neu.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (v.id in missing) {
                                TextButton(onClick = {
                                    relinking = v
                                    relinkPicker.launch(arrayOf("video/*"))
                                }) { Text("Neu verknüpfen") }
                            }
                            TextButton(onClick = { renaming = v }) { Text("Umbenennen") }
                            TextButton(onClick = { deleting = v }) { Text("Löschen") }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { picker.launch(arrayOf("video/*")) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "Video hinzufügen") }
    }

    pendingUri?.let { uri ->
        NameDialog(
            title = "Video benennen",
            initial = displayName(ctx, uri),
            onDismiss = { pendingUri = null },
            onSave = { name ->
                vm.saveVideo(VideoItem(name = name, uri = uri.toString()))
                pendingUri = null
            }
        )
    }
    renaming?.let { v ->
        NameDialog(
            title = "Video umbenennen",
            initial = v.name,
            onDismiss = { renaming = null },
            onSave = { name -> vm.saveVideo(v.copy(name = name)); renaming = null }
        )
    }
    deleting?.let { v ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Video entfernen?") },
            text = { Text("„${v.name}“ wird aus der Liste entfernt, ebenso die gespeicherten Szenen dazu. Die Videodatei auf dem Gerät bleibt erhalten.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteVideo(v); deleting = null }) { Text("Entfernen") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Abbrechen") } }
        )
    }
}

/** Tab "Video": Bibliothek und Clip-Übersicht. */
@Composable
fun VideoHost(vm: MainViewModel, onOpen: (Long, Long, Long) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Videos") })
            FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Clips") })
        }
        Box(Modifier.weight(1f)) {
            if (mode == 0) VideoListScreen(vm) { id -> onOpen(id, -1L, 0L) } else ClipsScreen(vm, onOpen)
        }
    }
}

/** Alle Marker über alle Videos, filterbar nach Art, Verein und Person (eigene Spieler und Gegner). */
@Composable
private fun ClipsScreen(vm: MainViewModel, onOpen: (Long, Long, Long) -> Unit) {
    val markers by vm.markers.collectAsStateWithLifecycle()
    val videos by vm.videos.collectAsStateWithLifecycle()
    val players by vm.players.collectAsStateWithLifecycle()
    val clubs by vm.clubs.collectAsStateWithLifecycle()
    var typeFilter by rememberSaveable { mutableStateOf("") }
    var clubFilter by rememberSaveable { mutableStateOf(-1L) }
    var playerFilter by rememberSaveable { mutableStateOf(-1L) }

    val marked = markers.mapNotNull { m -> players.firstOrNull { it.id == m.playerId } }.distinctBy { it.id }
    val clubIds = marked.map { it.clubId }.distinct()
    val filtered = markers.filter { m ->
        val pl = players.firstOrNull { it.id == m.playerId }
        (typeFilter.isEmpty() || m.type == typeFilter) &&
            (clubFilter == -1L || (pl != null && pl.clubId == clubFilter)) &&
            (playerFilter == -1L || m.playerId == playerFilter)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = typeFilter.isEmpty(), onClick = { typeFilter = "" }, label = { Text("Alle Arten") })
            MARKER_TYPES.forEach { t ->
                FilterChip(selected = typeFilter == t, onClick = { typeFilter = t }, label = { Text(t) })
            }
        }
        if (clubIds.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = clubFilter == -1L, onClick = { clubFilter = -1L; playerFilter = -1L }, label = { Text("Alle Vereine") })
                clubIds.forEach { cid ->
                    val name = clubs.firstOrNull { it.id == cid }?.name ?: "Vereinslos"
                    FilterChip(selected = clubFilter == cid, onClick = { clubFilter = cid; playerFilter = -1L }, label = { Text(name) })
                }
            }
        }
        if (clubFilter != -1L) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = playerFilter == -1L, onClick = { playerFilter = -1L }, label = { Text("Alle Spieler") })
                marked.filter { it.clubId == clubFilter }.sortedBy { it.number }.forEach { p ->
                    FilterChip(
                        selected = playerFilter == p.id,
                        onClick = { playerFilter = p.id },
                        label = { Text("${p.number} ${p.name}") }
                    )
                }
            }
        }
        Box(Modifier.weight(1f)) {
            if (filtered.isEmpty()) {
                Text(
                    "Keine Clips. Setze im Videoplayer Marker, dann erscheinen sie hier.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(filtered, key = { it.id }) { m ->
                    val pl = players.firstOrNull { it.id == m.playerId }
                    val vname = videos.firstOrNull { it.id == m.videoId }?.name ?: "Video"
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                            onOpen(m.videoId, m.timestampMs, if (m.lengthMs > 0) m.timestampMs + m.lengthMs else 0L)
                        }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(m.type + " · " + fmt(m.timestampMs) + (if (m.lengthMs > 0) " · " + (m.lengthMs / 1000) + " s" else ""), style = MaterialTheme.typography.titleSmall)
                            Text(vname, style = MaterialTheme.typography.bodySmall)
                            if (pl != null) Text("${pl.number} ${pl.name}", style = MaterialTheme.typography.bodySmall)
                            if (m.note.isNotBlank()) Text(m.note)
                        }
                    }
                }
            }
        }
    }
}

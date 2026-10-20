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

/** Ein einzelnes Video abspielen und Szenen mit Zeichnungen markieren. */
@Composable
fun VideoPlayerScreen(vm: MainViewModel, videoId: Long, seekMs: Long, endMs: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val videos by vm.videos.collectAsStateWithLifecycle()
    val annotations by vm.annotations.collectAsStateWithLifecycle()
    val markers by vm.markers.collectAsStateWithLifecycle()
    val players by vm.players.collectAsStateWithLifecycle()
    val clubs by vm.clubs.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val video = videos.firstOrNull { it.id == videoId }
    val uri = video?.uri

    var drawMode by remember { mutableStateOf(false) }
    var scribbles by remember { mutableStateOf<List<Scribble>>(emptyList()) }
    var stamp by remember { mutableLongStateOf(0L) }
    var note by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf(false) }
    var markerType by remember { mutableStateOf<String?>(null) }
    var markerTime by remember { mutableLongStateOf(0L) }
    var clipJob by remember { mutableStateOf<Job?>(null) }

    val exo = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(Unit) { onDispose { exo.release() } }

    // Spielt ab [start] und pausiert bei [end] (end = 0: läuft einfach weiter)
    fun playClip(start: Long, end: Long) {
        clipJob?.cancel()
        exo.seekTo(start)
        exo.play()
        if (end > start) {
            clipJob = scope.launch {
                while (isActive && exo.currentPosition < end) delay(100)
                exo.pause()
            }
        }
    }

    LaunchedEffect(uri) {
        if (uri != null) {
            exo.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
            exo.prepare()
            if (seekMs >= 0) playClip(seekMs, endMs)
        }
    }

    if (video == null || uri == null) {
        Column(Modifier.padding(16.dp)) {
            TextButton(onClick = onBack) { Text("← Zurück") }
            Text("Video nicht gefunden.")
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Zurück") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { renaming = true }) { Text("Umbenennen") }
        }
        Text(video.name, style = MaterialTheme.typography.titleLarge)

        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
            AndroidView(
                factory = { c -> PlayerView(c).apply { this.player = exo } },
                modifier = Modifier.fillMaxSize()
            )
            if (drawMode || scribbles.isNotEmpty()) {
                DrawingOverlay(
                    scribbles = scribbles,
                    enabled = drawMode,
                    onFinished = { scribbles = scribbles + it },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Text(
            "Pausiere an einer Stelle, tippe auf „Zeichnen“ und male Laufwege oder Räume ins Bild.",
            style = MaterialTheme.typography.bodySmall
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!drawMode) {
                Button(onClick = { exo.pause(); stamp = exo.currentPosition; drawMode = true }) { Text("Zeichnen") }
            } else {
                Button(onClick = { drawMode = false }) { Text("Fertig") }
            }
            OutlinedButton(
                enabled = scribbles.isNotEmpty(),
                onClick = { scribbles = scribbles.dropLast(1) }
            ) { Text("Zurück") }
            OutlinedButton(onClick = { scribbles = emptyList(); drawMode = false }) { Text("Ausblenden") }
        }

        if (scribbles.isNotEmpty()) {
            OutlinedTextField(
                note, { note = it },
                label = { Text("Notiz zur Szene") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = {
                vm.saveAnnotation(
                    VideoAnnotation(
                        videoUri = uri,
                        timestampMs = stamp,
                        scribblesJson = encodeScribbles(scribbles),
                        note = note.trim()
                    )
                )
                note = ""
                scribbles = emptyList()
                drawMode = false
            }) { Text("Zeichnung bei ${fmt(stamp)} speichern") }
        }

        val mine = annotations.filter { it.videoUri == uri }
        if (mine.isNotEmpty()) {
            Text("Gespeicherte Szenen", style = MaterialTheme.typography.titleMedium)
            mine.forEach { a ->
                Card(
                    Modifier.fillMaxWidth().clickable {
                        exo.pause()
                        exo.seekTo(a.timestampMs)
                        stamp = a.timestampMs
                        scribbles = decodeScribbles(a.scribblesJson)
                        drawMode = false
                    }
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(fmt(a.timestampMs), style = MaterialTheme.typography.titleSmall)
                            if (a.note.isNotBlank()) Text(a.note)
                        }
                        IconButton(onClick = { vm.deleteAnnotation(a) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Löschen")
                        }
                    }
                }
            }
        }

        Text("Marker & Clips", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tippe während des Videos auf eine Art, um die aktuelle Stelle zu markieren, optional mit Person und Länge als Clip.",
            style = MaterialTheme.typography.bodySmall
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MARKER_TYPES.forEach { t ->
                AssistChip(
                    onClick = { markerTime = exo.currentPosition; exo.pause(); markerType = t },
                    label = { Text(t) }
                )
            }
        }
        markers.filter { it.videoId == video.id }.forEach { m ->
            val pl = players.firstOrNull { it.id == m.playerId }
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            fmt(m.timestampMs) + " · " + m.type + (if (m.lengthMs > 0) " · Clip " + (m.lengthMs / 1000) + " s" else ""),
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (pl != null) {
                            Text(
                                "${pl.number} ${pl.name} (" + (clubs.firstOrNull { it.id == pl.clubId }?.name ?: "vereinslos") + ")",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (m.note.isNotBlank()) Text(m.note)
                    }
                    TextButton(onClick = {
                        playClip(m.timestampMs, if (m.lengthMs > 0) m.timestampMs + m.lengthMs else 0L)
                    }) { Text("▶") }
                    TextButton(onClick = { vm.deleteMarker(m) }) { Text("✕") }
                }
            }
        }
    }

    if (renaming) {
        NameDialog(
            title = "Video umbenennen",
            initial = video.name,
            onDismiss = { renaming = false },
            onSave = { name -> vm.saveVideo(video.copy(name = name)); renaming = false }
        )
    }

    markerType?.let { type ->
        MarkerDialog(
            type = type,
            players = players,
            clubs = clubs,
            onDismiss = { markerType = null },
            onSave = { pid, len, note ->
                vm.saveMarker(
                    VideoMarker(videoId = video.id, timestampMs = markerTime, lengthMs = len, type = type, playerId = pid, note = note)
                )
                markerType = null
            }
        )
    }
}

@Composable
private fun MarkerDialog(
    type: String,
    players: List<Player>,
    clubs: List<Club>,
    onDismiss: () -> Unit,
    onSave: (Long, Long, String) -> Unit
) {
    var playerId by remember { mutableStateOf(0L) }
    var lengthSec by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Marker: $type") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Länge", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 5, 10, 20, 30).forEach { s ->
                        FilterChip(
                            selected = lengthSec == s,
                            onClick = { lengthSec = s },
                            label = { Text(if (s == 0) "Punkt" else "$s s") }
                        )
                    }
                }
                OutlinedTextField(
                    note, { note = it }, label = { Text("Notiz (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Person (optional)", style = MaterialTheme.typography.labelLarge)
                Text(
                    (if (playerId == 0L) "✓ " else "") + "keine Person",
                    modifier = Modifier.fillMaxWidth().clickable { playerId = 0L }.padding(vertical = 10.dp)
                )
                players.sortedWith(compareBy<Player>({ it.clubId }, { it.number })).forEach { p ->
                    val club = clubs.firstOrNull { it.id == p.clubId }?.name ?: "vereinslos"
                    Text(
                        (if (playerId == p.id) "✓ " else "") + "${p.number} ${p.name} · $club",
                        modifier = Modifier.fillMaxWidth().clickable { playerId = p.id }.padding(vertical = 10.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(playerId, lengthSec * 1000L, note.trim()) }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

/** Zeichenfläche über dem Video; Striche sind normiert (0..1) und damit größenunabhängig. */
@Composable
fun DrawingOverlay(
    scribbles: List<Scribble>,
    enabled: Boolean,
    onFinished: (Scribble) -> Unit,
    modifier: Modifier = Modifier
) {
    var current by remember { mutableStateOf<List<Float>>(emptyList()) }
    val finished by rememberUpdatedState(onFinished)

    val gesture = if (enabled) {
        Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { o -> current = listOf(o.x / size.width, o.y / size.height) },
                onDrag = { change, _ ->
                    change.consume()
                    val p = change.position
                    current = current + listOf(p.x / size.width, p.y / size.height)
                },
                onDragEnd = {
                    if (current.size >= 4) finished(Scribble(current))
                    current = emptyList()
                },
                onDragCancel = { current = emptyList() }
            )
        }
    } else Modifier

    Canvas(modifier.then(gesture)) {
        fun drawPoints(pts: List<Float>) {
            var i = 0
            while (i + 3 < pts.size) {
                drawLine(
                    color = Color(0xFFFFEB3B),
                    start = Offset(pts[i] * size.width, pts[i + 1] * size.height),
                    end = Offset(pts[i + 2] * size.width, pts[i + 3] * size.height),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
                i += 2
            }
        }
        scribbles.forEach { drawPoints(it.points) }
        drawPoints(current)
    }
}

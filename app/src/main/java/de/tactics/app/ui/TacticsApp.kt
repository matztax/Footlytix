@file:OptIn(ExperimentalMaterial3Api::class)

package de.tactics.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.tactics.app.AutoBackup
import de.tactics.app.MainViewModel
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import de.tactics.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("db", "Datenbank", Icons.Default.AccountBox),
    Tab("matches", "Spiele", Icons.Default.Star),
    Tab("tactics", "Taktik", Icons.Default.Place),
    Tab("training", "Training", Icons.Default.DateRange),
    Tab("video", "Video", Icons.Default.PlayArrow)
)

@Composable
fun TacticsApp(vm: MainViewModel = viewModel()) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tabRoute = when {
        route == null -> null
        route.startsWith("player") || route.startsWith("club") -> "db"
        route.startsWith("video") -> "video"
        route.startsWith("match") || route.startsWith("live") -> "matches"
        route.startsWith("import") -> "db"
        route.startsWith("exercise") || route.startsWith("session") -> "training"
        else -> route
    }
    var menuOpen by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            val msg = try {
                // Mit Backup-Passwort verschlüsselt, sonst Klartext-JSON
                val text = vm.exportForSharing()
                withContext(Dispatchers.IO) {
                    val out = ctx.contentResolver.openOutputStream(uri) ?: error("Datei konnte nicht geöffnet werden")
                    out.use { it.write(text.toByteArray()) }
                }
                "Backup gespeichert"
            } catch (e: Exception) {
                "Export fehlgeschlagen: ${e.message}"
            }
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }

    var restoreRequest by remember { mutableStateOf<RestoreRequest?>(null) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "gewählte Datei"
            restoreRequest = RestoreRequest(name) {
                val stream = ctx.contentResolver.openInputStream(uri) ?: error("Datei konnte nicht geöffnet werden")
                stream.bufferedReader().use { it.readText() }
            }
        }
    }
    RestoreFlow(vm, restoreRequest, onClose = { restoreRequest = null })

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.logo_emblem),
                            contentDescription = "Futlytix",
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = Color.White)) { append("Fut") }
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("lytix") }
                            },
                            fontWeight = FontWeight.ExtraBold,
                            fontStyle = FontStyle.Italic
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menü")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Import (CSV / Kalender)") },
                            onClick = { menuOpen = false; nav.navigate("import") }
                        )
                        DropdownMenuItem(
                            text = { Text("Sicherheit & Backup") },
                            onClick = { menuOpen = false; nav.navigate("settings") }
                        )
                        DropdownMenuItem(
                            text = { Text("Backup exportieren") },
                            onClick = { menuOpen = false; exportLauncher.launch("futlytix-backup-" + AutoBackup.today() + ".json") }
                        )
                        DropdownMenuItem(
                            text = { Text("Backup importieren") },
                            onClick = { menuOpen = false; importLauncher.launch(arrayOf("*/*")) }
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = tabRoute == t.route,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) }
                    )
                }
            }
        }
    ) { pad ->
        NavHost(
            nav,
            startDestination = "db",
            modifier = Modifier.padding(pad).consumeWindowInsets(pad).imePadding()
        ) {
            composable("db") {
                DatabaseHost(
                    vm,
                    onOpenPlayer = { id -> nav.navigate("player/$id") },
                    onOpenClub = { id -> nav.navigate("club/$id") }
                )
            }
            composable(
                "player/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { e ->
                PlayerDetailScreen(vm, e.arguments?.getLong("id") ?: 0L) { nav.popBackStack() }
            }
            composable(
                "club/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { e ->
                ClubDetailScreen(
                    vm,
                    e.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onOpenPlayer = { id -> nav.navigate("player/$id") }
                )
            }
            composable("tactics") { TacticsHost(vm) }
            composable("training") {
                TrainingHost(
                    vm,
                    onOpenExercise = { id -> nav.navigate("exercise/$id") },
                    onOpenSession = { id -> nav.navigate("session/$id") }
                )
            }
            composable(
                "exercise/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { e ->
                ExerciseEditorScreen(vm, e.arguments?.getLong("id") ?: 0L) { nav.popBackStack() }
            }
            composable(
                "session/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { e ->
                SessionEditorScreen(vm, e.arguments?.getLong("id") ?: 0L) { nav.popBackStack() }
            }
            composable("video") {
                VideoHost(vm) { id, seek, end -> nav.navigate("video/$id?seek=$seek&end=$end") }
            }
            composable(
                "video/{id}?seek={seek}&end={end}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("seek") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("end") { type = NavType.LongType; defaultValue = 0L }
                )
            ) { e ->
                VideoPlayerScreen(
                    vm,
                    e.arguments?.getLong("id") ?: 0L,
                    e.arguments?.getLong("seek") ?: -1L,
                    e.arguments?.getLong("end") ?: 0L
                ) { nav.popBackStack() }
            }
            composable("matches") { MatchesHost(vm) { id -> nav.navigate("match/$id") } }
            composable(
                "match/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { e ->
                val mid = e.arguments?.getLong("id") ?: 0L
                MatchDetailScreen(vm, mid, onLive = { nav.navigate("live/$mid") }, onBack = { nav.popBackStack() })
            }
            composable(
                "live/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { e ->
                LiveMatchScreen(vm, e.arguments?.getLong("id") ?: 0L) { nav.popBackStack() }
            }
            composable("import") { ImportScreen(vm) { nav.popBackStack() } }
            composable("settings") { SettingsScreen(vm) { nav.popBackStack() } }
        }
    }
}

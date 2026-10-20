@file:OptIn(ExperimentalMaterial3Api::class)

package de.tactics.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.tactics.app.MainViewModel

/** Tab "Taktik": Aufstellung, Standards und Briefing. */
@Composable
fun TacticsHost(vm: MainViewModel) {
    var mode by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Aufstellung") })
            FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Standards") })
            FilterChip(selected = mode == 2, onClick = { mode = 2 }, label = { Text("Briefing") })
        }
        Box(Modifier.weight(1f)) {
            when (mode) {
                0 -> FormationScreen(vm)
                1 -> SetPieceScreen(vm)
                else -> BriefingScreen(vm)
            }
        }
    }
}

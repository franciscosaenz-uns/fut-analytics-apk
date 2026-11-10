package com.futbolanalisis.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.futbolanalisis.app.UiState
import com.futbolanalisis.app.ui.dateTime
import com.futbolanalisis.app.ui.km
import com.futbolanalisis.app.ui.minutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: UiState,
    snackbar: SnackbarHostState,
    onImportFile: () -> Unit,
    onSample: () -> Unit,
    onOpen: (String) -> Unit,
    onHealth: () -> Unit,
    onProfile: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fútbol Análisis") },
                actions = {
                    IconButton(onClick = onHealth) { Icon(Icons.Filled.Favorite, contentDescription = "Health Connect") }
                    IconButton(onClick = onProfile) { Icon(Icons.Filled.Person, contentDescription = "Perfil") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onImportFile, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Importar partido") })
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.matches.isEmpty()) {
                EmptyState(onImportFile, onSample, onHealth)
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.matches, key = { it.id }) { m ->
                        Card(Modifier.fillMaxWidth().clickable { onOpen(m.id) }) {
                            Column(Modifier.padding(16.dp)) {
                                Text(m.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(m.startMs.dateTime(), style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text(m.distanceM.km())
                                    Text(m.playingTimeS.minutes())
                                    Text(m.role, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                    item {
                        OutlinedButton(onClick = onSample, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 72.dp)) {
                            Text("Agregar partido de ejemplo")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onImportFile: () -> Unit, onSample: () -> Unit, onHealth: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Analiza tu partido", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "1. En Zepp, abre el entrenamiento del partido y expórtalo como GPX (o descárgalo desde Strava).\n" +
                "2. Impórtalo aquí o compártelo directamente con esta app.\n" +
                "3. Conecta Health Connect para sumar frecuencia cardiaca, HRV, SpO2, VO2 máx y pasos.",
            style = MaterialTheme.typography.bodyMedium,
        )
        FilledTonalButton(onClick = onImportFile, modifier = Modifier.fillMaxWidth()) { Text("Importar archivo GPX / TCX") }
        OutlinedButton(onClick = onHealth, modifier = Modifier.fillMaxWidth()) { Text("Importar desde Health Connect") }
        OutlinedButton(onClick = onSample, modifier = Modifier.fillMaxWidth()) { Text("Probar con un partido de ejemplo") }
    }
}

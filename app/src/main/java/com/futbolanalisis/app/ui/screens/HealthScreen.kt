package com.futbolanalisis.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.futbolanalisis.app.UiState
import com.futbolanalisis.app.health.HcAvailability
import com.futbolanalisis.app.health.HcSession
import com.futbolanalisis.app.ui.components.StatRow
import com.futbolanalisis.app.ui.dateTime
import com.futbolanalisis.app.ui.fmt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(
    state: UiState,
    onRequestPermissions: () -> Unit,
    onInstall: () -> Unit,
    onRefresh: () -> Unit,
    onImportSession: (HcSession) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Health Connect") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            actions = { TextButton(onClick = onRefresh) { Text("Actualizar") } },
        )
    }) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item {
                when (state.hcAvailability) {
                    HcAvailability.NOT_SUPPORTED -> Text("Este teléfono no es compatible con Health Connect (requiere Android 9 o superior).")
                    HcAvailability.NOT_INSTALLED, HcAvailability.UPDATE_REQUIRED -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Health Connect no está instalado o necesita actualizarse.")
                        Button(onClick = onInstall) { Text("Instalar / actualizar Health Connect") }
                    }
                    HcAvailability.AVAILABLE -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "En la app Zepp: Perfil → Agregar cuentas → Health Connect, y activa la sincronización. " +
                                "Luego concede aquí los permisos de lectura.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (!state.hcGranted) {
                            Button(onClick = onRequestPermissions, modifier = Modifier.fillMaxWidth()) { Text("Conceder permisos") }
                        } else {
                            Text("Permisos concedidos ✓", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            state.wellness?.let { w ->
                item { Text("Estado físico actual", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp)) }
                item {
                    StatRow(
                        Triple("HRV (RMSSD)", w.hrvRmssdMs?.let { "${it.fmt(0)} ms" } ?: "—", w.hrvBaselineMs?.let { "media 7 d: ${it.fmt(0)} ms" }),
                        Triple("Recuperación", w.readiness?.label ?: "—", if (w.readiness == null) "Se necesitan 4+ días de HRV" else null),
                    )
                }
                item {
                    StatRow(
                        Triple("FC reposo", w.restingHr?.let { "$it lpm" } ?: "—", null),
                        Triple("SpO2", w.spo2Pct?.let { "${it.fmt(0)} %" } ?: "—", null),
                    )
                }
                item {
                    StatRow(
                        Triple("VO2 máx", w.vo2Max?.let { "${it.fmt(1)}" } ?: "—", "ml/kg/min"),
                        Triple("Pasos hoy", w.stepsToday?.toString() ?: "—", null),
                    )
                }
                item {
                    Text(
                        "Un guion (—) significa que Zepp no comparte ese dato con Health Connect en tu teléfono.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.sessions.isNotEmpty()) {
                item { Text("Entrenamientos (últimos 30 días)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp)) }
                items(state.sessions, key = { it.id }) { s ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.title ?: if (s.isSoccer) "Fútbol" else "Entrenamiento", fontWeight = FontWeight.SemiBold)
                                Text(s.start.toEpochMilli().dateTime(), style = MaterialTheme.typography.bodySmall)
                                val mins = (s.end.toEpochMilli() - s.start.toEpochMilli()) / 60000
                                Text("$mins min · ${s.origin}", style = MaterialTheme.typography.bodySmall)
                            }
                            FilledTonalButton(onClick = { onImportSession(s) }) { Text("Analizar") }
                        }
                    }
                }
            } else if (state.hcGranted) {
                item { Text("No hay entrenamientos en Health Connect en los últimos 30 días.", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

package com.futbolanalisis.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.futbolanalisis.app.UiState
import com.futbolanalisis.app.data.MatchSummary
import com.futbolanalisis.app.ui.components.PitchCanvas
import com.futbolanalisis.app.ui.dateTime
import com.futbolanalisis.app.ui.fmt
import com.futbolanalisis.app.ui.km
import com.futbolanalisis.app.ui.minutes
import com.futbolanalisis.app.ui.theme.PitchGreen
import com.futbolanalisis.app.ui.theme.PitchGreenDark
import com.futbolanalisis.core.pitch.PitchDimensions

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
        floatingActionButton = {
            if (state.matches.isNotEmpty()) {
                ExtendedFloatingActionButton(onClick = onImportFile, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Importar partido") })
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Hero(state, onHealth, onProfile, onImportFile, onSample) }
            if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
            if (state.matches.isEmpty()) {
                item { ZeppHowTo(Modifier.padding(horizontal = 16.dp), onHealth) }
            } else {
                item {
                    Text("Tus partidos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp))
                }
                items(state.matches, key = { it.id }) { m -> MatchCard(m, Modifier.padding(horizontal = 16.dp)) { onOpen(m.id) } }
                item { ZeppHowTo(Modifier.padding(horizontal = 16.dp), onHealth, compact = true) }
                item {
                    TextButton(onClick = onSample, modifier = Modifier.fillMaxWidth().navigationBarsPadding()) { Text("Agregar el partido de ejemplo") }
                }
            }
        }
    }
}

@Composable
private fun Hero(state: UiState, onHealth: () -> Unit, onProfile: () -> Unit, onImport: () -> Unit, onSample: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brush.verticalGradient(listOf(PitchGreenDark, PitchGreen))),
    ) {
        // Líneas de cancha de fondo, muy sutiles.
        Canvas(Modifier.matchParentSize()) {
            val c = Color.White.copy(alpha = 0.08f)
            drawLine(c, Offset(size.width * 0.62f, 0f), Offset(size.width * 0.62f, size.height), 4f)
            drawCircle(c, size.height * 0.28f, Offset(size.width * 0.62f, size.height * 0.55f), style = Stroke(4f))
            drawRect(c, Offset(size.width * 0.86f, size.height * 0.25f), androidx.compose.ui.geometry.Size(size.width * 0.2f, size.height * 0.6f), style = Stroke(4f))
        }
        Column(Modifier.statusBarsPadding().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(Color.White.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                    Text("⚽", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.width(10.dp))
                Text("Fútbol Análisis", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onHealth) { Icon(Icons.Filled.Favorite, "Health Connect", tint = Color.White) }
                IconButton(onClick = onProfile) { Icon(Icons.Filled.Person, "Perfil", tint = Color.White) }
            }
            Spacer(Modifier.height(18.dp))
            if (state.matches.isEmpty()) {
                Text("Tu partido, analizado\ncomo un profesional", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Mapa de calor, rol táctico, sprints, energía y alertas minuto a minuto con los datos de tu reloj.",
                    color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = onImport) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Importar partido") }
                    OutlinedButton(onClick = onSample) {
                        Icon(Icons.Filled.PlayArrow, null, tint = Color.White); Spacer(Modifier.width(6.dp)); Text("Ver ejemplo", color = Color.White)
                    }
                }
            } else {
                val km = state.matches.sumOf { it.distanceM }
                val sprints = state.matches.sumOf { it.sprints }
                val top = state.matches.maxOf { it.maxSpeedKmh }
                Text("Hola, ${state.profile.name}", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    HeroStat("${state.matches.size}", "partidos")
                    HeroStat(km.km(), "recorridos")
                    HeroStat("$sprints", "sprints")
                    HeroStat("${top.fmt(1)}", "km/h máx")
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String) {
    Column {
        Text(value, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ZeppHowTo(modifier: Modifier, onHealth: () -> Unit, compact: Boolean = false) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Share, null)
                Spacer(Modifier.width(8.dp))
                Text("Sube tu partido desde Zepp", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (!compact) {
                Step(1, "Abre la app Zepp y entra al entrenamiento de tu partido.")
                Step(2, "Toca Compartir (o ⋯ → Exportar) y elige el archivo GPX.")
                Step(3, "En la lista de apps elige Fútbol Análisis: el partido se importa y se analiza al instante.")
                Text(
                    "También funciona con archivos GPX/TCX de Strava, desde el gestor de archivos o con el botón Importar.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onHealth) { Text("Conectar Health Connect para FC, HRV, SpO2 y VO2 máx →") }
            } else {
                Text(
                    "En Zepp abre el partido, toca Compartir/Exportar GPX y elige Fútbol Análisis.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(26.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
            Text("$n", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun MatchCard(m: MatchSummary, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.fillMaxWidth().clickable(onClick = onClick), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(118.dp).clip(RoundedCornerShape(10.dp))) {
                PitchCanvas(m.heatmap?.dims ?: PitchDimensions(), heatmap = m.heatmap)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(m.startMs.dateTime(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(m.archetype.ifBlank { m.role }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                Text("${m.distanceM.km()} · ${m.playingTimeS.minutes()} · ${m.sprints} sprints", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

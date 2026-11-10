package com.futbolanalisis.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.futbolanalisis.app.data.MatchRecord
import com.futbolanalisis.app.ui.components.SectionTitle
import com.futbolanalisis.core.coach.MatchLog

private val rpeText = listOf(
    "Reposo", "Muy, muy suave", "Muy suave", "Moderado", "Algo duro", "Duro", "Duro+", "Muy duro", "Muy duro+", "Casi máximo", "Máximo",
)
private val surfaces = listOf("Césped natural", "Césped sintético", "Tierra", "Fútbol sala")
private val feelings = listOf("Muy mal", "Mal", "Normal", "Bien", "Muy bien")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(record: MatchRecord?, onSave: (MatchLog) -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Registro del partido") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
        )
    }) { padding ->
        if (record == null) return@Scaffold
        var log by remember(record.id) { mutableStateOf(record.log.copy(rpe = record.log.rpe ?: record.options.rpe)) }
        val number = KeyboardOptions(keyboardType = KeyboardType.Number)
        Column(Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            SectionTitle("Partido")
            OutlinedTextField(log.opponent, { log = log.copy(opponent = it) }, label = { Text("Rival") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    log.goalsFor?.toString() ?: "", { log = log.copy(goalsFor = it.filter(Char::isDigit).toIntOrNull()) },
                    label = { Text("Mi equipo") }, keyboardOptions = number, singleLine = true, modifier = Modifier.weight(1f),
                )
                Text("–", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    log.goalsAgainst?.toString() ?: "", { log = log.copy(goalsAgainst = it.filter(Char::isDigit).toIntOrNull()) },
                    label = { Text("Rival") }, keyboardOptions = number, singleLine = true, modifier = Modifier.weight(1f),
                )
            }
            Text("Posición en la que jugaste", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MatchLog.POSITIONS.forEach { pos ->
                    FilterChip(log.positionPlayed == pos, { log = log.copy(positionPlayed = if (log.positionPlayed == pos) "" else pos) }, label = { Text(pos) })
                }
            }
            Text("Superficie", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                surfaces.forEach { sf -> FilterChip(log.surface == sf, { log = log.copy(surface = if (log.surface == sf) "" else sf) }, label = { Text(sf) }) }
            }

            SectionTitle("Tus acciones")
            Stepper("Goles", log.goals) { log = log.copy(goals = it) }
            Stepper("Asistencias", log.assists) { log = log.copy(assists = it) }
            Stepper("Tiros", log.shots) { log = log.copy(shots = it, shotsOnTarget = log.shotsOnTarget.coerceAtMost(it)) }
            Stepper("Tiros al arco", log.shotsOnTarget) { log = log.copy(shotsOnTarget = it, shots = log.shots.coerceAtLeast(it)) }
            Stepper("Pases clave", log.keyPasses) { log = log.copy(keyPasses = it) }
            Stepper("Recuperaciones", log.recoveries) { log = log.copy(recoveries = it) }
            Stepper("Duelos ganados", log.duelsWon) { log = log.copy(duelsWon = it) }
            Stepper("Duelos perdidos", log.duelsLost) { log = log.copy(duelsLost = it) }
            Stepper("Tarjetas amarillas", log.yellowCards, max = 2) { log = log.copy(yellowCards = it) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tarjeta roja", Modifier.weight(1f))
                Switch(log.redCard, { log = log.copy(redCard = it) })
            }

            SectionTitle("Cómo te sentiste")
            val rpe = log.rpe ?: 0
            Text("Esfuerzo percibido (RPE): ${if (rpe == 0) "sin indicar" else "$rpe/10 · ${rpeText[rpe]}"}")
            Slider(rpe.toFloat(), { log = log.copy(rpe = it.toInt().takeIf { v -> v > 0 }) }, valueRange = 0f..10f, steps = 9)
            val rating = log.selfRating ?: 0
            Text("Autoevaluación: ${if (rating == 0) "sin indicar" else "$rating/10"}")
            Slider(rating.toFloat(), { log = log.copy(selfRating = it.toInt().takeIf { v -> v > 0 }) }, valueRange = 0f..10f, steps = 9)
            Text("Sensaciones físicas", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                feelings.forEachIndexed { i, f -> FilterChip(log.feeling == i + 1, { log = log.copy(feeling = if (log.feeling == i + 1) null else i + 1) }, label = { Text(f) }) }
            }
            OutlinedTextField(
                log.notes, { log = log.copy(notes = it) }, label = { Text("Notas (qué salió bien, qué mejorar, molestias…)") },
                minLines = 3, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Button(onClick = { onSave(log); onBack() }, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) { Text("Guardar registro") }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, max: Int = 99, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        FilledTonalIconButton(onClick = { onChange((value - 1).coerceAtLeast(0)) }) { Text("−", fontWeight = FontWeight.Bold) }
        Text("$value", Modifier.width(36.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        FilledTonalIconButton(onClick = { onChange((value + 1).coerceAtMost(max)) }) { Text("+", fontWeight = FontWeight.Bold) }
    }
}

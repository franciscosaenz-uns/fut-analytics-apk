package com.futbolanalisis.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.futbolanalisis.app.NewMatchDetails
import com.futbolanalisis.app.PendingImport
import com.futbolanalisis.app.ui.components.BigStat
import com.futbolanalisis.app.ui.components.Overline
import com.futbolanalisis.app.ui.components.Panel
import com.futbolanalisis.app.ui.components.SectionHeader
import com.futbolanalisis.app.ui.dateOnly
import com.futbolanalisis.app.ui.dateTime
import com.futbolanalisis.app.ui.theme.Brand
import com.futbolanalisis.core.coach.MatchLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Convierte la fecha elegida en el calendario (medianoche UTC) al mediodía local de ese día. */
fun pickerMillisToLocalNoon(utcMidnight: Long): Long {
    val day: LocalDate = Instant.ofEpochMilli(utcMidnight).atZone(ZoneOffset.UTC).toLocalDate()
    return day.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

/** Inversa: el día local de [ms] como medianoche UTC, que es lo que espera el calendario. */
fun localDayToPickerMillis(ms: Long): Long =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** Datos del partido que se está agregando: equipo propio, rival y fecha. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMatchScreen(
    pending: PendingImport?,
    defaultTeam: String,
    busy: Boolean,
    onConfirm: (NewMatchDetails) -> Unit,
    onCancel: () -> Unit,
) {
    Scaffold(
        containerColor = Brand.Bg,
        topBar = {
            TopAppBar(
                title = { Text("Nuevo partido") },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, "Cancelar") } },
            )
        },
    ) { padding ->
        if (pending == null) return@Scaffold
        val track = pending.track
        var ownTeam by rememberSaveable { mutableStateOf(defaultTeam) }
        var rival by rememberSaveable { mutableStateOf("") }
        var dateMs by rememberSaveable { mutableStateOf<Long?>(null) }
        var goalsFor by rememberSaveable { mutableStateOf("") }
        var goalsAgainst by rememberSaveable { mutableStateOf("") }
        var position by rememberSaveable { mutableStateOf("") }
        var showPicker by remember { mutableStateOf(false) }
        val words = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
        val number = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)

        Column(Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Panel {
                Overline("Recorrido detectado · ${pending.source}", color = Brand.Accent)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    BigStat("Duración", "${(track.endMs - track.startMs) / 60000}", Modifier.weight(1f), unit = "min")
                    BigStat("Puntos GPS", "${track.points.size}", Modifier.weight(1f))
                    BigStat("Pulso", if (track.heartRateSamples.size >= 10) "Sí" else "No", Modifier.weight(1f))
                }
                Text("Grabado el ${track.startMs.dateTime()}", style = MaterialTheme.typography.bodySmall, color = Brand.Muted, modifier = Modifier.padding(top = 8.dp))
            }

            SectionHeader("Equipos")
            OutlinedTextField(ownTeam, { ownTeam = it }, label = { Text("Tu equipo") }, singleLine = true, keyboardOptions = words, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(rival, { rival = it }, label = { Text("Rival") }, singleLine = true, keyboardOptions = words, modifier = Modifier.fillMaxWidth())

            SectionHeader("Fecha")
            Panel(onClick = { showPicker = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text((dateMs ?: track.startMs).dateOnly().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (dateMs == null) "Tomada del registro del reloj" else "Indicada a mano",
                            style = MaterialTheme.typography.bodySmall, color = Brand.Muted,
                        )
                    }
                    Text("Cambiar", style = MaterialTheme.typography.labelLarge, color = Brand.Accent)
                }
            }

            SectionHeader("Resultado (opcional)")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    goalsFor, { goalsFor = it.filter(Char::isDigit).take(2) }, label = { Text(ownTeam.ifBlank { "Tu equipo" }, maxLines = 1) },
                    singleLine = true, keyboardOptions = number, modifier = Modifier.weight(1f),
                )
                Text("–", style = MaterialTheme.typography.headlineSmall, color = Brand.Muted)
                OutlinedTextField(
                    goalsAgainst, { goalsAgainst = it.filter(Char::isDigit).take(2) }, label = { Text(rival.ifBlank { "Rival" }, maxLines = 1) },
                    singleLine = true, keyboardOptions = number, modifier = Modifier.weight(1f),
                )
            }

            SectionHeader("Posición en la que jugaste (opcional)")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MatchLog.POSITIONS.forEach { pos ->
                    FilterChip(position == pos, { position = if (position == pos) "" else pos }, label = { Text(pos) })
                }
            }
            Text(
                "Ayuda a orientar la dirección de ataque en el mapa de calor. Goles, asistencias y sensaciones se anotan después, dentro del partido.",
                style = MaterialTheme.typography.bodySmall, color = Brand.Muted, modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    onConfirm(
                        NewMatchDetails(
                            ownTeam = ownTeam, rival = rival, dateMs = dateMs,
                            goalsFor = goalsFor.toIntOrNull(), goalsAgainst = goalsAgainst.toIntOrNull(), position = position,
                        ),
                    )
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text(if (busy) "Analizando…" else "Analizar partido", fontWeight = FontWeight.Bold) }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) { Text("Cancelar") }
        }

        if (showPicker) {
            val pickerState = rememberDatePickerState(initialSelectedDateMillis = localDayToPickerMillis(dateMs ?: track.startMs))
            DatePickerDialog(
                onDismissRequest = { showPicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        pickerState.selectedDateMillis?.let { dateMs = pickerMillisToLocalNoon(it) }
                        showPicker = false
                    }) { Text("Aceptar") }
                },
                dismissButton = { TextButton(onClick = { dateMs = null; showPicker = false }) { Text("Usar la del reloj") } },
            ) { DatePicker(state = pickerState) }
        }
    }
}

package com.futbolanalisis.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.futbolanalisis.app.UiState
import com.futbolanalisis.app.health.HcAvailability
import com.futbolanalisis.app.ui.components.BigStat
import com.futbolanalisis.app.ui.components.Overline
import com.futbolanalisis.app.ui.components.Panel
import com.futbolanalisis.app.ui.components.Pill
import com.futbolanalisis.app.ui.components.SectionHeader
import com.futbolanalisis.app.ui.fmt
import com.futbolanalisis.app.ui.theme.Brand
import com.futbolanalisis.app.ui.theme.numberStyle

/** Perfil del jugador, conexión con el reloj y ayuda. */
@Composable
fun ProfileTab(state: UiState, onProfile: () -> Unit, onHealth: () -> Unit, onSample: () -> Unit) {
    val p = state.profile
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Overline("Jugador", color = Brand.Accent, modifier = Modifier.padding(top = 18.dp))
        Text("Perfil", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).background(Brand.Accent, CircleShape), contentAlignment = Alignment.Center) {
                    Text((p.name.trim().firstOrNull() ?: 'J').uppercase(), style = numberStyle(26), color = Brand.OnAccent)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        listOfNotNull(p.usualPosition, state.teamName.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { "Completa tu posición habitual" },
                        style = MaterialTheme.typography.bodySmall, color = Brand.Muted,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                BigStat("Edad", "${p.age}", Modifier.weight(1f))
                BigStat("FC máx", "${p.effectiveMaxHr}", Modifier.weight(1f), unit = "lpm", hint = if (p.maxHeartRate == null) "estimada" else null)
                BigStat("Peso", p.weightKg?.fmt(0) ?: "—", Modifier.weight(1f), unit = if (p.weightKg != null) "kg" else "")
            }
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = onProfile, modifier = Modifier.fillMaxWidth()) { Text("Editar perfil y umbrales de velocidad") }
        }

        SectionHeader("Reloj y salud")
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Health Connect", style = MaterialTheme.typography.titleMedium)
                    Text("Frecuencia cardiaca, HRV, SpO2, VO2 máx y pasos de tu reloj.", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                }
                when {
                    state.hcAvailability != HcAvailability.AVAILABLE -> Pill("No disponible", Brand.Muted)
                    state.hcGranted -> Pill("Conectado", Brand.Good)
                    else -> Pill("Sin permisos", Brand.Warn)
                }
            }
            state.wellness?.let { w ->
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth()) {
                    BigStat("HRV", w.hrvRmssdMs?.fmt(0) ?: "—", Modifier.weight(1f), unit = "ms", hint = w.readiness?.label)
                    BigStat("FC reposo", w.restingHr?.toString() ?: "—", Modifier.weight(1f), unit = "lpm")
                    BigStat("VO2 máx", w.vo2Max?.fmt(1) ?: "—", Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(onClick = onHealth, modifier = Modifier.fillMaxWidth()) { Text(if (state.hcGranted) "Ver datos y entrenamientos" else "Conectar Health Connect") }
        }

        SectionHeader("Cómo traer un partido desde Zepp")
        Panel { HowToSteps() }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onSample, modifier = Modifier.fillMaxWidth()) { Text("Agregar el partido de ejemplo") }
        Text(
            "Fútbol Análisis $version · Tus datos se guardan solo en este teléfono.",
            style = MaterialTheme.typography.labelSmall, color = Brand.Muted,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

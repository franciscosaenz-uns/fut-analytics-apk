package com.futbolanalisis.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.futbolanalisis.core.model.PlayerProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(profile: PlayerProfile, amateurZones: Boolean, onSave: (PlayerProfile, Boolean) -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf(profile.name) }
    var age by remember { mutableStateOf(profile.age.toString()) }
    var maxHr by remember { mutableStateOf(profile.maxHeartRate?.toString() ?: "") }
    var restHr by remember { mutableStateOf(profile.restingHeartRate?.toString() ?: "") }
    var weight by remember { mutableStateOf(profile.weightKg?.toString() ?: "") }
    var amateur by remember { mutableStateOf(amateurZones) }
    val number = KeyboardOptions(keyboardType = KeyboardType.Number)

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Perfil del jugador") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(age, { age = it.filter(Char::isDigit) }, label = { Text("Edad") }, keyboardOptions = number, modifier = Modifier.fillMaxWidth())
            val estimated = (208 - 0.7 * (age.toIntOrNull() ?: 30)).toInt()
            OutlinedTextField(
                maxHr, { maxHr = it.filter(Char::isDigit) },
                label = { Text("FC máxima (opcional)") },
                supportingText = { Text("Si la dejas vacía se estima en $estimated lpm (fórmula de Tanaka). Mejor si la mides en una prueba.") },
                keyboardOptions = number, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(restHr, { restHr = it.filter(Char::isDigit) }, label = { Text("FC en reposo (opcional)") }, keyboardOptions = number, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(weight, { weight = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Peso en kg (opcional)") }, keyboardOptions = number, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Umbrales de velocidad amateur", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (amateur) "Sprint > 21 km/h, alta intensidad > 16 km/h" else "Profesional: sprint > 25.2 km/h, alta intensidad > 19.8 km/h",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(amateur, { amateur = it })
            }
            Button(
                onClick = {
                    onSave(
                        PlayerProfile(
                            name = name.ifBlank { "Jugador" },
                            age = age.toIntOrNull()?.coerceIn(10, 90) ?: 30,
                            maxHeartRate = maxHr.toIntOrNull()?.takeIf { it in 120..230 },
                            restingHeartRate = restHr.toIntOrNull()?.takeIf { it in 30..120 },
                            weightKg = weight.toDoubleOrNull(),
                        ),
                        amateur,
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Guardar") }
        }
    }
}

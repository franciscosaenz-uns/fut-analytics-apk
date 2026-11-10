package com.futbolanalisis.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.futbolanalisis.app.UiState
import com.futbolanalisis.app.ui.components.AppIcons
import com.futbolanalisis.app.ui.components.Overline
import com.futbolanalisis.app.ui.theme.Brand

private data class Dest(val label: String, val icon: ImageVector)

private val destinations = listOf(
    Dest("Inicio", Icons.Filled.Home),
    Dest("Partidos", AppIcons.Pitch),
    Dest("Progreso", AppIcons.Chart),
    Dest("Perfil", Icons.Filled.Person),
)

/** Pantalla principal con las cuatro secciones y el botón de agregar partido. */
@Composable
fun MainScreen(
    state: UiState,
    snackbar: SnackbarHostState,
    onImportFile: () -> Unit,
    onSample: () -> Unit,
    onOpen: (String) -> Unit,
    onShare: (String) -> Unit,
    onHealth: () -> Unit,
    onProfile: () -> Unit,
    initialTab: Int = 0,
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var showAdd by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = Brand.Bg,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Brand.Surface, tonalElevation = 0.dp) {
                destinations.forEachIndexed { i, d ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(d.icon, d.label) },
                        label = { Text(d.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Brand.OnAccent,
                            selectedTextColor = Brand.Text,
                            indicatorColor = Brand.Accent,
                            unselectedIconColor = Brand.Muted,
                            unselectedTextColor = Brand.Muted,
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab <= 1 && state.matches.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Agregar partido", fontWeight = FontWeight.Bold) },
                    containerColor = Brand.Accent,
                    contentColor = Brand.OnAccent,
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> HomeTab(state, onAdd = { showAdd = true }, onSample = onSample, onOpen = onOpen, onShare = onShare, onProgress = { tab = 2 }, onMatches = { tab = 1 })
                1 -> MatchesTab(state, onAdd = { showAdd = true }, onOpen = onOpen)
                2 -> ProgressTab(state, onOpen = onOpen)
                else -> ProfileTab(state, onProfile = onProfile, onHealth = onHealth, onSample = onSample)
            }
        }
    }

    if (showAdd) {
        AddMatchSheet(
            onDismiss = { showAdd = false },
            onFile = { showAdd = false; onImportFile() },
            onHealth = { showAdd = false; onHealth() },
            onSample = { showAdd = false; onSample() },
        )
    }
}

/** Hoja inferior para elegir de dónde viene el partido. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMatchSheet(onDismiss: () -> Unit, onFile: () -> Unit, onHealth: () -> Unit, onSample: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Brand.Surface) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Agregar partido", style = MaterialTheme.typography.titleLarge)
            Text(
                "Elige de dónde traer el recorrido. Después podrás indicar tu equipo, el rival y la fecha.",
                style = MaterialTheme.typography.bodyMedium, color = Brand.Muted,
            )
            Spacer(Modifier.height(4.dp))
            SourceRow(Icons.Filled.Add, "Archivo GPX o TCX", "Exportado desde Zepp, Strava u otra app del reloj", onFile, primary = true)
            SourceRow(Icons.Filled.Favorite, "Health Connect", "Entrenamientos sincronizados por tu reloj", onHealth)
            SourceRow(Icons.Filled.PlayArrow, "Partido de ejemplo", "Para conocer la app sin datos propios", onSample)
            Spacer(Modifier.height(4.dp))
            Overline("Atajo desde Zepp")
            Text(
                "En Zepp abre el entrenamiento, toca Compartir o Exportar GPX y elige Fútbol Análisis: llega directo a esta pantalla.",
                style = MaterialTheme.typography.bodySmall, color = Brand.Muted,
            )
        }
    }
}

@Composable
private fun SourceRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, primary: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand.SurfaceHigh).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).background(if (primary) Brand.Accent else Brand.Outline, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (primary) Brand.OnAccent else Brand.Text)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = Brand.Muted)
    }
}

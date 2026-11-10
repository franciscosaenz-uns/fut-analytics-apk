package com.futbolanalisis.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.IntentCompat
import androidx.health.connect.client.contracts.ExerciseRouteRequestContract
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.futbolanalisis.app.ui.screens.HealthScreen
import com.futbolanalisis.app.ui.screens.MainScreen
import com.futbolanalisis.app.ui.screens.NewMatchScreen
import com.futbolanalisis.app.ui.screens.ShareScreen
import com.futbolanalisis.app.ui.screens.MatchScreen
import com.futbolanalisis.app.ui.screens.ProfileScreen
import com.futbolanalisis.app.ui.screens.CalibrationScreen
import com.futbolanalisis.app.ui.screens.TrimScreen
import com.futbolanalisis.app.ui.screens.LogScreen
import com.futbolanalisis.app.ui.theme.FutbolTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (savedInstanceState == null) handleShare(intent)
        setContent { FutbolTheme { App(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshHealth()
    }

    /** GPX/TCX compartido desde otra app (Zepp, Strava, gestor de archivos). */
    private fun handleShare(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        uri?.let { vm.importUri(it) }
    }
}

@Composable
private fun App(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importUri(it) }
    }
    val requestPermissions = rememberLauncherForActivityResult(vm.health.permissionContract()) { vm.refreshHealth() }
    val routeConsent = rememberLauncherForActivityResult(ExerciseRouteRequestContract()) { route -> vm.onRouteConsent(route) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }
    LaunchedEffect(state.openMatchId) {
        state.openMatchId?.let {
            vm.consumeOpenMatch()
            vm.openMatch(it)
            nav.navigate("match/$it") { popUpTo("home") }
        }
    }
    // Recorrido leído: falta indicar equipos y fecha antes de analizarlo.
    LaunchedEffect(state.pending) {
        if (state.pending != null) nav.navigate("new") { launchSingleTop = true }
    }
    LaunchedEffect(state.routeConsentFor) {
        state.routeConsentFor?.let { routeConsent.launch(it.id) }
    }

    NavHost(nav, startDestination = "home") {
        composable("home") {
            MainScreen(
                state = state,
                snackbar = snackbar,
                onImportFile = { pickFile.launch(arrayOf("*/*")) },
                onSample = vm::loadSample,
                onOpen = { id -> vm.openMatch(id); nav.navigate("match/$id") },
                onShare = { id -> vm.openMatch(id); nav.navigate("share/$id") },
                onHealth = { nav.navigate("health") },
                onProfile = { nav.navigate("profile") },
            )
        }
        composable("new") {
            NewMatchScreen(
                pending = state.pending,
                defaultTeam = state.teamName,
                busy = state.busy,
                onConfirm = vm::confirmImport,
                onCancel = { vm.cancelImport(); nav.popBackStack() },
            )
            androidx.activity.compose.BackHandler { vm.cancelImport(); nav.popBackStack() }
        }
        composable("match/{id}") { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            MatchScreen(
                record = current?.takeIf { it.id == id },
                matches = state.matches,
                busy = state.busy,
                onBack = { nav.popBackStack() },
                onOptions = { transform -> vm.updateOptions(id, transform) },
                onRename = { vm.rename(id, it) },
                onDelete = { vm.delete(id); nav.popBackStack() },
                onTrim = { nav.navigate("trim/$id") },
                onCalibrate = { nav.navigate("calibrate/$id") },
                onLog = { nav.navigate("log/$id") },
                onShare = { nav.navigate("share/$id") },
            )
        }
        composable("share/{id}") { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            ShareScreen(record = current?.takeIf { it.id == id }, profile = state.profile, onMessage = vm::showMessage, onBack = { nav.popBackStack() })
        }
        composable("log/{id}") { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            LogScreen(record = current?.takeIf { it.id == id }, onSave = { vm.saveLog(id, it) }, onBack = { nav.popBackStack() })
        }
        composable("trim/{id}") { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            TrimScreen(
                record = current?.takeIf { it.id == id },
                onApply = { s, e -> vm.setTrim(id, s, e) },
                onBack = { nav.popBackStack() },
            )
        }
        composable("calibrate/{id}") { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            CalibrationScreen(
                record = current?.takeIf { it.id == id },
                pitches = state.pitches,
                onApply = { cal, name -> vm.setCalibration(id, cal, name) },
                onDeletePitch = vm::deletePitch,
                onBack = { nav.popBackStack() },
            )
        }
        composable("health") {
            val context = androidx.compose.ui.platform.LocalContext.current
            HealthScreen(
                state = state,
                onRequestPermissions = { requestPermissions.launch(vm.health.permissions) },
                onInstall = { runCatching { context.startActivity(vm.health.installIntent()) } },
                onRefresh = vm::refreshHealth,
                onImportSession = vm::importSession,
                onBack = { nav.popBackStack() },
            )
        }
        composable("profile") {
            ProfileScreen(state.profile, state.zoneMode, onSave = vm::saveProfile, onBack = { nav.popBackStack() })
        }
    }
}

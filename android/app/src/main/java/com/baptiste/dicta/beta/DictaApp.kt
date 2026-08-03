package com.baptiste.dicta.beta

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baptiste.dicta.beta.domain.DetectionMode
import com.baptiste.dicta.beta.domain.SchoolLevel
import com.baptiste.dicta.beta.domain.SessionPhase
import com.baptiste.dicta.beta.vision.CameraCoordinator
import com.baptiste.dicta.beta.vision.CameraMode
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictaApp(vm: DictaViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val coordinator = remember(vm) { CameraCoordinator(context, executor, vm::onAttention) }
    DisposableEffect(Unit) { onDispose { coordinator.close(); executor.shutdown() } }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state.screen) {
            AppScreen.SETUP -> SetupScreen(state, vm)
            AppScreen.PLACEMENT -> PlacementScreen(state, vm, coordinator)
            AppScreen.READY -> ReadyScreen(state, vm)
            AppScreen.SESSION -> SessionScreen(state, vm, coordinator)
            AppScreen.SUMMARY -> SummaryScreen(state, vm)
            AppScreen.OCR -> OcrScreen(state, vm, coordinator)
            AppScreen.ERROR -> ErrorScreen(state, vm)
        }
    }
}

@Composable
private fun AppFrame(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("bêta copy chalenge", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupScreen(state: DictaUiState, vm: DictaViewModel) {
    AppFrame("Mémorisez, écrivez, vérifiez.") {
        Text("Choisissez un niveau puis lancez une courte dictée. Les images restent sur le téléphone.")
        Text("Niveau", fontWeight = FontWeight.Bold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(SchoolLevel.values().toList()) { level ->
                FilterChip(
                    selected = state.level == level,
                    onClick = { vm.selectLevel(level) },
                    label = { Text(level.label) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Text("Taille des fragments : ${state.maxLetters} lettres", fontWeight = FontWeight.Bold)
        Slider(value = state.maxLetters.toFloat(), onValueChange = { vm.setMaxLetters(it.toInt()) }, valueRange = 4f..32f, steps = 27)
        Button(onClick = vm::startChallenge, modifier = Modifier.fillMaxWidth()) { Text("Lancer un challenge") }
        if (state.leaderboard.isNotEmpty()) {
            Text("Meilleurs scores : " + state.leaderboard.joinToString(" · ") { it.score.toString() }, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PlacementScreen(state: DictaUiState, vm: DictaViewModel, coordinator: CameraCoordinator) {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionGranted = it }
    var calibrating by remember { mutableStateOf(false) }
    LaunchedEffect(calibrating) {
        if (calibrating) {
            kotlinx.coroutines.delay(1800)
            vm.calibrationFinished(coordinator.finishCalibration())
            calibrating = false
        }
    }
    AppFrame("Regardez l’écran") {
        Text("Placez le téléphone face à vous. La caméra frontale sert uniquement à savoir si vous regardez l’écran.")
        if (permissionGranted) {
            CameraPreview(coordinator, CameraMode.FRONT)
            Text(if (calibrating) "Gardez les yeux sur l’écran…" else "Votre visage doit rester dans le cadre.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (!calibrating) {
                Button(onClick = { coordinator.beginCalibration(); calibrating = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(8.dp)); Text("Commencer la calibration")
                }
            } else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Lock, null)
                    Text("La caméra est utilisée localement et n’enregistre aucune vidéo.")
                    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Autoriser la caméra") }
                }
            }
        }
        OutlinedButton(onClick = vm::useManualMode, modifier = Modifier.fillMaxWidth()) { Text("Continuer sans caméra") }
        state.calibrationMessage?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
    }
}

@Composable
private fun ReadyScreen(state: DictaUiState, vm: DictaViewModel) {
    AppFrame("Tout est prêt") {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                Text("Quand vous appuyez sur commencer, mémorisez le fragment affiché.")
                Text(if (state.session?.detectionMode == DetectionMode.MANUAL) "Mode manuel" else "Détection du regard activée", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = vm::startSession, modifier = Modifier.fillMaxWidth()) { Text("Commencer la dictée") }
    }
}

@Composable
private fun SessionScreen(state: DictaUiState, vm: DictaViewModel, coordinator: CameraCoordinator) {
    val session = state.session ?: return
    val showText = session.detectionMode == DetectionMode.MANUAL || state.attention == com.baptiste.dicta.beta.vision.AttentionState.SCREEN
    AppFrame("Fragment ${session.currentFragment + 1} / ${session.exercise.fragments.size}") {
        if (session.detectionMode == DetectionMode.CAMERA) {
            // Keep the front camera analysis alive while the learner writes.
            // The preview is local-only and is never recorded or uploaded.
            CameraPreview(coordinator, CameraMode.FRONT)
        }
        Text(if (showText) session.fragment else "Le texte est masqué : regardez l’écran pour le faire réapparaître.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp))
        if (session.detectionMode == DetectionMode.CAMERA) {
            Text(if (state.faceDetected) "Regard : ${state.attention.name.lowercase()}" else "Visage non détecté — texte masqué", style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.weight(1f))
        if (session.phase == SessionPhase.DECISION) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = vm::review, modifier = Modifier.weight(1f)) { Text("Revoir") }
                Button(onClick = vm::continueFragment, modifier = Modifier.weight(1f)) { Text(if (session.isLastFragment) "Terminer" else "Continuer") }
            }
        } else {
            OutlinedButton(onClick = vm::lookedAwayManually, modifier = Modifier.fillMaxWidth()) { Text("J’ai fini d’écrire") }
        }
    }
}

@Composable
private fun SummaryScreen(state: DictaUiState, vm: DictaViewModel) {
    AppFrame("Résultat") {
        if (state.score == null) {
            Text("Photographiez votre feuille pour vérifier l’orthographe et calculer le score.")
            Button(onClick = vm::startOcr, modifier = Modifier.fillMaxWidth()) { Text("Vérifier l’orthographe") }
            OutlinedButton(onClick = vm::finishWithoutOcr, modifier = Modifier.fillMaxWidth()) { Text("Afficher sans photo") }
        } else {
            Text("${state.score} / 100", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Text("★".repeat(state.rewardStars).ifEmpty { "Pas encore d’étoile" }, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            if (state.rewardBadge.isNotEmpty()) Text(state.rewardBadge.lowercase().replace('_', ' '), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            state.comparison?.let { comparison ->
                Text(if (comparison.matches) "Texte reconnu avec succès." else "Quelques différences sont à revoir.", fontWeight = FontWeight.Bold)
                if (comparison.differences.isNotEmpty()) Text(comparison.differences.take(5).joinToString(" · ") { "${it.reference} → ${it.recognized}" }, style = MaterialTheme.typography.bodySmall)
            }
            state.spelling?.let { spelling -> if (spelling.issues.isNotEmpty()) Text("Mots à vérifier : " + spelling.issues.joinToString(", ") { it.word }, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.weight(1f))
            Button(onClick = vm::reset, modifier = Modifier.fillMaxWidth()) { Text("Nouveau challenge") }
        }
    }
}

@Composable
private fun OcrScreen(state: DictaUiState, vm: DictaViewModel, coordinator: CameraCoordinator) {
    AppFrame("Photographiez votre feuille") {
        Text("Utilisez la caméra arrière, placez les lignes dans le cadre et prenez une image nette.")
        CameraPreview(coordinator, CameraMode.BACK)
        if (state.isOcrProcessing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Lecture de la feuille…")
        } else {
            Button(onClick = { coordinator.capture(vm::processOcr) { vm.fail(it.message ?: "La photo n’a pas pu être traitée.") } }, modifier = Modifier.fillMaxWidth()) { Text("Photographier ma feuille") }
            OutlinedButton(onClick = vm::finishWithoutOcr, modifier = Modifier.fillMaxWidth()) { Text("Terminer sans photo") }
        }
        state.ocrError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun ErrorScreen(state: DictaUiState, vm: DictaViewModel) {
    AppFrame("Une erreur est survenue") {
        Text(state.error ?: "La séance peut être reprise en mode manuel.")
        Button(onClick = vm::reset, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Recommencer") }
    }
}

@Composable
private fun CameraPreview(coordinator: CameraCoordinator, mode: CameraMode) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewState = remember { mutableStateOf<PreviewView?>(null) }
    AndroidView(
        factory = { context -> PreviewView(context).also { previewState.value = it } },
        modifier = Modifier.fillMaxWidth().height(220.dp).background(Color.Black, RoundedCornerShape(20.dp)),
    )
    val preview = previewState.value
    DisposableEffect(mode, preview, lifecycleOwner) {
        if (preview != null) coordinator.attachPreview(preview, lifecycleOwner, mode)
        onDispose { coordinator.detach() }
    }
}

package com.baptiste.dicta.beta

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.baptiste.dicta.beta.data.LeaderboardEntry
import com.baptiste.dicta.beta.domain.Badge
import com.baptiste.dicta.beta.domain.DetectionMode
import com.baptiste.dicta.beta.domain.SchoolLevel
import com.baptiste.dicta.beta.domain.SessionPhase
import com.baptiste.dicta.beta.vision.AttentionState
import com.baptiste.dicta.beta.vision.CameraCoordinator
import com.baptiste.dicta.beta.vision.CameraMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

private val Paper = Color(0xFFF2C8A7)
private val PaperStrong = Color(0xFFFFFAF2)
private val Ink = Color(0xFF25233B)
private val Muted = Color(0xFF716D7E)
private val Violet = Color(0xFF6654D9)
private val VioletDark = Color(0xFF4E3EB2)
private val VioletSoft = Color(0xFFE9E4FF)
private val Coral = Color(0xFFEF765F)
private val Mint = Color(0xFFCFE8DC)
private val Success = Color(0xFF58A37C)
private val CardShape = RoundedCornerShape(28.dp)
private val ButtonShape = RoundedCornerShape(18.dp)
private const val CALIBRATION_PREPARATION_MS = 2_000L
private const val CALIBRATION_MEASUREMENT_MS = 1_500L
private const val SCORE_REVEAL_DURATION_MS = 1_800
private const val REWARD_FEATURE_DURATION_MS = 2_400L

@Composable
fun DictaApp(vm: DictaViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val coordinator = remember(vm) { CameraCoordinator(context, executor, vm::onAttention) }

    DisposableEffect(Unit) {
        onDispose {
            coordinator.close()
            executor.shutdown()
        }
    }
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.checkForUpdates()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFFF4C29F), Color(0xFFF0D0B8), Color(0xFFD7CBEA)),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
            ),
    ) {
        BackgroundHalos()
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .align(Alignment.TopCenter),
            color = Color.Transparent,
        ) {
            when (state.screen) {
                AppScreen.SETUP -> SetupScreen(state, vm)
                AppScreen.PLACEMENT -> PlacementScreen(state, vm, coordinator)
                AppScreen.CALIBRATION -> CalibrationScreen(state, vm, coordinator)
                AppScreen.SESSION -> SessionScreen(state, vm, coordinator)
                AppScreen.SUMMARY -> SummaryScreen(state, vm)
                AppScreen.ERROR -> ErrorScreen(state, vm)
            }
        }
    }
}

@Composable
private fun BackgroundHalos() {
    Canvas(Modifier.fillMaxSize().alpha(0.72f)) {
        drawCircle(Color(0x66EF533E), radius = size.minDimension * 0.48f, center = Offset(0f, 0f))
        drawCircle(Color(0x555B41CF), radius = size.minDimension * 0.44f, center = Offset(size.width, size.height * 0.08f))
        drawCircle(Color(0x44309E70), radius = size.minDimension * 0.55f, center = Offset(size.width * 0.5f, size.height * 1.04f))
    }
}

@Composable
private fun AppColumn(
    showInfo: Boolean = false,
    infoExpanded: Boolean = false,
    onInfo: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    scroll: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val baseModifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(horizontal = 18.dp, vertical = 12.dp)
    val modifier = if (scroll) baseModifier.verticalScroll(rememberScrollState()) else baseModifier
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TopBar(showInfo, infoExpanded, onInfo, onClose)
        content()
    }
}

@Composable
private fun TopBar(showInfo: Boolean, infoExpanded: Boolean, onInfo: (() -> Unit)?, onClose: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppLogo()
            Text("Copy Challenge", color = Ink, fontWeight = FontWeight.Black, fontSize = 22.sp)
        }
        when {
            showInfo -> Surface(
                modifier = Modifier.size(38.dp).clickable { onInfo?.invoke() },
                shape = CircleShape,
                color = if (infoExpanded) Violet else PaperStrong.copy(alpha = 0.75f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Violet.copy(alpha = 0.28f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("i", color = if (infoExpanded) Color.White else VioletDark, fontWeight = FontWeight.Black, fontSize = 20.sp)
                }
            }
            onClose != null -> IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(42.dp)
                    .background(PaperStrong.copy(alpha = 0.66f), RoundedCornerShape(14.dp))
                    .border(1.dp, Ink.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Quitter la séance", tint = Ink)
            }
        }
    }
}

@Composable
private fun AppLogo() {
    Canvas(
        Modifier
            .size(34.dp)
            .shadow(5.dp, RoundedCornerShape(11.dp))
            .clip(RoundedCornerShape(11.dp))
            .background(Violet),
    ) {
        val page = Path().apply {
            moveTo(size.width * .24f, size.height * .19f)
            lineTo(size.width * .69f, size.height * .15f)
            lineTo(size.width * .76f, size.height * .77f)
            lineTo(size.width * .30f, size.height * .82f)
            close()
        }
        drawPath(page, PaperStrong)
        drawLine(Coral, Offset(size.width * .24f, size.height * .67f), Offset(size.width * .76f, size.height * .26f), strokeWidth = size.width * .11f, cap = StrokeCap.Round)
        drawLine(VioletDark, Offset(size.width * .34f, size.height * .46f), Offset(size.width * .46f, size.height * .58f), strokeWidth = size.width * .08f, cap = StrokeCap.Round)
        drawLine(VioletDark, Offset(size.width * .46f, size.height * .58f), Offset(size.width * .68f, size.height * .35f), strokeWidth = size.width * .08f, cap = StrokeCap.Round)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupScreen(state: DictaUiState, vm: DictaViewModel) {
    val context = LocalContext.current
    var helpOpen by rememberSaveable { mutableStateOf(false) }
    var levelMenuOpen by remember { mutableStateOf(false) }
    AppColumn(
        showInfo = true,
        infoExpanded = helpOpen,
        onInfo = { helpOpen = !helpOpen },
        scroll = true,
    ) {
        state.availableUpdate?.let { update ->
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Mint.copy(alpha = .96f))) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Mise à jour ${update.versionName} disponible", color = Ink, fontWeight = FontWeight.Black)
                    Text("Une nouvelle bêta de Copy Challenge est prête à installer.", color = Muted, fontSize = 13.sp)
                    Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl))) }) {
                        Text("Télécharger la mise à jour")
                    }
                }
            }
        }
        if (helpOpen) HelpPanel { helpOpen = false }
        HomeIllustration()
        Card(
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = PaperStrong.copy(alpha = 0.92f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Niveau de classe", color = Ink, fontWeight = FontWeight.ExtraBold)
                    Pill("Challenge ${state.challengeIndex + 1} / ${state.challengeTotal}")
                }
                Box {
                    val levelColor = levelColor(state.level)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Brush.horizontalGradient(listOf(levelColor.copy(alpha = .22f), Color.White.copy(alpha = .95f))))
                            .border(1.dp, levelColor, RoundedCornerShape(18.dp))
                            .clickable { levelMenuOpen = true }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(state.level.label, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        Text("⌄", color = levelColor, fontWeight = FontWeight.Black, fontSize = 24.sp)
                    }
                    DropdownMenu(expanded = levelMenuOpen, onDismissRequest = { levelMenuOpen = false }) {
                        SchoolLevel.values().forEach { level ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(level.label, fontWeight = FontWeight.Bold, color = Ink)
                                        Text(level.cycle, color = Muted, fontSize = 12.sp)
                                    }
                                },
                                leadingIcon = { Box(Modifier.size(10.dp).background(levelColor(level), CircleShape)) },
                                trailingIcon = if (level == state.level) ({ Icon(Icons.Default.Check, null, tint = levelColor(level)) }) else null,
                                onClick = {
                                    levelMenuOpen = false
                                    vm.selectLevel(level)
                                },
                            )
                        }
                    }
                }
                Text(
                    "Challenge ${state.challengeIndex + 1} sur ${state.challengeTotal} · ${state.level.cycle} · ${state.wordCount} mots",
                    color = Muted,
                    fontSize = 12.sp,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Lettres par étape", color = Ink, fontWeight = FontWeight.ExtraBold)
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(15.dp)).border(1.dp, Ink.copy(alpha = .12f), RoundedCornerShape(15.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StepperButton("Réduire le nombre de lettres", Icons.Default.Remove) { vm.setMaxLetters(state.maxLetters - 1) }
                        Text(
                            state.maxLetters.toString(),
                            modifier = Modifier.width(54.dp),
                            textAlign = TextAlign.Center,
                            color = Ink,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                        )
                        StepperButton("Augmenter le nombre de lettres", Icons.Default.Add) { vm.setMaxLetters(state.maxLetters + 1) }
                    }
                }
                PrimaryButton("Lancer un challenge.", vm::startChallenge)
                TextButton(onClick = vm::startManualChallenge, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Continuer sans caméra", color = Muted, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
                }
                state.cameraMessage?.let { Text(it, color = Muted, fontSize = 13.sp) }
                TextButton(onClick = { vm.checkForUpdates(force = true) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (state.updateCheckState == UpdateCheckState.CHECKING) "Recherche en cours…" else "Rechercher les mises à jour", color = Muted)
                }
            }
        }
        Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(Modifier.padding(top = 5.dp).size(9.dp).background(Success, CircleShape))
            Text("La vidéo reste sur ce téléphone. Aucune image n’est enregistrée ni envoyée.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text("Tous droits réservés, Florence Fauchery", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun HelpPanel(onClose: () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = PaperStrong.copy(alpha = .96f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Eyebrow("Mode d’emploi")
                    Text("Comment utiliser Copy Challenge", color = Ink, fontSize = 27.sp, lineHeight = 29.sp, fontWeight = FontWeight.Black)
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Fermer les informations", tint = Ink) }
            }
            val steps = listOf(
                "Choisis ta classe" to "Le niveau sélectionne un challenge adapté. Le numéro du challenge est indiqué à côté du niveau.",
                "Choisis les lettres par étape" to "Le challenge est découpé en petits groupes de mots, sans mélanger deux phrases.",
                "Lis tout le challenge" to "Avec la caméra ou en mode manuel, lis le texte affiché puis appuie sur « J’ai lu ».",
                "Mémorise et écris" to "Regarde le fragment, écris-le sur ton cahier, puis relève les yeux pour continuer.",
                "Revois si nécessaire" to "À chaque étape, choisis « Revoir » ou « Continuer ». Ton score et le classement s’affichent à la fin.",
            )
            steps.forEachIndexed { index, (title, body) ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(28.dp).background(VioletSoft, CircleShape), contentAlignment = Alignment.Center) {
                        Text("${index + 1}", color = VioletDark, fontWeight = FontWeight.Black)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(title, color = Ink, fontWeight = FontWeight.ExtraBold)
                        Text(body, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeIllustration() {
    Card(
        modifier = Modifier.fillMaxWidth().height(175.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = PaperStrong),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.dicta_banner_tilted_notebook),
            contentDescription = "Un œil, un cahier et un crayon illustrent le challenge de mémoire.",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun PlacementScreen(state: DictaUiState, vm: DictaViewModel, coordinator: CameraCoordinator) {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var askedPermission by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
        askedPermission = true
        if (!it) vm.cameraUnavailable("La caméra n’est pas disponible. Vous pouvez continuer en mode manuel.")
    }
    LaunchedEffect(Unit) {
        if (!permissionGranted && !askedPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    AppColumn(onClose = vm::reset, scroll = true) {
        Spacer(Modifier.height(4.dp))
        Eyebrow("Installation")
        Text("Place ton visage dans le repère.", color = Ink, fontSize = 44.sp, lineHeight = 43.sp, fontWeight = FontWeight.Black)
        Text("Pose le téléphone verticalement, à peu près à la longueur d’un bras.", color = Muted, lineHeight = 21.sp)
        Card(shape = CardShape, colors = CardDefaults.cardColors(containerColor = PaperStrong.copy(alpha = .92f))) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (permissionGranted) {
                    CameraStage(coordinator, state.faceDetected) { vm.cameraUnavailable(it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("◎", color = Violet, fontSize = 24.sp)
                        Text("Centre ton visage dans le cadre, puis garde le téléphone bien droit.", color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    PrimaryButton(
                        if (state.faceDetected) "Mon visage est bien placé" else "Recherche du visage…",
                        vm::beginCameraCalibration,
                        enabled = state.faceDetected,
                    )
                } else {
                    Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("La caméra est utilisée localement et n’enregistre aucune vidéo.", color = Ink, textAlign = TextAlign.Center)
                        OutlinedButton(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Autoriser la caméra") }
                    }
                }
                TextButton(onClick = vm::startManualChallenge, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Utiliser le mode manuel", color = Muted, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
                }
            }
        }
    }
}

@Composable
private fun CameraStage(coordinator: CameraCoordinator, faceDetected: Boolean, onError: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(.84f)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF373056), Color(0xFF171627)))),
    ) {
        CameraPreview(coordinator, CameraMode.FRONT, Modifier.fillMaxSize(), onError)
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = .18f))
            val guide = Rect(size.width * .16f, size.height * .11f, size.width * .84f, size.height * .84f)
            drawOval(
                if (faceDetected) Success else Color.White,
                topLeft = guide.topLeft,
                size = guide.size,
                style = Stroke(width = 5f),
            )
            val corner = 28f
            val stroke = if (faceDetected) Success else Color.White
            listOf(
                Offset(guide.left, guide.top) to Offset(1f, 1f),
                Offset(guide.right, guide.top) to Offset(-1f, 1f),
                Offset(guide.left, guide.bottom) to Offset(1f, -1f),
                Offset(guide.right, guide.bottom) to Offset(-1f, -1f),
            ).forEach { (point, direction) ->
                drawLine(stroke, point, Offset(point.x + direction.x * corner, point.y), 7f, StrokeCap.Round)
                drawLine(stroke, point, Offset(point.x, point.y + direction.y * corner), 7f, StrokeCap.Round)
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(14.dp),
            shape = RoundedCornerShape(99.dp),
            color = Color(0xDD25233B),
        ) {
            Row(Modifier.padding(horizontal = 13.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(if (faceDetected) Success else Coral, CircleShape))
                Text(if (faceDetected) "Visage détecté" else "Place ton visage dans le cadre", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CalibrationScreen(state: DictaUiState, vm: DictaViewModel, coordinator: CameraCoordinator) {
    val session = state.session ?: return
    val cameraMode = session.detectionMode == DetectionMode.CAMERA

    if (cameraMode) {
        Box(Modifier.size(1.dp).alpha(.01f)) {
            CameraPreview(coordinator, CameraMode.FRONT, Modifier.size(1.dp)) { vm.cameraUnavailable(it) }
        }
        LaunchedEffect(session.id, state.calibrationAttempt) {
            if (state.calibrationStage == CalibrationStage.PREPARING) {
                delay(CALIBRATION_PREPARATION_MS)
                vm.calibrationMeasuring()
                coordinator.beginCalibration()
                delay(CALIBRATION_MEASUREMENT_MS)
                val success = coordinator.finishCalibration()
                vm.calibrationFinished(success)
                if (success) playCalibrationBeep()
            }
        }
    }

    AppColumn(onClose = vm::reset) {
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = PaperStrong.copy(alpha = .94f)),
        ) {
            Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Eyebrow("Lis le challenge")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White.copy(alpha = .74f))
                        .border(1.dp, Ink.copy(alpha = .12f), RoundedCornerShape(22.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    Text(session.exercise.sourceText, color = Ink, fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold)
                }
                when (state.calibrationStage) {
                    CalibrationStage.FAILED -> {
                        Text("Replace ton visage dans le champ de la caméra.", modifier = Modifier.fillMaxWidth(), color = Color(0xFF94412F), fontSize = 13.sp, textAlign = TextAlign.Center)
                        OutlinedButton(onClick = vm::retryCalibration, modifier = Modifier.fillMaxWidth().height(56.dp), shape = ButtonShape) { Text("Réessayer", color = Ink, fontWeight = FontWeight.Bold) }
                    }
                    else -> {
                        Text(
                            if (cameraMode && state.calibrationStage != CalibrationStage.READY) "Lis tout le challenge pendant que le regard est calibré, puis appuie quand tu as terminé."
                            else "Lis tout le challenge, puis appuie quand tu as terminé.",
                            modifier = Modifier.fillMaxWidth(),
                            color = Muted,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                        )
                        if (cameraMode && state.calibrationStage != CalibrationStage.READY) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Violet, trackColor = VioletSoft)
                        }
                        PrimaryButton(
                            if (state.calibrationReadConfirmed && state.calibrationStage != CalibrationStage.READY) "Lecture terminée · calibration en cours…" else "J’ai lu",
                            vm::confirmCalibrationRead,
                            enabled = !state.calibrationReadConfirmed || state.calibrationStage == CalibrationStage.READY,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionScreen(state: DictaUiState, vm: DictaViewModel, coordinator: CameraCoordinator) {
    val session = state.session ?: return
    if (session.detectionMode == DetectionMode.CAMERA) {
        Box(Modifier.size(1.dp).alpha(.01f)) {
            CameraPreview(coordinator, CameraMode.FRONT, Modifier.size(1.dp)) { vm.cameraUnavailable(it) }
        }
    }
    val progress = (session.currentFragment + 1f) / session.exercise.fragments.size.coerceAtLeast(1)
    val percent = (progress * 100).toInt()
    val progressColor = Color.hsl(120f * progress.pow(1.65f), .72f, .52f)

    AppColumn(onClose = vm::reset) {
        Column(Modifier.padding(horizontal = 2.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Étape ${session.currentFragment + 1} sur ${session.exercise.fragments.size}", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("$percent %", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = progressColor, trackColor = Ink.copy(alpha = .1f))
        }
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = PaperStrong.copy(alpha = .94f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Box(Modifier.fillMaxSize().padding(18.dp).border(1.dp, Violet.copy(alpha = .2f), RoundedCornerShape(21.dp))) {
                if (session.phase == SessionPhase.MEMORIZING) MemorizingStage(state, vm)
                else DecisionStage(vm)
            }
        }
    }
}

@Composable
private fun BoxScope.MemorizingStage(state: DictaUiState, vm: DictaViewModel) {
    val session = state.session ?: return
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StatusPill(
            when {
                session.detectionMode == DetectionMode.MANUAL -> "Mode manuel"
                state.attention == AttentionState.SCREEN -> "Regard détecté"
                else -> "Analyse du regard"
            },
            unknown = state.attention == AttentionState.UNKNOWN && session.detectionMode == DetectionMode.CAMERA,
        )
        Spacer(Modifier.height(20.dp))
        Text(session.fragment, modifier = Modifier.fillMaxWidth(), color = Ink, fontSize = 43.sp, lineHeight = 46.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Text("Mémorise ces mots, puis écris-les sur ton cahier.", color = Muted, lineHeight = 21.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = vm::hideFragment) {
            Text(if (session.detectionMode == DetectionMode.CAMERA) "Masquer maintenant" else "J’ai mémorisé", color = Muted, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
        }
    }
}

@Composable
private fun BoxScope.DecisionStage(vm: DictaViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Eyebrow("À toi de choisir")
        Spacer(Modifier.height(40.dp))
        Text("Revoir les mots ?", color = Ink, fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(30.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = vm::review,
                modifier = Modifier.weight(1f).height(70.dp),
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(containerColor = VioletSoft, contentColor = VioletDark),
            ) { Text("↶  Revoir", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
            Button(
                onClick = vm::continueFragment,
                modifier = Modifier.weight(1f).height(70.dp),
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Color.White),
            ) { Text("Continuer  →", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
        }
    }
}

@Composable
private fun SummaryScreen(state: DictaUiState, vm: DictaViewModel) {
    val score = state.score ?: 0
    var revealedScore by remember(score) { mutableStateOf(0) }
    var revealComplete by remember(score) { mutableStateOf(false) }
    var rewardFeatured by remember(score) { mutableStateOf(false) }

    LaunchedEffect(score) {
        val started = SystemClock.uptimeMillis()
        do {
            val progress = ((SystemClock.uptimeMillis() - started).toFloat() / SCORE_REVEAL_DURATION_MS).coerceIn(0f, 1f)
            val eased = 1f - (1f - progress).pow(3)
            revealedScore = (score * eased).toInt()
            if (progress < 1f) delay(16)
        } while (progress < 1f)
        revealedScore = score
        revealComplete = true
        rewardFeatured = true
        if (score > 80) playScoreFanfare()
        delay(REWARD_FEATURE_DURATION_MS)
        rewardFeatured = false
    }

    AppColumn(onClose = vm::reset, scroll = true) {
        Box {
            if (revealComplete && score > 0) ConfettiField()
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Bravo, c’est terminé !", color = Ink, fontSize = 42.sp, lineHeight = 43.sp, fontWeight = FontWeight.Black)
                Card(
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = PaperStrong.copy(alpha = .95f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                ) {
                    Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(revealedScore.toString(), color = if (state.isNewBestScore) Coral else Violet, fontSize = 72.sp, fontWeight = FontWeight.Black, lineHeight = 76.sp)
                        Text(if (state.isNewBestScore) "Nouveau record !" else "score sur 100", color = Muted)
                        LinearProgressIndicator(
                            progress = revealedScore / 100f,
                            modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape),
                            color = Violet,
                            trackColor = VioletSoft,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Progression", color = Muted, fontSize = 13.sp)
                            Text("$revealedScore / 100", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                        RewardDisplay(revealedScore, featured = rewardFeatured)
                        Leaderboard(state.leaderboard, state.currentScoreId, state.isNewBestScore)
                        Surface(shape = RoundedCornerShape(18.dp), color = VioletSoft.copy(alpha = .7f)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(state.session?.totalReviews?.toString() ?: "0", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Black)
                                Text("relecture", color = Muted, fontSize = 13.sp)
                            }
                        }
                        PrimaryButton("Préparer le challenge suivant", vm::prepareNextDictation)
                    }
                }
            }
        }
    }
}

@Composable
private fun RewardDisplay(score: Int, featured: Boolean) {
    val reward = com.baptiste.dicta.beta.domain.rewardFor(score)
    val badge = when (reward.badge) {
        Badge.NONE -> null
        Badge.BRONZE -> "🥉" to "Médaille de bronze"
        Badge.SILVER -> "🥈" to "Médaille d’argent"
        Badge.GOLD -> "🥇" to "Médaille d’or"
        Badge.TROPHY -> "🏆" to "Coupe en or"
    }
    val stars = "★".repeat(reward.stars)
    val scale = if (featured) 1.55f else 1f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (featured) 24.dp else 6.dp)
            .semantics { contentDescription = if (reward.stars == 0) "Aucune étoile" else "${reward.stars} étoiles${badge?.let { ", ${it.second}" } ?: ""}" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (stars.isNotEmpty()) Text(stars, color = Color(0xFFF3B34F), fontSize = (28 * scale).sp, fontWeight = FontWeight.Black)
        if (badge != null) Text("${badge.first}  ${badge.second}", color = Ink, fontSize = (16 * scale).sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun Leaderboard(entries: List<LeaderboardEntry>, currentId: String?, isNewBest: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Classement", color = Ink, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text("Meilleurs scores", color = Muted, fontSize = 13.sp)
        }
        if (entries.isEmpty()) {
            Text("Ton score apparaîtra ici.", color = Muted, fontSize = 13.sp)
        } else entries.take(5).forEachIndexed { index, entry ->
            val rank = index + 1
            val rankLabel = when (rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "#$rank" }
            val current = entry.id == currentId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (current) VioletSoft else Color.White.copy(alpha = .62f))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(rankLabel, modifier = Modifier.width(44.dp), fontSize = if (rank <= 3) 24.sp else 15.sp, fontWeight = FontWeight.Black)
                Text(entry.score.toString(), modifier = Modifier.weight(1f), color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
                if (current) Text(if (isNewBest && rank == 1) "Toi · record" else "Toi", color = VioletDark, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun ConfettiField() {
    val fall = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fall.animateTo(1f, tween(6_500)) }
    val colors = listOf(Coral, Violet, Success, Color(0xFFF3B34F))
    Canvas(Modifier.fillMaxWidth().height(560.dp)) {
        repeat(56) { index ->
            val seed = ((index * 47) % 101) / 101f
            val x = size.width * (((index * 37) % 97) / 97f)
            val origin = if (index % 3 == 0) -size.height * .04f else size.height * (.04f + seed * .18f)
            val y = origin + fall.value * size.height * (1.05f + seed * .45f)
            val drift = sin((fall.value * 5f + seed) * PI).toFloat() * 34f
            drawRoundRect(colors[index % colors.size], Offset(x + drift, y), Size(8f + index % 4, 15f + index % 5), CornerRadius(3f, 3f))
        }
    }
}

@Composable
private fun ErrorScreen(state: DictaUiState, vm: DictaViewModel) {
    AppColumn(onClose = vm::reset) {
        Card(shape = CardShape, colors = CardDefaults.cardColors(containerColor = PaperStrong.copy(alpha = .95f))) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Une erreur est survenue", color = Ink, fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
                Text(state.error ?: "La séance peut être reprise en mode manuel.", color = Muted)
                PrimaryButton("Recommencer", vm::reset)
            }
        }
    }
}

@Composable
private fun CameraPreview(
    coordinator: CameraCoordinator,
    mode: CameraMode,
    modifier: Modifier,
    onError: (String) -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewState = remember { mutableStateOf<PreviewView?>(null) }
    AndroidView(
        factory = { context ->
            PreviewView(context).also {
                it.scaleType = PreviewView.ScaleType.FILL_CENTER
                previewState.value = it
            }
        },
        modifier = modifier,
    )
    val preview = previewState.value
    DisposableEffect(mode, preview, lifecycleOwner) {
        if (preview != null) {
            runCatching { coordinator.attachPreview(preview, lifecycleOwner, mode) }
                .onFailure { onError("La caméra n’est pas disponible. Vous pouvez continuer en mode manuel.") }
        }
        onDispose { coordinator.detach() }
    }
}

@Composable
private fun Eyebrow(text: String) {
    Text(text.uppercase(), color = VioletDark, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
}

@Composable
private fun Pill(text: String) {
    Surface(shape = CircleShape, color = VioletSoft) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = VioletDark, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
    }
}

@Composable
private fun StatusPill(text: String, unknown: Boolean) {
    Surface(shape = CircleShape, color = if (unknown) Color(0xFFEEEAF7) else Mint) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(if (unknown) Color(0xFF5C5670) else Color(0xFF276348), CircleShape))
            Text(text, color = if (unknown) Color(0xFF5C5670) else Color(0xFF276348), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Color.White, disabledContainerColor = Violet.copy(alpha = .45f)),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp),
    ) { Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
}

@Composable
private fun StepperButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(46.dp)) { Icon(icon, label, tint = VioletDark) }
}

private fun levelColor(level: SchoolLevel): Color = when (level) {
    SchoolLevel.CP -> Coral
    SchoolLevel.CE1 -> Color(0xFFE19B38)
    SchoolLevel.CE2 -> Success
    SchoolLevel.CM1 -> Color(0xFF4B9DB6)
    SchoolLevel.CM2 -> Violet
}

private fun playCalibrationBeep() {
    runCatching {
        ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 75).apply {
            startTone(ToneGenerator.TONE_PROP_BEEP, 220)
            Thread { Thread.sleep(260); release() }.start()
        }
    }
}

private suspend fun playScoreFanfare() = withContext(Dispatchers.Default) {
    runCatching {
        val sampleRate = 22_050
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.5)
        val noteSeconds = .24
        val gapSeconds = .11
        val totalSeconds = gapSeconds * (notes.size - 1) + noteSeconds
        val samples = ShortArray((sampleRate * totalSeconds).toInt())
        notes.forEachIndexed { index, frequency ->
            val start = (index * gapSeconds * sampleRate).toInt()
            val length = (noteSeconds * sampleRate).toInt()
            repeat(length) { offset ->
                val phase = (offset * frequency / sampleRate) % 1.0
                val envelope = when {
                    offset < sampleRate * .025 -> offset / (sampleRate * .025)
                    else -> (1.0 - offset.toDouble() / length).coerceAtLeast(0.0)
                }
                val saw = 2.0 * phase - 1.0
                val value = (saw * envelope * Short.MAX_VALUE * .11).toInt().toShort()
                val target = start + offset
                if (target in samples.indices) {
                    samples[target] = (samples[target].toInt() + value.toInt())
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                }
            }
        }
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(maxOf(minBuffer, samples.size * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(samples, 0, samples.size)
        track.setNotificationMarkerPosition(samples.size)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(audioTrack: AudioTrack) { audioTrack.release() }
            override fun onPeriodicNotification(audioTrack: AudioTrack) = Unit
        })
        track.play()
    }
}

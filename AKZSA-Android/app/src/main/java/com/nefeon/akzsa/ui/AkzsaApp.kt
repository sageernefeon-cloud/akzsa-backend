package com.nefeon.akzsa.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nefeon.akzsa.MainActivity
import com.nefeon.akzsa.diagnostics.DiagnosticStore
import com.nefeon.akzsa.keyboard.AkzsaInputMethodService
import com.nefeon.akzsa.network.ApiResult
import com.nefeon.akzsa.network.BackendClient
import androidx.compose.ui.graphics.luminance
import com.nefeon.akzsa.design.GraphiteSignal
import com.nefeon.akzsa.network.SettingsValidation
import com.nefeon.akzsa.security.SecureTokenStore
import com.nefeon.akzsa.settings.AppSettings
import com.nefeon.akzsa.settings.SettingsRepository
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Green used for "good" states, taken from the Graphite Signal tokens (AA contrast in both themes). */
private val androidx.compose.material3.ColorScheme.successTone: Color
    get() = Color(GraphiteSignal.palette(background.luminance() < 0.5f).success)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AkzsaApp(requestMicPermission: Boolean) {
    val context = LocalContext.current
    val repository = remember { SettingsRepository(context.applicationContext) }
    val settings by repository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    var screen by remember { mutableStateOf(if (settings.onboardingComplete) "home" else "onboarding") }
    var showPermissionDialog by remember { mutableStateOf(requestMicPermission) }
    var permissionGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(settings.onboardingComplete) {
        if (!settings.onboardingComplete) screen = "onboarding" else if (screen == "onboarding") screen = "home"
    }
    LaunchedEffect(requestMicPermission) { if (requestMicPermission) showPermissionDialog = true }

    AkzsaTheme(settings.theme) {
        Scaffold(
            contentWindowInsets = WindowInsets.systemBars,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                if (screen != "onboarding") {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BrandMark(34.dp)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("AKZSA", fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, fontSize = 17.sp)
                                    Text("One Mic AI Keyboard", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        navigationIcon = {
                            if (screen != "home") TextButton(onClick = { screen = "home" }) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    )
                }
            },
        ) { padding ->
            when (screen) {
                "onboarding" -> OnboardingScreen(
                    modifier = Modifier.padding(padding),
                    onContinue = {
                        scope.launch { repository.update { it.copy(onboardingComplete = true) } }
                        screen = "home"
                    },
                    onPrivacy = { screen = "privacy" },
                )
                "settings" -> SettingsScreen(
                    modifier = Modifier.padding(padding), settings = settings, repository = repository,
                    onMessage = { scope.launch { snackbar.showSnackbar(it) } },
                )
                "diagnostics" -> DiagnosticsScreen(Modifier.padding(padding), settings.backendBaseUrl)
                "privacy" -> PrivacyScreen(Modifier.padding(padding))
                "help" -> HelpScreen(Modifier.padding(padding), onOpenSettings = { screen = "settings" })
                else -> HomeScreen(
                    modifier = Modifier.padding(padding), settings = settings, permissionGranted = permissionGranted,
                    onGrantPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onEnableKeyboard = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                    onChooseKeyboard = {
                        val imm = context.getSystemService(InputMethodManager::class.java)
                        imm?.showInputMethodPicker()
                    },
                    onSettings = { screen = "settings" },
                    onDiagnostics = { screen = "diagnostics" },
                    onPrivacy = { screen = "privacy" },
                    onHelp = { screen = "help" },
                    onMessage = { scope.launch { snackbar.showSnackbar(it) } },
                )
            }
        }
        if (showPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showPermissionDialog = false },
                icon = { Icon(Icons.Rounded.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                title = { Text("Allow voice typing") },
                text = { Text("AKZSA uses the microphone only while you are voice typing. Depending on your device, speech may be processed by network services. Audio is uploaded to AKZSA only if you turn on higher-accuracy cloud dictation in Settings. You can still type without granting microphone access.") },
                confirmButton = {
                    Button(onClick = {
                        showPermissionDialog = false
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }) { Text("Continue to permission") }
                },
                dismissButton = { TextButton(onClick = { showPermissionDialog = false }) { Text("Not now") } },
            )
        }
    }
}

@Composable
private fun AkzsaTheme(theme: String, content: @Composable () -> Unit) {
    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    // AKZSA Midnight: neutral surfaces, one calm indigo action colour (primary / tertiary) and a teal voice accent.
    val p = GraphiteSignal.paletteForTheme(theme, dark)
    val target = if (dark) darkColorScheme(
        primary = Color(p.accent), onPrimary = Color(p.onAccent), primaryContainer = Color(p.secondarySurface), onPrimaryContainer = Color(p.textPrimary),
        secondary = Color(p.voice), onSecondary = Color(p.onAccent), tertiary = Color(p.accent),
        background = Color(p.background), onBackground = Color(p.textPrimary),
        surface = Color(p.surface), onSurface = Color(p.textPrimary),
        surfaceVariant = Color(p.secondarySurface), onSurfaceVariant = Color(p.textSecondary),
        outline = Color(p.borderStrong), outlineVariant = Color(p.border), error = Color(p.error), onError = Color(p.onError),
    ) else lightColorScheme(
        primary = Color(p.accent), onPrimary = Color(p.onAccent), primaryContainer = Color(p.secondarySurface), onPrimaryContainer = Color(p.textPrimary),
        secondary = Color(p.voice), onSecondary = Color(0xFF0B2C33), tertiary = Color(p.accent),
        background = Color(p.background), onBackground = Color(p.textPrimary),
        surface = Color(p.surface), onSurface = Color(p.textPrimary),
        surfaceVariant = Color(p.secondarySurface), onSurfaceVariant = Color(p.textSecondary),
        outline = Color(p.borderStrong), outlineVariant = Color(p.border), error = Color(p.error), onError = Color(p.onError),
    )
    // Smooth theme switching: every colour eases to its new value instead of jumping.
    val spec = androidx.compose.animation.core.tween<Color>(durationMillis = 280)
    @Composable fun anim(c: Color): Color = androidx.compose.animation.animateColorAsState(c, spec, label = "theme").value
    val colors = target.copy(
        primary = anim(target.primary), onPrimary = anim(target.onPrimary),
        primaryContainer = anim(target.primaryContainer), onPrimaryContainer = anim(target.onPrimaryContainer),
        secondary = anim(target.secondary), onSecondary = anim(target.onSecondary), tertiary = anim(target.tertiary),
        background = anim(target.background), onBackground = anim(target.onBackground),
        surface = anim(target.surface), onSurface = anim(target.onSurface),
        surfaceVariant = anim(target.surfaceVariant), onSurfaceVariant = anim(target.onSurfaceVariant),
        outline = anim(target.outline), outlineVariant = anim(target.outlineVariant),
        error = anim(target.error), onError = anim(target.onError),
    )
    MaterialTheme(colorScheme = colors, typography = MaterialTheme.typography.copy(
        headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold),
        titleLarge = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
        bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.SansSerif),
    ), content = content)
}

/** The AKZSA mark: a rounded "A" arch with the teal voice dot. Drawn in code so it matches the launcher icon at any size. */
@Composable
private fun BrandMark(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val stroke = MaterialTheme.colorScheme.primary
    val dot = MaterialTheme.colorScheme.secondary
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        val w = this.size.width
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.20f, w * 0.80f)
            cubicTo(w * 0.34f, w * 0.52f, w * 0.42f, w * 0.30f, w * 0.50f, w * 0.20f)
            cubicTo(w * 0.58f, w * 0.30f, w * 0.68f, w * 0.56f, w * 0.80f, w * 0.80f)
        }
        drawPath(path, stroke, style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.115f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawCircle(dot, radius = w * 0.085f, center = androidx.compose.ui.geometry.Offset(w * 0.52f, w * 0.64f))
    }
}

@Composable
private fun OnboardingScreen(modifier: Modifier, onContinue: () -> Unit, onPrivacy: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        BrandMark(84.dp)
        Text("AKZSA", style = MaterialTheme.typography.headlineLarge, letterSpacing = 2.sp)
        Text("One Mic AI Keyboard", style = MaterialTheme.typography.titleLarge)
        Text("Type. Speak. Create. More.", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
        Text("A real Android keyboard with one-tap voice typing, Grammar Fix and AI writing tools, built to stay out of your way.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        FeatureRow(Icons.Rounded.Keyboard, "Type in compatible apps", "Use the system keyboard for ordinary text, email, URLs, and numeric fields.")
        FeatureRow(Icons.Rounded.Mic, "Speak naturally", "Tap Talk, speak, and insert the final recognized phrase. Partial results are not inserted.")
        FeatureRow(Icons.Rounded.Tune, "Write better with AI", "Grammar Fix, rewrite, reply, translate and notes. You always review the result before anything changes.")
        FeatureRow(Icons.Rounded.Shield, "You stay in control", "Voice uses Android's speech recognition. Text is shared with AI tools only when you turn that on, and never from password fields.")
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Before your first dictation", fontWeight = FontWeight.SemiBold)
                Text("Android will ask for microphone permission. You must also enable AKZSA in system keyboard settings and select it with the keyboard picker. Android does not allow apps to turn on a keyboard silently.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(15.dp)) {
            Text("Set up AKZSA", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp)); Icon(Icons.Rounded.ArrowForward, contentDescription = null)
        }
        TextButton(onClick = onPrivacy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Read privacy and data-flow notes") }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    settings: AppSettings,
    permissionGranted: Boolean,
    onGrantPermission: () -> Unit,
    onEnableKeyboard: () -> Unit,
    onChooseKeyboard: () -> Unit,
    onSettings: () -> Unit,
    onDiagnostics: () -> Unit,
    onPrivacy: () -> Unit,
    onHelp: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val inputMethodManager = context.getSystemService(InputMethodManager::class.java)
    val component = remember(context) { ComponentName(context, AkzsaInputMethodService::class.java) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumeTick += 1 }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val enabled = remember(component, inputMethodManager, resumeTick) {
        inputMethodManager?.enabledInputMethodList?.any { it.id == component.flattenToString() } == true
    }
    val selected = remember(component, resumeTick) {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.contains(component.className) == true
    }
    LazyColumn(modifier = modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item {
            Column(Modifier.padding(top = 12.dp, bottom = 3.dp)) {
                Text("Type. Speak. Create. More.", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(5.dp))
                Text("One Mic AI Keyboard", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Keyboard setup", style = MaterialTheme.typography.titleLarge)
                            Text(if (selected) "AKZSA is selected" else if (enabled) "Enabled · select it to start typing" else "Enable AKZSA in Android settings", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                        StatusDot(if (selected) "Ready" else if (enabled) "Enabled" else "Setup", selected)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onEnableKeyboard, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Enable keyboard") }
                        OutlinedButton(onClick = onChooseKeyboard, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Choose keyboard") }
                    }
                    if (!enabled) Text("Step 1: enable AKZSA in system keyboard settings. Step 2: return here and choose AKZSA in the keyboard picker.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            StatusCard(
                icon = Icons.Rounded.Mic,
                title = "Microphone permission",
                detail = if (permissionGranted) "Permission granted on this device" else "Needed only for voice typing; ordinary keys still work",
                good = permissionGranted,
                actionLabel = if (permissionGranted) null else "Grant permission",
                onAction = onGrantPermission,
            )
        }
        item {
            StatusCard(
                icon = Icons.Rounded.Wifi,
                title = "AI services",
                detail = "Run a diagnostic check to see whether AKZSA's AI and voice services are reachable.",
                good = false,
                actionLabel = "Test connection",
                onAction = onDiagnostics,
                neutral = true,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniAction(Icons.Rounded.Mic, "Voice typing", "Open keyboard picker") { onChooseKeyboard() }
                MiniAction(Icons.Rounded.Code, "Diagnostics", "Check system status") { onDiagnostics() }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    HomeAction(Icons.Rounded.Settings, "Settings", "Typing, voice, AI, privacy and theme", onSettings)
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = .6f))
                    HomeAction(Icons.Rounded.PrivacyTip, "Privacy", "What stays local and what may be sent", onPrivacy)
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = .6f))
                    HomeAction(Icons.Rounded.HelpOutline, "Help and troubleshooting", "Keyboard setup, permission and service errors", onHelp)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusDot(label: String, good: Boolean) {
    Row(
        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (good) MaterialTheme.colorScheme.successTone else MaterialTheme.colorScheme.tertiary))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (good) MaterialTheme.colorScheme.successTone else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun StatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    good: Boolean,
    actionLabel: String?,
    onAction: () -> Unit,
    neutral: Boolean = false,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = if (good) MaterialTheme.colorScheme.successTone else MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(9.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (!neutral) StatusDot(if (good) "Ready" else "Action needed", good)
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (actionLabel != null) TextButton(onClick = onAction, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp, vertical = 2.dp)) {
                Text(actionLabel); Spacer(Modifier.width(5.dp)); Icon(Icons.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun RowScope.MiniAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.weight(1f), onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomeAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 13.dp, horizontal = 0.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Rounded.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    modifier: Modifier,
    settings: AppSettings,
    repository: SettingsRepository,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokenStore = remember { SecureTokenStore(context) }
    var url by remember { mutableStateOf(settings.backendBaseUrl) }
    var newToken by remember { mutableStateOf("") }
    var tokenSaved by remember { mutableStateOf(runCatching { tokenStore.hasToken() }.getOrDefault(false)) }
    var language by remember { mutableStateOf(settings.recognitionLanguage) }
    var cloudDictation by remember { mutableStateOf(settings.cloudDictationEnabled) }
    var dictionaryTerms by remember { mutableStateOf(settings.dictionaryTerms) }
    var snippetTerms by remember { mutableStateOf(settings.snippetTerms) }
    var aiPersona by remember { mutableStateOf(settings.aiPersona) }
    var streamingDictation by remember { mutableStateOf(settings.streamingDictation) }
    var noiseReduction by remember { mutableStateOf(settings.noiseReduction) }
    var spokenPunctuation by remember { mutableStateOf(settings.spokenPunctuation) }
    var shareContext by remember { mutableStateOf(settings.shareEditorContext) }
    var commandModeDefault by remember { mutableStateOf(settings.aiCommandModeDefault) }
    var appendSpace by remember { mutableStateOf(settings.appendSpaceAfterDictation) }
    var confirmEdits by remember { mutableStateOf(settings.alwaysConfirmEdits) }
    var haptics by remember { mutableStateOf(settings.hapticFeedback) }
    var smartSuggestions by remember { mutableStateOf(settings.smartSuggestions) }
    var autoCorrect by remember { mutableStateOf(settings.autoCorrect) }
    var doubleSpacePeriod by remember { mutableStateOf(settings.doubleSpacePeriod) }
    var showNumberRow by remember { mutableStateOf(settings.showNumberRow) }
    var numberHints by remember { mutableStateOf(settings.numberHints) }
    var keyboardHeight by remember { mutableStateOf(settings.keyboardHeightPercent) }
    var oneHanded by remember { mutableStateOf(settings.oneHandedMode) }
    var keySound by remember { mutableStateOf(settings.keySound) }
    var keySoundVolume by remember { mutableStateOf(settings.keySoundVolume) }
    var persistClipboard by remember { mutableStateOf(settings.persistClipboard) }
    var incognito by remember { mutableStateOf(settings.incognito) }
    var glideTyping by remember { mutableStateOf(settings.glideTyping) }
    var voiceEditCommands by remember { mutableStateOf(settings.voiceEditCommands) }
    var accentTheme by remember { mutableStateOf(settings.accentTheme) }
    var toolbarOrder by remember { mutableStateOf(settings.toolbarOrder) }
    var theme by remember { mutableStateOf(settings.theme) }
    var showResetDialog by remember { mutableStateOf(false) }
    var advancedOpen by remember { mutableStateOf(false) }

    LaunchedEffect(settings.backendBaseUrl) { url = settings.backendBaseUrl }
    LaunchedEffect(settings.recognitionLanguage) { language = settings.recognitionLanguage }
    LaunchedEffect(settings.cloudDictationEnabled) { cloudDictation = settings.cloudDictationEnabled }
    LaunchedEffect(settings.dictionaryTerms) { dictionaryTerms = settings.dictionaryTerms }
    LaunchedEffect(settings.snippetTerms) { snippetTerms = settings.snippetTerms }
    LaunchedEffect(settings.aiPersona) { aiPersona = settings.aiPersona }
    LaunchedEffect(settings.streamingDictation) { streamingDictation = settings.streamingDictation }
    LaunchedEffect(settings.noiseReduction) { noiseReduction = settings.noiseReduction }
    LaunchedEffect(settings.spokenPunctuation) { spokenPunctuation = settings.spokenPunctuation }
    LaunchedEffect(settings.shareEditorContext) { shareContext = settings.shareEditorContext }
    LaunchedEffect(settings.aiCommandModeDefault) { commandModeDefault = settings.aiCommandModeDefault }
    LaunchedEffect(settings.appendSpaceAfterDictation) { appendSpace = settings.appendSpaceAfterDictation }
    LaunchedEffect(settings.alwaysConfirmEdits) { confirmEdits = settings.alwaysConfirmEdits }
    LaunchedEffect(settings.hapticFeedback) { haptics = settings.hapticFeedback }
    LaunchedEffect(settings.smartSuggestions, settings.autoCorrect, settings.doubleSpacePeriod, settings.showNumberRow) {
        smartSuggestions = settings.smartSuggestions; autoCorrect = settings.autoCorrect
        doubleSpacePeriod = settings.doubleSpacePeriod; showNumberRow = settings.showNumberRow
    }
    LaunchedEffect(settings.numberHints, settings.keyboardHeightPercent, settings.oneHandedMode, settings.keySound,
        settings.keySoundVolume, settings.persistClipboard, settings.incognito) {
        numberHints = settings.numberHints; keyboardHeight = settings.keyboardHeightPercent; oneHanded = settings.oneHandedMode
        keySound = settings.keySound; keySoundVolume = settings.keySoundVolume
        persistClipboard = settings.persistClipboard; incognito = settings.incognito
    }
    LaunchedEffect(settings.glideTyping, settings.voiceEditCommands, settings.accentTheme, settings.toolbarOrder) {
        glideTyping = settings.glideTyping; voiceEditCommands = settings.voiceEditCommands
        accentTheme = settings.accentTheme; toolbarOrder = settings.toolbarOrder
    }
    LaunchedEffect(settings.theme) { theme = settings.theme }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text("Everything here is stored on this device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionCard("Voice") {
            Text("Voice language", fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    "en-IN" to "English (India)",
                    "hi-IN" to "Hindi (India)",
                    "kn-IN" to "Kannada (India)",
                    "hinglish" to "Hinglish (Latin)",
                    "en-US" to "English (US)",
                ).chunked(2).forEach { languageRow ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        languageRow.forEach { (value, label) ->
                            FilterChip(selected = language == value, onClick = { language = value }, label = { Text(label) })
                        }
                    }
                }
            }
            SettingToggle("Higher-accuracy cloud dictation (Whisper + AI cleanup)", "OFF by default. When enabled, AKZSA temporarily records the dictation and sends the audio to the AKZSA speech service for transcription and light cleanup. Requires internet. Kannada uses Kannada transcription; Hinglish aims to keep Latin script. For the best Hinglish results, enable cloud dictation.", cloudDictation) { cloudDictation = it }
            SettingToggle("Live cloud dictation", "ON by default when cloud dictation is enabled. Text is inserted phrase by phrase while you keep talking (after each pause). Turn off to record everything and upload once when you tap Stop.", streamingDictation) { streamingDictation = it }
            SettingToggle("Noise reduction", "Cloud dictation only: removes low rumble, uses the phone's noise suppressor when available, ignores short bursts of noise and lifts quiet speech. Turn off if your voice sounds cut or muffled on this phone.", noiseReduction) { noiseReduction = it }
            SettingToggle("Spoken punctuation", "Say \"comma\", \"full stop\", \"question mark\", \"new line\" or \"new paragraph\" (Hindi and Hinglish phrases work too) to insert the symbol instead of the words.", spokenPunctuation) { spokenPunctuation = it }
        }
        SectionCard("Typing preferences") {
            SettingToggle("Auto spacing", "Adds one trailing space after dictated text in normal text fields. Email and URL fields are excluded.", appendSpace) { appendSpace = it }
            SettingToggle("Key feedback", "Use Android keyboard tap vibration where available.", haptics) { haptics = it }
            SettingToggle("Smart suggestions", "Offline word completion, spelling candidates and next-word suggestions in the strip. Nothing you type is sent anywhere. Off in password fields.", smartSuggestions) { smartSuggestions = it }
            SettingToggle("Auto-correction", "Fixes very common typos (like \"dont\" to \"don't\") when you press space. Press backspace right after to restore what you typed. Words in your personal dictionary are never changed.", autoCorrect) { autoCorrect = it }
            SettingToggle("Double-space full stop", "Press space twice after a word to insert a full stop and a space.", doubleSpacePeriod) { doubleSpacePeriod = it }
            SettingToggle("Number row", "Show a permanent 0-9 row above the letters.", showNumberRow) { showNumberRow = it }
            SettingToggle("Number hints", "Small digits on Q to P; long-press a letter to type its digit.", numberHints) { numberHints = it }
            SettingToggle("Key sound", "Play a click sound on each key press.", keySound) { keySound = it }
            if (keySound) {
                Text("Sound volume: $keySoundVolume%", fontWeight = FontWeight.Medium)
                Slider(value = keySoundVolume.toFloat(), onValueChange = { keySoundVolume = it.toInt() }, valueRange = 0f..100f)
            }
            Text("Keyboard height: $keyboardHeight%", fontWeight = FontWeight.Medium)
            Slider(value = keyboardHeight.toFloat(), onValueChange = { keyboardHeight = it.toInt() }, valueRange = 85f..120f)
            SettingToggle("One-handed (left)", "Shrink the keyboard and keep it at the left edge.", oneHanded == "left") { oneHanded = if (it) "left" else "off" }
            SettingToggle("One-handed (right)", "Shrink the keyboard and keep it at the right edge.", oneHanded == "right") { oneHanded = if (it) "right" else "off" }
            SettingToggle("Remember clipboard", "Keep clipboard history on this device for 24 hours (pinned items stay). Passwords, one-time codes, PINs and card numbers are never kept.", persistClipboard) { persistClipboard = it }
            SettingToggle("Glide typing", "Swipe across the letters to type a word.", glideTyping) { glideTyping = it }
            SettingToggle("Voice editing commands", "Say \"delete word\", \"delete sentence\", \"select all\", \"new line\", \"new paragraph\", \"undo\" or \"heart emoji\" on its own to edit instead of typing it.", voiceEditCommands) { voiceEditCommands = it }
            Text("Accent colour", fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("default", "blue", "green", "rose", "amber").forEach { name ->
                    FilterChip(selected = accentTheme == name, onClick = { accentTheme = name }, label = { Text(name.replaceFirstChar { it.uppercase() }) })
                }
            }
            Text("Toolbar order", fontWeight = FontWeight.Medium)
            com.nefeon.akzsa.keyboard.toolbarOrderLabels(toolbarOrder).forEach { label ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(label, Modifier.weight(1f))
                    TextButton(onClick = { toolbarOrder = com.nefeon.akzsa.keyboard.moveToolbarItem(toolbarOrder, label, -1) }) { Text("▲") }
                    TextButton(onClick = { toolbarOrder = com.nefeon.akzsa.keyboard.moveToolbarItem(toolbarOrder, label, 1) }) { Text("▼") }
                }
            }
            TextButton(onClick = { toolbarOrder = "" }) { Text("Reset toolbar order") }
            SettingToggle("Incognito typing", "Remember nothing: no clipboard history and no recent emoji.", incognito) { incognito = it }
            OutlinedTextField(
                value = dictionaryTerms,
                onValueChange = { dictionaryTerms = it.take(4000) },
                label = { Text("Personal dictionary") },
                supportingText = { Text("One entry per line or comma. A plain word (a name, a brand) is used as a recognition hint and always written with your spelling and capitals. To fix a word that is often heard wrongly write: wrong = Right, for example nefion = NEFEON. Entries also appear as suggestions while you type.") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 8,
            )
            OutlinedTextField(
                value = snippetTerms,
                onValueChange = { snippetTerms = it.take(8000) },
                label = { Text("Snippets (add, edit or delete here)") },
                supportingText = { Text("Saved text you can insert from the keyboard's Snippets button, or by speaking the trigger. Everything stays on this device. Write: trigger = text. Put --- on its own line between snippets, for example:\nmy signature = Best regards,\nJordan\n---\nmy address = 221B Baker Street\nThe whole spoken phrase must match the trigger. Not used in live cloud dictation.") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 10,
            )
        }
        SectionCard("AI personalisation") {
            OutlinedTextField(
                value = aiPersona,
                onValueChange = { aiPersona = it.take(300) },
                label = { Text("Writing style") },
                supportingText = { Text("Optional. Describe your tone, for example: short and friendly, professional, uses Hinglish. It only shapes wording and is sent only when you use an AI tool. Needs sharing of selected text to be on.") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
            )
            SettingToggle("Review AI edits first", "Results always appear as a Before / After preview. Deleting or replacing text always asks for review, even if this is switched off.", confirmEdits) { confirmEdits = it }
            SettingToggle("Start in AI command mode", "The keyboard remembers this mode.", commandModeDefault) { commandModeDefault = it }
        }
        SectionCard("Privacy and security") {
            SettingToggle("Share selected and surrounding text", "Off by default. Grammar Fix, Rewrite, Reply, Translate and Notes need this on. Only the text you act on is sent, and never from password or other protected fields.", shareContext) { shareContext = it }
            Text("Protected fields (passwords, PINs, one-time codes and incognito fields) are always blocked from voice, AI tools, clipboard history and the GIF panel.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionCard("Theme") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("system" to "System", "light" to "Light", "dark" to "Dark").chunked(3).forEach { themeRow ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        themeRow.forEach { (value, label) ->
                            FilterChip(selected = theme == value, onClick = { theme = value }, label = { Text(label) })
                        }
                    }
                }
            }
        }
        SectionCard("Advanced · service connection") {
            TextButton(onClick = { advancedOpen = !advancedOpen }) { Text(if (advancedOpen) "Hide connection details" else "Show connection details") }
            if (advancedOpen) {
            OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Service address") }, supportingText = { Text("Production must use HTTPS. Do not add /v1/commands/interpret.") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = newToken, onValueChange = { newToken = it }, label = { Text("Access token") }, placeholder = { Text(if (tokenSaved) "Saved token is encrypted · leave blank to keep" else "Paste your access token") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(if (tokenSaved) "A token is stored in Android Keystore-backed encrypted storage." else "No access token saved. AI tools need one on a protected service.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    try { tokenStore.saveToken(null); tokenSaved = false; newToken = ""; onMessage("Saved token cleared") }
                    catch (_: Exception) { onMessage("Could not clear token storage") }
                }) { Text("Clear token") }
                OutlinedButton(onClick = { showResetDialog = true }) { Text("Reset settings") }
            }
            }
        }
        Button(
            onClick = {
                val check = SettingsValidation.validateBackendBaseUrl(url)
                if (!check.valid || check.normalized == null) {
                    onMessage(check.message)
                } else if (newToken.isNotBlank() && newToken.trim().length > 4096) {
                    onMessage("Token is too long")
                } else {
                    scope.launch {
                        try {
                            repository.update { it.copy(
                                backendBaseUrl = check.normalized,
                                recognitionLanguage = language,
                                cloudDictationEnabled = cloudDictation,
                                dictionaryTerms = dictionaryTerms.trim(),
                                snippetTerms = snippetTerms.trim(),
                                aiPersona = aiPersona.trim(),
                                streamingDictation = streamingDictation,
                                noiseReduction = noiseReduction,
                                spokenPunctuation = spokenPunctuation,
                                aiCommandModeDefault = commandModeDefault,
                                appendSpaceAfterDictation = appendSpace,
                                shareEditorContext = shareContext,
                                alwaysConfirmEdits = confirmEdits,
                                hapticFeedback = haptics,
                                smartSuggestions = smartSuggestions,
                                autoCorrect = autoCorrect,
                                doubleSpacePeriod = doubleSpacePeriod,
                                showNumberRow = showNumberRow,
                                numberHints = numberHints,
                                keyboardHeightPercent = keyboardHeight,
                                oneHandedMode = oneHanded,
                                keySound = keySound,
                                keySoundVolume = keySoundVolume,
                                persistClipboard = persistClipboard,
                                incognito = incognito,
                                glideTyping = glideTyping,
                                voiceEditCommands = voiceEditCommands,
                                accentTheme = accentTheme,
                                toolbarOrder = toolbarOrder,
                                theme = theme,
                            ) }
                            if (newToken.isNotBlank()) tokenStore.saveToken(newToken.trim())
                            tokenSaved = tokenStore.hasToken()
                            newToken = ""
                            onMessage("Settings saved")
                        } catch (_: Exception) {
                            onMessage("Settings could not be saved. Check Android storage and try again.")
                        }
                    }
                }
            }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
        ) { Text("Save settings") }
        Text("The access token is for private testing only. A public release needs per-user sign-in.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
    }
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset preferences?") },
            text = { Text("This restores local preferences to defaults. The saved bearer token is kept unless you clear it separately.") },
            confirmButton = { TextButton(onClick = { scope.launch { repository.reset(); showResetDialog = false; onMessage("Preferences reset") } }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun SettingToggle(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

private enum class Health { GOOD, BAD, UNKNOWN }

@Composable
private fun HealthRow(title: String, value: String, health: Health) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val tint = when (health) {
            Health.GOOD -> MaterialTheme.colorScheme.successTone
            Health.BAD -> MaterialTheme.colorScheme.error
            Health.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Box(Modifier.size(26.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(tint))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** System status. Every indicator is computed from the real state of the device or from the last real service check. */
@Composable
private fun DiagnosticsScreen(modifier: Modifier, baseUrl: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { DiagnosticStore(context) }
    var running by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var latency by remember { mutableStateOf<Long?>(null) }
    var serverVersion by remember { mutableStateOf<String?>(null) }
    var authConfigured by remember { mutableStateOf<Boolean?>(null) }
    var voiceConfigured by remember { mutableStateOf<Boolean?>(null) }
    var lastSuccess by remember { mutableStateOf(store.snapshot().lastSuccessEpochMs) }
    var authState by remember { mutableStateOf(store.snapshot().authStatus) }

    val keyboardEnabled = remember(running, outcome) {
        val imm = context.getSystemService(InputMethodManager::class.java)
        val id = ComponentName(context, AkzsaInputMethodService::class.java).flattenToString()
        imm?.enabledInputMethodList?.any { it.id == id } == true
    }
    val micGranted = remember(running, outcome) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    val online = remember(running, outcome) {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
        cm?.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
    val checked = outcome != null

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Diagnostics", style = MaterialTheme.typography.headlineMedium)
        Text("System status for the keyboard, AI and voice services. Run the report to check the services now; it sends no text and does not validate your access token.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        SectionCard("System status") {
            HealthRow("Keyboard core", if (keyboardEnabled) "Healthy · enabled in Android settings" else "AKZSA is not enabled in Android keyboard settings", if (keyboardEnabled) Health.GOOD else Health.BAD)
            HealthRow("AI services", when { !checked -> "Not checked yet · run the report"; failed -> "Unreachable"; else -> "Connected" }, when { !checked -> Health.UNKNOWN; failed -> Health.BAD; else -> Health.GOOD })
            HealthRow("Voice service", when {
                !micGranted -> "Microphone permission is needed"
                voiceConfigured == true -> "Ready"
                voiceConfigured == false -> "Cloud voice is not configured · device voice still works"
                else -> "Microphone allowed · run the report for cloud voice"
            }, when { !micGranted -> Health.BAD; voiceConfigured == false -> Health.UNKNOWN; voiceConfigured == true -> Health.GOOD; else -> Health.UNKNOWN })
            HealthRow("Network status", if (online) "Online" else "Offline", if (online) Health.GOOD else Health.BAD)
            HealthRow("Last error", if (failed) (outcome ?: "Error") else "None", if (failed) Health.BAD else Health.GOOD)
            Button(onClick = {
                running = true; outcome = null; failed = false
                scope.launch {
                    when (val result = BackendClient.checkHealth(baseUrl)) {
                        is ApiResult.Success -> {
                            outcome = "Services responded successfully"
                            failed = false
                            status = "HTTP ${result.statusCode} · ${result.value.status}"
                            latency = result.latencyMs
                            serverVersion = "${result.value.service} ${result.value.version}"
                            authConfigured = result.value.authentication_configured
                            voiceConfigured = result.value.voice_transcription_configured
                            store.recordBackendSuccess(result.statusCode, result.latencyMs)
                        }
                        is ApiResult.Failure -> {
                            outcome = result.message
                            failed = true
                            status = result.statusCode?.let { "HTTP $it" } ?: "Network error"
                            latency = result.latencyMs
                            store.recordBackendFailure(result.statusCode, result.latencyMs)
                        }
                    }
                    val snap = store.snapshot(); lastSuccess = snap.lastSuccessEpochMs; authState = snap.authStatus
                    running = false
                }
            }, enabled = !running, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                if (running) { CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(8.dp)); Text("Checking…") }
                else { Icon(Icons.Rounded.Refresh, contentDescription = null); Spacer(Modifier.width(7.dp)); Text("Run diagnostic report") }
            }
        }
        SectionCard("Details") {
            DiagnosticRow("Result", outcome ?: "Not checked in this session")
            DiagnosticRow("Status", status ?: "—")
            DiagnosticRow("Response time", latency?.let { "$it ms" } ?: "—")
            DiagnosticRow("Service version", serverVersion ?: "—")
            DiagnosticRow("Service requires sign-in", authConfigured?.let { if (it) "Yes" else "No" } ?: "—")
            DiagnosticRow("Access token", authState.replaceFirstChar { it.uppercase() } + " (this check cannot verify it)")
            DiagnosticRow("Last successful check", if (lastSuccess > 0L) formatTime(lastSuccess) else "None recorded")
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(1.15f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PrivacyScreen(modifier: Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Privacy and data flow", style = MaterialTheme.typography.headlineMedium)
        SectionCard("Voice dictation") {
            Text("With Higher-accuracy cloud dictation OFF, AKZSA uses Android SpeechRecognizer. The selected recognition service may process audio on-device or through its own network service depending on the device, language pack, and provider.")
            Text("If you explicitly turn Higher-accuracy cloud dictation ON in Settings, AKZSA records a short session to a temporary file on your phone, uploads the audio to the AKZSA speech service for transcription, which may also add punctuation, remove filler words and resolve spoken self-corrections. The service does not intentionally save the uploaded audio to disk; the temporary file on your phone is deleted after the request. This is cloud processing, not offline dictation.")
            Text("Do not dictate passwords, payment details, medical information, or confidential text in cloud mode. The backend and provider still process the audio/transcript to fulfil the request. Turn the feature OFF to return to Android's selected speech-recognition service.")
        }
        SectionCard("AI tools") {
            Text("When you use AI command mode or an AI tool (Grammar Fix, Rewrite, Reply, Translate, Notes, AI Emoji), AKZSA sends only that text to the AKZSA AI service. It does not send anything in the background.")
            Text("Selected text and nearby text are not read or included unless you explicitly enable ‘Share selected and surrounding text’ in Settings. If enabled, requests may include the selected text and up to 500 characters before and after the cursor.")
            Text("The AKZSA AI service processes the text to produce a result. Avoid using AI tools on private or sensitive information.")
        }
        SectionCard("Local storage and limitations") {
            Text("Preferences use Android DataStore. A private testing bearer token is encrypted using an Android Keystore-backed AES-GCM key. Clearing app data or losing an invalidated key may require entering the token again.")
            Text("AKZSA does not request contacts, location, SMS, call logs, or analytics permissions. This screen is a data-flow summary, not a legal privacy policy or a claim of regulatory compliance. Publish a reviewed privacy policy before public distribution.")
        }
    }
}

@Composable
private fun HelpScreen(modifier: Modifier, onOpenSettings: () -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Help and troubleshooting", style = MaterialTheme.typography.headlineMedium)
        SectionCard("Enable the keyboard") {
            Text("1. Tap Enable keyboard on Home and switch on AKZSA in Android's Input method settings.\n2. Return to AKZSA and tap Choose keyboard.\n3. Pick AKZSA in the system picker.\n4. Open a Notes app or a browser search field and tap the text field.")
        }
        SectionCard("Voice typing") {
            Text("Grant microphone permission from AKZSA first. Confirm your phone has a speech-recognition service installed and selected. Recognition may require network access. Partial text is only shown in the keyboard status; only the final result is inserted once.")
        }
        SectionCard("AI tools and service errors") {
            Text("AI tools need an internet connection and, on protected deployments, a valid access token. Open Diagnostics to run a check, then Settings to review the connection details. A successful check alone does not validate your access token.")
            Text("The app deliberately rejects unknown actions. Text rewriting requires the backend to return actual replacement text.")
        }
        SectionCard("Password and sensitive fields") {
            Text("AKZSA disables voice and AI commands in password fields. Do not use voice dictation to enter passwords, payment credentials, or other sensitive secrets.")
        }
        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Settings, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Open settings") }
    }
}

private fun formatTime(epochMs: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))

package app.omnisuite

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Change à chaque retour dans l'appli : versions installées et autorisation relues. */
    private var resumes by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT))
        window.isNavigationBarContrastEnforced = false
        // Les confirmations d'Android attendent que l'écran soit visible (une activité ne s'ouvre pas en arrière-plan)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                for (confirm in Installer.confirmations) startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        setContent {
            OmniSuiteTheme { InstallScreen(resumes) }
        }
    }

    override fun onResume() {
        super.onResume()
        resumes++
    }
}

/** Comme OmniLauncher : Material You, les couleurs suivent le fond d'écran du téléphone. */
@Composable
private fun OmniSuiteTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface, content = content)
    }
}

private val CARD = RoundedCornerShape(20.dp)

@Composable
private fun cardBackground() = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f)

@Composable
private fun InstallScreen(resumes: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshes by remember { mutableIntStateOf(0) }
    var latest by remember { mutableStateOf<Map<String, Latest>>(emptyMap()) }
    val busy by Installer.busy.collectAsState()
    val installs by Installer.installs.collectAsState()
    // Relu à chaque retour dans l'appli et après chaque installation
    val installed = remember(resumes, installs) { CATALOG.associate { it.packageName to Installer.installedVersion(context, it.packageName) } }
    val canInstall = remember(resumes) { Installer.canInstall(context) }

    LaunchedEffect(refreshes) {
        latest = emptyMap()
        latest = CATALOG.map { app -> async { app.packageName to Installer.latest(app) } }.awaitAll().toMap()
    }
    // Ouverte téléphone en veille, la première vérification échoue : on réessaie au retour dans l'appli
    LaunchedEffect(resumes) {
        if (latest.values.any { it == Latest.Offline }) refreshes++
    }

    Column(
        Modifier
            .fillMaxSize()
            // Comme les réglages de OmniLauncher : le fond d'écran reste visible derrière un voile
            .background(
                Brush.verticalGradient(
                    0f to MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                    1f to MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                ),
            )
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("OmniSuite", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { refreshes++ }) { Icon(Icons.Outlined.Refresh, contentDescription = tr("Actualiser", "Refresh")) }
        }
        if (!canInstall) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(CARD).background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr("OmniSuite n'a pas le droit d'installer des applis", "OmniSuite may not install apps"), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Button(onClick = {
                    context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName)))
                }) { Text(tr("Autoriser", "Allow")) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CATALOG.forEach { app ->
                AppCard(app, installed[app.packageName], latest[app.packageName], app.packageName in busy) {
                    val release = (latest[app.packageName] as? Latest.Found)?.release ?: return@AppCard
                    scope.launch {
                        if (!Installer.install(context, app, release)) toast(context, tr("Installation impossible", "Installation failed"))
                    }
                }
            }
        }
        Text(
            tr("Les applis viennent des releases GitHub de KrakenAgite. Chaque APK est vérifié par son empreinte SHA-256, puis par Android (même signature).",
                "Apps come from KrakenAgite's GitHub releases. Each APK is checked against its SHA-256 digest, then by Android (same signature)."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun AppCard(app: CatalogApp, installed: String?, latest: Latest?, busy: Boolean, onInstall: () -> Unit) {
    val context = LocalContext.current
    val release = (latest as? Latest.Found)?.release
    val installable = release?.apkUrl != null
    val newer = release != null && (installed == null || UpdateCheck.isNewer(release.version, installed))
    val status = buildList {
        add(installed?.let { tr("Installée : $it", "Installed: $it") } ?: tr("Non installée", "Not installed"))
        when {
            latest == null -> add(tr("Recherche…", "Checking…"))
            latest == Latest.Missing -> add(tr("Pas encore publiée", "Not published yet"))
            latest == Latest.Offline -> add(tr("GitHub injoignable", "GitHub unreachable"))
            release != null && !installable -> add(tr("${release.version} sans APK vérifiable", "${release.version} without a verifiable APK"))
            newer && installed != null -> add(tr("${release!!.version} disponible", "${release!!.version} available"))
            installed == null -> add(release!!.version)
            else -> add(tr("À jour", "Up to date"))
        }
    }.joinToString(" · ")

    Row(
        Modifier.fillMaxWidth().clip(CARD).background(cardBackground()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app, installed != null)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.name, style = MaterialTheme.typography.titleMedium)
            Text(app.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(8.dp))
        when {
            busy -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            newer && installable && installed == null -> Button(onClick = onInstall) { Text(tr("Installer", "Install")) }
            newer && installable -> FilledTonalButton(onClick = onInstall) { Text(tr("Mettre à jour", "Update")) }
            installed != null && app.packageName != context.packageName -> {
                // OmniLauncher n'a pas d'icône dans la liste des applis (écran d'accueil) : rien à ouvrir
                val open = remember(app) { context.packageManager.getLaunchIntentForPackage(app.packageName) }
                if (open != null) TextButton(onClick = { context.startActivity(open) }) { Text(tr("Ouvrir", "Open")) }
            }
        }
    }
}

/** L'icône de l'appli si elle est installée, sinon son initiale. */
@Composable
private fun AppIcon(app: CatalogApp, isInstalled: Boolean) {
    val context = LocalContext.current
    val bitmap = remember(app, isInstalled) {
        if (!isInstalled) null
        else runCatching { context.packageManager.getApplicationIcon(app.packageName).toBitmap().asImageBitmap() }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(44.dp))
    } else {
        Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(app.name.take(1), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

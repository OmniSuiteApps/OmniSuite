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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
            OmniSuiteTheme { StoreScreen(resumes) }
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

/** Fond commun des icônes Omni (`launcher_background`). */
private val OMNI_BLUE = Color(0xFF2D3A8C)

@Composable
private fun cardBackground() = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f)

private enum class Page { UPDATES, STORE }

/** Une appli du catalogue telle que la boutique la voit. */
private class Entry(val app: CatalogApp, val installed: String?, val latest: Latest?, val busy: Boolean) {
    val state = appState(installed, latest)
    val newVersion = (latest as? Latest.Found)?.release?.version
}

@Composable
private fun StoreScreen(resumes: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf(Page.UPDATES) }
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

    val entries = CATALOG.map { Entry(it, installed[it.packageName], latest[it.packageName], it.packageName in busy) }
    val updates = entries.filter { it.state == AppState.UPDATE }

    fun install(entry: Entry) {
        val release = (entry.latest as? Latest.Found)?.release ?: return
        scope.launch {
            if (!Installer.install(context, entry.app, release)) toast(context, tr("Installation impossible", "Installation failed"))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            // Comme les réglages d'OmniLauncher : le fond d'écran reste visible derrière un voile
            .background(
                Brush.verticalGradient(
                    0f to MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                    1f to MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                ),
            ),
    ) {
        Column(
            Modifier.weight(1f).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("OmniSuite", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(
                        if (page == Page.UPDATES) tr("Mises à jour", "Updates") else "Marketplace",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
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
            when (page) {
                Page.UPDATES -> UpdatesPage(entries, updates, ::install, openStore = { page = Page.STORE })
                Page.STORE -> StorePage(entries, ::install)
            }
            Spacer(Modifier.height(16.dp))
        }
        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f)) {
            NavigationBarItem(
                selected = page == Page.UPDATES,
                onClick = { page = Page.UPDATES },
                icon = {
                    BadgedBox(badge = { if (updates.isNotEmpty()) Badge { Text("${updates.size}") } }) {
                        Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
                    }
                },
                label = { Text(tr("Mises à jour", "Updates")) },
            )
            NavigationBarItem(
                selected = page == Page.STORE,
                onClick = { page = Page.STORE },
                icon = { Icon(Icons.Outlined.Storefront, contentDescription = null) },
                label = { Text("Marketplace") },
            )
        }
    }
}

@Composable
private fun UpdatesPage(entries: List<Entry>, updates: List<Entry>, install: (Entry) -> Unit, openStore: () -> Unit) {
    val self = LocalContext.current.packageName
    val installed = entries.filter { it.installed != null }
    val waiting = installed.filter { it.state == AppState.CHECKING }
    val upToDate = installed - updates.toSet() - waiting.toSet()

    if (installed.isEmpty()) {
        Message(tr("Aucune appli Omni installée", "No Omni app installed"), tr("Voir le Marketplace", "Browse the Marketplace"), openStore)
        return
    }
    if (waiting.isNotEmpty()) {
        Message(tr("Recherche des mises à jour…", "Checking for updates…"))
    } else if (updates.isEmpty()) {
        Message(tr("Tout est à jour", "Everything is up to date"))
    } else {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(CARD).background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (updates.size == 1) tr("1 mise à jour disponible", "1 update available")
                else tr("${updates.size} mises à jour disponibles", "${updates.size} updates available"),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
            // OmniSuite en dernier : sa propre mise à jour redémarre l'appli
            Button(
                onClick = { updates.sortedBy { it.app.packageName == self }.forEach(install) },
                enabled = updates.none { it.busy },
            ) { Text(tr("Tout mettre à jour", "Update all")) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            updates.forEach { entry ->
                AppRow(entry, "${entry.installed} → ${entry.newVersion}") {
                    FilledTonalButton(onClick = { install(entry) }) { Text(tr("Mettre à jour", "Update")) }
                }
            }
        }
    }
    if (upToDate.isNotEmpty()) {
        SectionTitle(tr("Installées", "Installed"))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            upToDate.forEach { entry -> AppRow(entry, statusText(entry)) { OpenButton(entry.app) } }
        }
    }
}

@Composable
private fun StorePage(entries: List<Entry>, install: (Entry) -> Unit) {
    Text(
        tr("Les applis Omni, depuis les releases GitHub de $GITHUB_OWNER", "Omni apps, from $GITHUB_OWNER's GitHub releases"),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { entry -> StoreTile(entry, install, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    Text(
        tr("Chaque APK est vérifié par son empreinte SHA-256 publiée, puis par Android (même signature).",
            "Each APK is checked against its published SHA-256 digest, then by Android (same signature)."),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
    )
}

/** Fiche du Marketplace : grande icône, nom, description, version et action. */
@Composable
private fun StoreTile(entry: Entry, install: (Entry) -> Unit, modifier: Modifier) {
    Column(
        modifier.clip(CARD).background(cardBackground()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OmniIcon(entry.app, 72.dp)
        Spacer(Modifier.height(10.dp))
        Text(entry.app.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            entry.app.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(statusText(entry), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
        Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
            when {
                entry.busy -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                entry.state == AppState.INSTALL -> Button(onClick = { install(entry) }) { Text(tr("Installer", "Install")) }
                entry.state == AppState.UPDATE -> FilledTonalButton(onClick = { install(entry) }) { Text(tr("Mettre à jour", "Update")) }
                entry.installed != null -> OpenButton(entry.app, fallback = tr("Installée", "Installed"))
            }
        }
    }
}

/** Ligne de la page Mises à jour. */
@Composable
private fun AppRow(entry: Entry, subtitle: String, action: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(CARD).background(cardBackground()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OmniIcon(entry.app, 48.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.app.name, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        if (entry.busy) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp) else action()
    }
}

/** « Ouvrir » si l'appli a une icône dans la liste des applis ; sinon [fallback] (OmniLauncher est un écran d'accueil). */
@Composable
private fun OpenButton(app: CatalogApp, fallback: String? = null) {
    val context = LocalContext.current
    val open = remember(app) {
        context.packageManager.getLaunchIntentForPackage(app.packageName).takeIf { app.packageName != context.packageName }
    }
    if (open != null) OutlinedButton(onClick = { context.startActivity(open) }) { Text(tr("Ouvrir", "Open")) }
    else if (fallback != null) Text(fallback, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun statusText(entry: Entry): String = when (entry.state) {
    AppState.CHECKING -> tr("Recherche…", "Checking…")
    AppState.MISSING -> tr("Pas encore publiée", "Not published yet")
    AppState.OFFLINE -> tr("GitHub injoignable", "GitHub unreachable")
    AppState.NO_APK -> tr("${entry.newVersion} sans APK vérifiable", "${entry.newVersion} without a verifiable APK")
    AppState.INSTALL -> tr("Version ${entry.newVersion}", "Version ${entry.newVersion}")
    AppState.UPDATE -> tr("${entry.newVersion} disponible", "${entry.newVersion} available")
    AppState.UP_TO_DATE -> tr("Version ${entry.installed} · à jour", "Version ${entry.installed} · up to date")
}

/** Icône Omni dessinée comme sur l'écran d'accueil : le glyphe blanc de l'appli sur le bleu commun. */
@Composable
private fun OmniIcon(app: CatalogApp, size: Dp) {
    Image(
        painterResource(app.icon),
        contentDescription = null,
        // Une icône adaptative ne montre que les 72 dp du centre de ses 108 : même cadrage qu'à l'écran d'accueil
        modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(OMNI_BLUE)
            .graphicsLayer { scaleX = 1.5f; scaleY = 1.5f },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun Message(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(CARD).background(cardBackground()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        if (action != null) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

package app.omnisuite

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import android.widget.Toast
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** Ce que GitHub dit de la dernière release d'une appli. */
sealed interface Latest {
    data class Found(val release: Release) : Latest
    /** Dépôt ou release absents (404) : l'appli n'est pas encore publiée. */
    data object Missing : Latest
    data object Offline : Latest
}

/**
 * Télécharge l'APK d'une release, vérifie son empreinte SHA-256 publiée, puis le confie à l'installeur d'Android (qui
 * vérifie aussi que la signature est celle de l'appli déjà installée). Android demande une confirmation la première
 * fois ; ensuite, OmniSuite étant l'installeur de l'appli, ses mises à jour passent sans question.
 */
object Installer {
    private const val TAG = "OmniSuite"
    const val EXTRA_PACKAGE = "app.omnisuite.PACKAGE"

    /** Paquets en cours de téléchargement ou d'installation : leur bouton attend. */
    val busy = MutableStateFlow<Set<String>>(emptySet())

    /** Change à chaque installation réussie : l'écran relit les versions installées. */
    val installs = MutableStateFlow(0)

    /** Fenêtres de confirmation d'Android, ouvertes par l'écran dès qu'il est visible. */
    val confirmations = Channel<Intent>(Channel.UNLIMITED)

    fun installedVersion(context: Context, packageName: String): String? =
        runCatching { context.packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()

    /** « Installer des applis inconnues » autorisé pour OmniSuite. */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    suspend fun latest(app: CatalogApp): Latest = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(app.latestUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            try {
                when (conn.responseCode) {
                    200 -> UpdateCheck.parse(conn.inputStream.bufferedReader().use { it.readText() })?.let { Latest.Found(it) } ?: Latest.Missing
                    404 -> Latest.Missing
                    else -> Latest.Offline
                }
            } finally {
                conn.disconnect()
            }
        }.onFailure { Log.w(TAG, "Release de ${app.repo} illisible", it) }.getOrDefault(Latest.Offline)
    }

    /** `false` si le téléchargement, l'empreinte ou l'ouverture de l'installation échouent. */
    suspend fun install(context: Context, app: CatalogApp, release: Release): Boolean {
        val url = release.apkUrl ?: return false
        val sha = release.sha256 ?: return false
        val pkg = app.packageName
        if (pkg in busy.value) return false
        busy.update { it + pkg }
        val ctx = context.applicationContext
        val apk = apkFile(ctx, pkg)
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                if (!download(url, apk)) error("Téléchargement impossible")
                if (!apk.inputStream().use { UpdateCheck.matches(it, sha) }) error("Empreinte différente de celle publiée")
                val installer = ctx.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(pkg)
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    session.openWrite("${app.repo}.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; session.fsync(out) }
                    val result = PendingIntent.getBroadcast(
                        ctx, id,
                        Intent(ctx, InstallResultReceiver::class.java).setPackage(ctx.packageName).putExtra(EXTRA_PACKAGE, pkg),
                        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    session.commit(result.intentSender)
                }
                true
            }.onFailure { Log.w(TAG, "Installation de $pkg impossible", it) }.getOrDefault(false)
        }
        // En cas de succès, c'est la réponse de l'installeur (InstallResultReceiver) qui libère le bouton
        if (!ok) done(ctx, pkg)
        return ok
    }

    fun done(context: Context, packageName: String) {
        apkFile(context, packageName).delete()
        busy.update { it - packageName }
    }

    private fun apkFile(context: Context, packageName: String) = File(context.cacheDir, "$packageName.apk")

    private fun download(url: String, to: File): Boolean {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 30_000 }
        try {
            if (conn.responseCode != 200) return false
            conn.inputStream.use { input -> to.outputStream().use { input.copyTo(it) } }
            return true
        } finally {
            conn.disconnect()
        }
    }
}

/** Réponse de l'installeur d'Android : confirmation à demander, succès, ou échec à signaler. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.getStringExtra(Installer.EXTRA_PACKAGE) ?: return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) Installer.confirmations.trySend(confirm) else Installer.done(context, pkg)
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Installer.done(context, pkg)
                Installer.installs.update { it + 1 }
            }
            // Bouton Annuler de la confirmation : rien à signaler
            PackageInstaller.STATUS_FAILURE_ABORTED -> Installer.done(context, pkg)
            else -> {
                Installer.done(context, pkg)
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.w("OmniSuite", "Installation de $pkg refusée : $message")
                Toast.makeText(context, tr("Installation refusée", "Installation refused") + (message?.let { " : $it" } ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }
}

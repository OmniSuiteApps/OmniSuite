package app.omnisuite

import androidx.annotation.DrawableRes
import java.io.InputStream
import java.security.MessageDigest
import org.json.JSONObject

/**
 * Une appli installable : ses releases sont sur github.com/[GITHUB_OWNER]/[repo]. [icon] : le dessin blanc de son icône
 * (copie de son `ic_launcher_foreground`). Ajouter aussi son paquet aux `<queries>` du manifeste.
 */
data class CatalogApp(
    val name: String,
    val packageName: String,
    val repo: String,
    @DrawableRes val icon: Int,
    private val fr: String,
    private val en: String,
) {
    val description: String get() = tr(fr, en)
    val latestUrl: String get() = "https://api.github.com/repos/$GITHUB_OWNER/$repo/releases/latest"
}

/** Compte GitHub de toutes les applis Omni. */
const val GITHUB_OWNER = "OmniSuiteApps"

val CATALOG = listOf(
    CatalogApp("OmniLauncher", "app.lanceur", "lanceur", R.drawable.glyph_omnilauncher, "Écran d'accueil : favoris, alphabet, recherche", "Home screen: favorites, alphabet, search"),
    CatalogApp("OmniKeyboard", "app.clavier", "OmniKeyboard", R.drawable.glyph_omnikeyboard, "Clavier personnalisable avec complétion", "Customizable keyboard with suggestions"),
    CatalogApp("OmniBackground", "app.diaporama", "diaporama", R.drawable.glyph_omnibackground, "Fond d'écran animé avec tes photos", "Live wallpaper with your photos"),
    // Elle-même : ses mises à jour apportent aussi les nouvelles applis du catalogue
    CatalogApp("OmniSuite", "app.omnisuite", "OmniSuite", R.drawable.ic_launcher_foreground, "Installe et met à jour les applis Omni", "Installs and updates the Omni apps"),
)

/** Ce que la boutique propose pour une appli, d'après sa version installée et sa dernière release. */
enum class AppState { CHECKING, MISSING, OFFLINE, NO_APK, INSTALL, UPDATE, UP_TO_DATE }

fun appState(installed: String?, latest: Latest?): AppState {
    val release = when (latest) {
        null -> return AppState.CHECKING
        Latest.Missing -> return AppState.MISSING
        Latest.Offline -> return AppState.OFFLINE
        is Latest.Found -> latest.release
    }
    val newer = installed == null || UpdateCheck.isNewer(release.version, installed)
    return when {
        !newer -> AppState.UP_TO_DATE
        release.apkUrl == null -> AppState.NO_APK
        installed == null -> AppState.INSTALL
        else -> AppState.UPDATE
    }
}

/** [apkUrl] et [sha256] : de quoi installer ; null si la release n'a pas d'APK vérifiable. */
data class Release(val version: String, val apkUrl: String? = null, val sha256: String? = null)

/** Dernière release publiée sur GitHub et comparaison avec la version installée (pur). */
object UpdateCheck {
    fun parse(json: String): Release? = runCatching {
        val root = JSONObject(json)
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
        val tag = root.getString("tag_name").removePrefix("v")
        val assets = root.optJSONArray("assets")
        val apk = assets?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk") } }
        val apkUrl = apk?.optString("browser_download_url")?.takeIf { it.startsWith("https://") }
        val sha = apk?.optString("digest")?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase()
        // Sans empreinte publiée, pas d'installation : on ne saurait pas vérifier le fichier
        if (apkUrl != null && sha != null) Release(tag, apkUrl, sha) else Release(tag)
    }.getOrNull()

    /** Le fichier téléchargé a-t-il l'empreinte publiée ? */
    fun matches(input: InputStream, sha256: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == sha256.lowercase()
    }

    /** « 1.10 » est plus récent que « 1.9.3 » ; une version illisible ne l'est jamais. */
    fun isNewer(candidate: String, installed: String): Boolean {
        val a = numbers(candidate) ?: return false
        val b = numbers(installed) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun numbers(version: String): List<Int>? =
        version.trim().split('.').map { it.toIntOrNull() ?: return null }.takeIf { it.isNotEmpty() }
}

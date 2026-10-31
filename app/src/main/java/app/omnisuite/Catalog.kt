package app.omnisuite

import androidx.compose.ui.graphics.vector.ImageVector
import java.io.InputStream
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * Une appli installable : un dépôt de github.com/[GITHUB_OWNER] dont `app/build.gradle.kts` donne l'`applicationId`.
 * Ses releases y sont publiées ; [icon] est son `ic_launcher_foreground` (null : l'initiale à la place).
 */
data class CatalogApp(
    val name: String,
    val packageName: String,
    val repo: String,
    val description: String,
    val icon: ImageVector? = null,
) {
    val latestUrl: String get() = "https://api.github.com/repos/$GITHUB_OWNER/$repo/releases/latest"
}

/** Compte GitHub de toutes les applis Omni : chacun de ses dépôts est une appli. */
const val GITHUB_OWNER = "OmniSuiteApps"

/** Un dépôt de [GITHUB_OWNER], pas encore reconnu comme appli. */
data class Repo(val name: String, val description: String, val branch: String) {
    fun rawUrl(path: String) = "https://raw.githubusercontent.com/$GITHUB_OWNER/$name/$branch/$path"
}

/** Lecture des réponses de GitHub pour trouver les applis (pur). */
object Discovery {
    const val REPOS_URL = "https://api.github.com/users/$GITHUB_OWNER/repos?per_page=100"

    /** Dépôts actifs, triés par nom ; les archives et les forks ne sont pas des applis. */
    fun repos(json: String): List<Repo> {
        val a = JSONArray(json)
        return (0 until a.length()).map { a.getJSONObject(it) }
            .filter { !it.optBoolean("archived") && !it.optBoolean("fork") }
            .map { Repo(it.getString("name"), it.optString("description").takeUnless { d -> d == "null" }.orEmpty(), it.optString("default_branch", "main")) }
            .sortedBy { it.name.lowercase() }
    }

    fun applicationId(gradle: String): String? = Regex("""applicationId\s*=\s*"([\w.]+)"""").find(gradle)?.groupValues?.get(1)
}

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

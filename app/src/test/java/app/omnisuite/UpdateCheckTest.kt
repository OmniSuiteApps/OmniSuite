package app.omnisuite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    @Test
    fun the_latest_release_is_read_from_github() {
        val json = """{"tag_name":"v1.6.2","draft":false,"prerelease":false,
            "assets":[{"name":"notes.txt","browser_download_url":"https://github.com/x/notes.txt"},
                      {"name":"lanceur-1.6.2.apk","digest":"sha256:873D8ECE","browser_download_url":"https://github.com/KrakenAgite/lanceur/releases/download/v1.6.2/lanceur-1.6.2.apk"}]}"""
        assertEquals(Release("1.6.2", "https://github.com/KrakenAgite/lanceur/releases/download/v1.6.2/lanceur-1.6.2.apk", "873d8ece"), UpdateCheck.parse(json))
        assertNull(UpdateCheck.parse("""{"message":"Not Found"}"""))
        assertNull(UpdateCheck.parse("pas du json"))
        assertNull(UpdateCheck.parse(json.replace("\"draft\":false", "\"draft\":true")))
    }

    @Test
    fun a_release_without_https_apk_or_digest_cannot_be_installed() {
        val base = """{"tag_name":"v2.0","assets":[ASSET]}"""
        assertNull(UpdateCheck.parse(base.replace("ASSET", ""))!!.apkUrl)
        assertNull(UpdateCheck.parse(base.replace("ASSET", """{"name":"a.apk","browser_download_url":"http://x/a.apk","digest":"sha256:ab"}"""))!!.apkUrl)
        assertNull(UpdateCheck.parse(base.replace("ASSET", """{"name":"a.apk","browser_download_url":"https://x/a.apk"}"""))!!.sha256)
    }

    @Test
    fun the_download_must_match_the_published_digest() {
        val bytes = "apk".toByteArray()
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertTrue(UpdateCheck.matches(bytes.inputStream(), sha))
        assertTrue(UpdateCheck.matches(bytes.inputStream(), sha.uppercase()))
        assertFalse(UpdateCheck.matches(bytes.inputStream(), "00"))
    }

    @Test
    fun versions_compare_number_by_number() {
        assertTrue(UpdateCheck.isNewer("1.10", "1.9.3"))
        assertTrue(UpdateCheck.isNewer("2.0", "1.9.9"))
        assertFalse(UpdateCheck.isNewer("1.5", "1.5.0"))
        assertFalse(UpdateCheck.isNewer("abc", "1.4.0"))
    }

    @Test
    fun every_catalog_app_has_its_own_package_and_repo() {
        assertEquals(CATALOG.size, CATALOG.map { it.packageName }.toSet().size)
        assertEquals("https://api.github.com/repos/KrakenAgite/diaporama/releases/latest", CATALOG.first { it.name == "OmniBackground" }.latestUrl)
        assertTrue(CATALOG.any { it.packageName == "app.omnisuite" })
    }
}

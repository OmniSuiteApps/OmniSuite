package app.omnisuite

import android.util.Xml
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import org.xmlpull.v1.XmlPullParser

/** Dessin vectoriel Android (`<vector>`, `<group>`, `<path>`) lu depuis le dépôt d'une appli. */
object RemoteIcon {
    private const val NS = "http://schemas.android.com/apk/res/android"

    /** null si ce n'est pas un `<vector>` (une `<adaptive-icon>` par exemple) ou s'il est illisible. */
    fun parse(xml: String): ImageVector? = runCatching {
        val p = Xml.newPullParser().apply { setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true); setInput(xml.reader()) }
        fun attr(name: String) = p.getAttributeValue(NS, name)
        fun float(name: String, default: Float) = attr(name)?.removeSuffix("dp")?.toFloatOrNull() ?: default
        var builder: ImageVector.Builder? = null
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when {
                p.eventType == XmlPullParser.START_TAG && p.name == "vector" -> builder = ImageVector.Builder(
                    defaultWidth = float("width", 108f).dp, defaultHeight = float("height", 108f).dp,
                    viewportWidth = float("viewportWidth", 108f), viewportHeight = float("viewportHeight", 108f),
                )
                builder == null -> if (p.eventType == XmlPullParser.START_TAG) return null
                p.eventType == XmlPullParser.START_TAG && p.name == "group" -> builder.addGroup(
                    scaleX = float("scaleX", 1f), scaleY = float("scaleY", 1f),
                    translationX = float("translateX", 0f), translationY = float("translateY", 0f),
                    pivotX = float("pivotX", 0f), pivotY = float("pivotY", 0f), rotate = float("rotation", 0f),
                )
                p.eventType == XmlPullParser.END_TAG && p.name == "group" -> builder.clearGroup()
                p.eventType == XmlPullParser.START_TAG && p.name == "path" -> builder.addPath(
                    pathData = PathParser().parsePathString(attr("pathData") ?: continue).toNodes(),
                    fill = SolidColor(color(attr("fillColor"))),
                    pathFillType = if (attr("fillType") == "evenOdd") PathFillType.EvenOdd else PathFillType.NonZero,
                )
            }
        }
        builder?.build()
    }.getOrNull()

    /** `#RGB`, `#RRGGBB` ou `#AARRGGBB` ; blanc sinon (une couleur de thème `@color/…` ne se lit pas d'ici). */
    private fun color(value: String?): Color {
        val hex = value?.removePrefix("#")?.takeIf { value.startsWith("#") } ?: return Color.White
        val argb = when (hex.length) {
            3 -> "FF" + hex.map { "$it$it" }.joinToString("")
            6 -> "FF$hex"
            8 -> hex
            else -> return Color.White
        }
        return Color(argb.toLong(16).toInt())
    }
}

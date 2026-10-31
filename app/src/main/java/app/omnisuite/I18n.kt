package app.omnisuite

import java.util.Locale

/** Textes en paire dans le code : anglais si la langue d'Android (ou celle choisie pour l'appli) est l'anglais, sinon français. */
fun tr(fr: String, en: String): String = if (Locale.getDefault().language == "en") en else fr

package com.jbateam.scanconvert.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * App-Sprache (§F5). Persistiert in SharedPreferences, weil sie synchron in
 * Activity.attachBaseContext() gebraucht wird (DataStore wäre dort asynchron).
 *
 * Default ohne explizite Wahl: Gerätesprache, falls unterstützt (de/es/fr/pt/ar/ja),
 * sonst Englisch. Deutsche Geräte landen so automatisch auf Deutsch.
 */
object LocaleStore {
    private const val PREFS = "scanconvert_locale"
    private const val KEY = "lang"

    /** Wählbare Sprachen (Reihenfolge = Anzeige im Sprach-Sheet). */
    val SUPPORTED = listOf("de", "en", "es", "fr", "pt", "ar", "ja")

    /** Flaggen-Ländercode je Sprache. */
    val FLAGS = mapOf(
        "de" to "de", "en" to "gb", "es" to "es",
        "fr" to "fr", "pt" to "pt", "ar" to "sa", "ja" to "jp",
    )

    /** Explizit gewählte Sprache oder null (= Gerät folgen). */
    fun saved(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun setLang(context: Context, lang: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (lang == null) remove(KEY) else putString(KEY, lang)
        }.apply()
    }

    /** Effektive Sprache: explizite Wahl, sonst Gerätesprache (wenn unterstützt), sonst Englisch. */
    fun effective(context: Context): String {
        saved(context)?.let { if (it in SUPPORTED) return it }
        val device = Locale.getDefault().language
        return if (device in SUPPORTED) device else "en"
    }

    /** Context mit der effektiven Sprache — für Activity.attachBaseContext(). */
    fun wrap(context: Context): Context {
        val locale = Locale(effective(context))
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}

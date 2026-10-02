package com.josenavarro.tvmundo.data

import java.text.Normalizer
import java.util.Locale

/** Textos en español para categorías y países de iptv-org. */
object Translations {

    private val categoryNames = mapOf(
        "animation" to "Animación",
        "auto" to "Motor",
        "business" to "Negocios",
        "classic" to "Clásicos",
        "comedy" to "Comedia",
        "cooking" to "Cocina",
        "culture" to "Cultura",
        "documentary" to "Documentales",
        "education" to "Educación",
        "entertainment" to "Entretenimiento",
        "family" to "Familia",
        "general" to "General",
        "interactive" to "Interactivos",
        "kids" to "Infantil",
        "legislative" to "Legislativos",
        "lifestyle" to "Estilo de vida",
        "movies" to "Películas",
        "music" to "Música",
        "news" to "Noticias",
        "outdoor" to "Aire libre",
        "public" to "Públicos",
        "relax" to "Relax",
        "religious" to "Religiosos",
        "science" to "Ciencia",
        "series" to "Series",
        "shop" to "Compras",
        "sports" to "Deportes",
        "travel" to "Viajes",
        "weather" to "Clima",
    )

    /** Orden en el que se muestran las temáticas (el resto va después, alfabético). */
    val categoryOrder = listOf(
        "sports", "news", "movies", "series", "entertainment", "music", "kids",
        "animation", "documentary", "general", "comedy", "classic", "culture",
    )

    /** Categorías que nunca se muestran. */
    val hiddenCategories = setOf("xxx")

    fun categoryName(id: String, fallback: String): String = categoryNames[id] ?: fallback

    private val spanish: Locale = Locale.forLanguageTag("es-ES")

    /** iptv-org usa algunos códigos que no son ISO 3166. */
    private val isoAliases = mapOf("UK" to "GB")

    fun countryName(code: String, fallback: String): String {
        val iso = isoAliases[code] ?: code
        val localized = runCatching { Locale.Builder().setRegion(iso).build().getDisplayCountry(spanish) }
            .getOrDefault("")
        return if (localized.isBlank() || localized.equals(iso, ignoreCase = true)) fallback else localized
    }

    private val combiningMarks = Regex("\\p{Mn}+")

    /** Minúsculas y sin tildes, para búsquedas. Rápido para texto ASCII (la mayoría de nombres). */
    fun normalize(text: String): String {
        if (text.all { it.code < 128 }) return text.lowercase(Locale.ROOT)
        return combiningMarks.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)
    }
}

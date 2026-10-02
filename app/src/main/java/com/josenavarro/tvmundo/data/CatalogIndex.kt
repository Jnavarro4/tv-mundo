package com.josenavarro.tvmundo.data

/** Países de habla hispana: sus canales se priorizan en filas y búsquedas. */
val SpanishSpeakingCountries = setOf(
    "CO", "MX", "AR", "ES", "CL", "PE", "VE", "EC", "BO", "PY", "UY",
    "CR", "PA", "GT", "HN", "SV", "NI", "DO", "CU", "PR", "GQ",
)

private val spanishNameMarkers = listOf(
    "espanol", "latin america", "latam", "latino", "hispan",
    "deportes", "noticias", "novelas", "peliculas", "musica",
)

/**
 * Catálogo indexado para las pantallas. Todo se precalcula una vez (en un hilo de
 * fondo) para que la interfaz no haga trabajo pesado al navegar.
 */
class CatalogIndex(catalog: Catalog, val homeCountry: String = "CO") {
    val updatedAt = catalog.updatedAt
    val channels: List<Channel> = catalog.channels
    private val byId: Map<String, Channel> = channels.associateBy { it.id }

    /** Nombre normalizado (sin tildes, minúsculas) de cada canal, en el mismo orden que [channels]. */
    val searchKeys: List<String> = channels.map { Translations.normalize(it.name) }
    private val keyById: Map<String, String> = channels.indices.associate { channels[it].id to searchKeys[it] }

    /** Nombre normalizado de un canal (precalculado). */
    fun searchKey(channel: Channel): String = keyById[channel.id] ?: Translations.normalize(channel.name)

    private val spanish: Set<String> = channels.indices
        .filter { i -> isSpanish(channels[i], searchKeys[i]) }
        .mapTo(HashSet()) { channels[it].id }

    private val score: Map<String, Int> = channels.associate { it.id to computeScore(it) }

    /** Ordena de "mejor" a "peor" opción para mostrar en una fila. */
    val ranking: Comparator<Channel> = compareByDescending<Channel> { score[it.id] ?: 0 }.thenBy { it.name }

    val byCountry: Map<String, List<Channel>> = channels.groupBy { it.country }
    val byCategory: Map<String, List<Channel>> =
        channels.flatMap { c -> c.categories.map { it to c } }.groupBy({ it.first }, { it.second })

    val countryNames: Map<String, Country> = catalog.countries.associateBy { it.code }
    val categoryNames: Map<String, Category> = catalog.categories.associateBy { it.id }

    /** País principal primero y el resto en orden alfabético. */
    val countries: List<Pair<Country, Int>> = catalog.countries
        .map { it to (byCountry[it.code]?.size ?: 0) }
        .filter { it.second > 0 }
        .sortedWith(compareBy<Pair<Country, Int>> { it.first.code != homeCountry }.thenBy { Translations.normalize(it.first.name) })

    val categories: List<Pair<Category, Int>> = catalog.categories
        .map { it to (byCategory[it.id]?.size ?: 0) }
        .filter { it.second > 0 }
        .sortedWith(
            compareBy<Pair<Category, Int>> {
                Translations.categoryOrder.indexOf(it.first.id).let { i -> if (i < 0) Int.MAX_VALUE else i }
            }.thenBy { Translations.normalize(it.first.name) }
        )

    val featured: List<Channel> = FeaturedChannels.ids.mapNotNull { resolve(it) }
    val world: List<Channel> = WorldChannels.ids.mapNotNull { resolve(it) }

    private val rankedCache = HashMap<String, List<Channel>>()

    fun channel(id: String): Channel? = byId[id]

    fun isSpanish(channel: Channel): Boolean = channel.id in spanish

    /**
     * Resuelve una entrada `id` o `id@feed`. Con feed, el canal queda solo con las
     * señales de ese feed y toma el título de la señal (p. ej. "France 24 Español").
     */
    fun resolve(entry: String): Channel? {
        val id = entry.substringBefore('@')
        val feed = entry.substringAfter('@', "").ifBlank { null }
        val channel = byId[id] ?: return null
        if (feed == null) return channel
        val streams = channel.streams.filter { it.feed.equals(feed, ignoreCase = true) }
        return if (streams.isEmpty()) channel
        else channel.copy(name = streams.first().title ?: "${channel.name} $feed", streams = streams)
    }

    /** Mejores canales de una temática (para las filas del inicio). */
    fun topInCategory(categoryId: String, limit: Int = 24): List<Channel> = synchronized(rankedCache) {
        rankedCache.getOrPut("c:$categoryId") {
            byCategory[categoryId].orEmpty().sortedWith(ranking).take(limit)
        }
    }

    /** Mejores canales de un país. */
    fun topInCountry(code: String, limit: Int = 24): List<Channel> = synchronized(rankedCache) {
        rankedCache.getOrPut("p:$code") {
            byCountry[code].orEmpty().sortedWith(ranking).take(limit)
        }
    }

    /** Puntaje de "calidad" para ordenar filas: idioma, logo, señal estable, calidad. */
    private fun computeScore(c: Channel): Int {
        var s = 0
        if (c.id in spanish) s += 50
        if (c.country == homeCountry) s += 25
        if (c.logo != null) s += 20
        val best = c.streams.first()
        if (!best.geoBlocked) s += 15
        if (!best.notAlwaysOn) s += 10
        val quality = best.quality?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
        if (quality >= 720) s += 5
        s += minOf(c.streams.size, 5) // más señales = más probable que alguna funcione
        if (c.id in featuredIds) s += 30
        return s
    }

    private companion object {
        val featuredIds: Set<String> =
            (FeaturedChannels.ids + WorldChannels.ids).mapTo(HashSet()) { it.substringBefore('@') }

        fun isSpanish(c: Channel, key: String): Boolean {
            if (c.country in SpanishSpeakingCountries) return true
            if (spanishNameMarkers.any { key.contains(it) }) return true
            val feed = Translations.normalize(c.streams.first().feed ?: "")
            return feed.startsWith("spa") || feed.startsWith("esp") || feed == "latam" || feed == "hispanicamerica"
        }
    }
}

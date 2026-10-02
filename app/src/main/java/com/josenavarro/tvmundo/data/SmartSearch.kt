package com.josenavarro.tvmundo.data

/**
 * Búsqueda "inteligente" en lenguaje natural, 100 % local y sin conexión.
 * Entiende frases como "fútbol argentino", "noticias de colombia",
 * "películas en español" o "dibujos para niños": detecta temática, país e
 * idioma y usa el resto de palabras para filtrar por nombre.
 */
class SmartSearch(private val index: CatalogIndex) {

    data class Result(
        val channels: List<Channel>,
        val category: Category? = null,
        val country: Country? = null,
        val spanishOnly: Boolean = false,
        val events: List<SuggestedEvent> = emptyList(),
    ) {
        /** Texto que explica cómo se interpretó la búsqueda, o null si fue por nombre. */
        val interpretation: String?
            get() {
                val parts = listOfNotNull(
                    category?.name,
                    country?.let { "${it.flag} ${it.name}" },
                    "En español".takeIf { spanishOnly },
                )
                return if (parts.isEmpty()) null else parts.joinToString(" · ")
            }
    }

    // Nombres de país normalizados, de más largo a más corto ("corea del sur" antes que "corea").
    private val countryNames: List<Pair<String, Country>> = index.countries
        .map { Translations.normalize(it.first.name) to it.first }
        .sortedByDescending { it.first.length }

    fun search(query: String, events: List<SuggestedEvent> = emptyList(), limit: Int = 200): Result {
        val q = Translations.normalize(query).replace(Regex("[^a-z0-9ñ ]"), " ").replace(Regex("\\s+"), " ").trim()
        if (q.isEmpty()) return Result(emptyList())

        var rest = " $q "
        var spanishOnly = false
        if (rest.contains(" en espanol ") || rest.contains(" hispan")) {
            spanishOnly = true
            rest = rest.replace(" en espanol ", " ")
        }

        var country: Country? = null
        for ((name, c) in countryNames) {
            if (rest.contains(" $name ")) {
                country = c
                rest = rest.replace(" $name ", " ")
                break
            }
        }

        val words = rest.trim().split(" ").filter { it.isNotBlank() }
        var categoryId: String? = null
        val remaining = mutableListOf<String>()
        for (w in words) {
            val demonym = demonyms[w]
            val cat = categoryWords[w]
            when {
                country == null && demonym != null && index.countryNames.containsKey(demonym) ->
                    country = index.countryNames.getValue(demonym)
                w == "espanol" || w == "espanola" -> spanishOnly = true
                categoryId == null && cat != null && index.byCategory.containsKey(cat) -> categoryId = cat
                w in stopWords -> Unit
                else -> remaining += w
            }
        }

        val category = categoryId?.let { index.categoryNames[it] }
        val matchedEvents = matchEvents(remaining, categoryId, events)

        if (category == null && country == null && !spanishOnly) {
            return Result(byName(q, index.channels.indices.toList(), limit), events = matchedEvents)
        }

        var candidates: List<Channel> = when {
            category != null && country != null ->
                index.byCategory[category.id].orEmpty().filter { it.country == country.code }
            category != null -> index.byCategory[category.id].orEmpty()
            country != null -> index.byCountry[country.code].orEmpty()
            else -> index.channels
        }
        if (spanishOnly) candidates = candidates.filter { index.isSpanish(it) }
        if (remaining.isNotEmpty()) {
            val filtered = candidates.filter { c ->
                val key = index.searchKey(c)
                remaining.all { key.contains(it) }
            }
            // Si las palabras sobrantes no coinciden con nada, se ignoran (eran "de", "ver", etc.).
            if (filtered.isNotEmpty()) candidates = filtered
        }

        return Result(
            channels = candidates.sortedWith(index.ranking).take(limit),
            category = category,
            country = country,
            spanishOnly = spanishOnly,
            events = matchedEvents,
        )
    }

    /** Coincidencia por nombre: primero los que empiezan con el texto, luego los que lo contienen. */
    private fun byName(q: String, indices: List<Int>, limit: Int): List<Channel> {
        val starts = ArrayList<Channel>()
        val contains = ArrayList<Channel>()
        for (i in indices) {
            val key = index.searchKeys[i]
            when {
                key.startsWith(q) -> starts += index.channels[i]
                key.contains(q) -> contains += index.channels[i]
            }
        }
        if (starts.isEmpty() && contains.isEmpty()) {
            // Varias palabras en cualquier orden: "rcn noticias" encuentra "Noticias RCN".
            val words = q.split(" ").filter { it.length > 1 }
            if (words.size > 1) {
                for (i in indices) {
                    if (words.all { index.searchKeys[i].contains(it) }) contains += index.channels[i]
                }
            }
        }
        return (starts.sortedWith(index.ranking) + contains.sortedWith(index.ranking)).take(limit)
    }

    /**
     * Eventos que coinciden con las palabras no interpretadas ("millonarios") o con la
     * temática pedida ("fútbol" → partidos). Las palabras de idioma o país no cuentan.
     */
    private fun matchEvents(words: List<String>, categoryId: String?, events: List<SuggestedEvent>): List<SuggestedEvent> {
        val useful = words.filter { it.length > 2 && it !in stopWords }
        val types = when (categoryId) {
            "sports" -> setOf("futbol", "deporte")
            "news" -> setOf("noticias")
            "movies" -> setOf("cine")
            "music" -> setOf("musica")
            else -> emptySet()
        }
        if (useful.isEmpty() && types.isEmpty()) return emptyList()
        return events.filter { e ->
            val key = Translations.normalize("${e.title} ${e.competition.orEmpty()} ${e.description}")
            (useful.isNotEmpty() && useful.all { key.contains(it) }) || (useful.isEmpty() && e.type in types)
        }
    }

    companion object {
        val stopWords = setOf(
            "de", "del", "la", "el", "los", "las", "en", "y", "o", "con", "para", "por", "un", "una",
            "canal", "canales", "tv", "television", "ver", "quiero", "algo", "me", "que", "mi", "a",
            "al", "lo", "hoy", "ahora", "vivo", "directo", "senal", "senales", "programa", "programas",
        )

        val categoryWords: Map<String, String> = buildMap {
            fun add(cat: String, vararg ws: String) = ws.forEach { put(it, cat) }
            add("sports", "futbol", "deporte", "deportes", "deportivo", "deportivos", "partido", "partidos", "liga", "beisbol", "basquet", "baloncesto", "tenis", "boxeo", "ciclismo")
            add("news", "noticia", "noticias", "noticiero", "noticieros", "informativo", "informativos", "actualidad")
            add("movies", "pelicula", "peliculas", "cine", "peli", "pelis", "film", "films")
            add("series", "serie", "series", "novela", "novelas", "telenovela", "telenovelas")
            add("kids", "nino", "ninos", "nina", "ninas", "infantil", "infantiles", "dibujos", "caricaturas", "chicos")
            add("animation", "animacion", "animados", "anime")
            add("music", "musica", "musical", "musicales", "videoclips", "conciertos")
            add("documentary", "documental", "documentales")
            add("comedy", "comedia", "comedias", "humor")
            add("cooking", "cocina", "recetas", "gastronomia")
            add("religious", "religion", "religiosos", "religioso", "catolico", "cristiano", "cristianos", "misa")
            add("business", "negocios", "economia", "finanzas", "bolsa")
            add("travel", "viajes", "viaje", "turismo")
            add("weather", "clima")
            add("science", "ciencia", "ciencias")
            add("education", "educacion", "educativo", "educativos")
            add("culture", "cultura", "cultural", "culturales", "arte")
            add("auto", "motor", "autos", "carros", "coches", "automovilismo")
            add("lifestyle", "moda", "estilo")
            add("family", "familia", "familiar", "familiares")
            add("shop", "compras", "tienda")
            add("classic", "clasico", "clasicos", "clasicas")
            add("entertainment", "entretenimiento", "variedades")
            add("general", "generalista", "generales")
        }

        val demonyms: Map<String, String> = buildMap {
            fun add(code: String, vararg ws: String) = ws.forEach { put(it, code) }
            add("CO", "colombiano", "colombianos", "colombiana", "colombianas")
            add("MX", "mexicano", "mexicanos", "mexicana", "mexicanas")
            add("AR", "argentino", "argentinos", "argentina", "argentinas")
            add("ES", "espana")
            add("CL", "chileno", "chilenos", "chilena", "chilenas")
            add("PE", "peruano", "peruanos", "peruana", "peruanas")
            add("VE", "venezolano", "venezolanos", "venezolana", "venezolanas")
            add("EC", "ecuatoriano", "ecuatorianos", "ecuatoriana", "ecuatorianas")
            add("US", "gringo", "gringos", "estadounidense", "estadounidenses", "usa", "eeuu")
            add("UK", "britanico", "britanicos", "ingleses")
            add("FR", "frances", "franceses", "francesa")
            add("IT", "italiano", "italianos", "italiana")
            add("DE", "aleman", "alemanes", "alemana")
            add("BR", "brasileno", "brasilenos", "brasilera", "brasileros")
            add("DO", "dominicano", "dominicanos", "dominicana")
            add("UY", "uruguayo", "uruguayos", "uruguaya")
            add("PY", "paraguayo", "paraguayos", "paraguaya")
            add("BO", "boliviano", "bolivianos", "boliviana")
            add("CU", "cubano", "cubanos", "cubana")
            add("PR", "puertorriqueno", "puertorriquenos", "boricua")
            add("JP", "japones", "japoneses", "japonesa")
            add("KR", "coreano", "coreanos", "coreana")
        }
    }
}

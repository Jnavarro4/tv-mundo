package com.josenavarro.tvmundo.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeToSequence
import java.io.InputStream

/**
 * Une los JSON de iptv-org en un [Catalog]: excluye canales NSFW, cerrados y sin
 * stream, elige el mejor logo y ordena los streams de cada canal.
 * Lee en streaming para no cargar los ~25 MB en memoria de una vez.
 */
@OptIn(ExperimentalSerializationApi::class)
class CatalogBuilder(
    private val json: Json,
    /** Abre el JSON con ese nombre de archivo (p. ej. "channels.json"). */
    private val open: (String) -> InputStream,
) {
    private fun <T> String.sequence(serializer: KSerializer<T>, block: (Sequence<T>) -> Unit) {
        open(this).buffered().use { block(json.decodeToSequence(it, serializer)) }
    }

    fun build(): Catalog {
        // 1. Streams agrupados por canal.
        val streamsByChannel = HashMap<String, MutableList<ApiStream>>()
        "streams.json".sequence(ApiStream.serializer()) { seq ->
            seq.forEach { s ->
                val id = s.channel ?: return@forEach
                streamsByChannel.getOrPut(id) { mutableListOf() }.add(s)
            }
        }

        // 2. Canales: sin NSFW, sin cerrados y solo los que tienen stream.
        val apiChannels = ArrayList<ApiChannel>()
        "channels.json".sequence(ApiChannel.serializer()) { seq ->
            seq.forEach { c ->
                if (c.isNsfw || c.closed != null || c.country.isNullOrBlank()) return@forEach
                if (c.categories.any { it in Translations.hiddenCategories }) return@forEach
                if (streamsByChannel.containsKey(c.id)) apiChannels.add(c)
            }
        }
        val keptIds = apiChannels.mapTo(HashSet()) { it.id }

        // 3. Mejor logo de cada canal.
        val bestLogo = HashMap<String, ApiLogo>()
        "logos.json".sequence(ApiLogo.serializer()) { seq ->
            seq.forEach { logo ->
                if (logo.channel !in keptIds || !isSupportedLogo(logo)) return@forEach
                val current = bestLogo[logo.channel]
                if (current == null || logoScore(logo) > logoScore(current)) bestLogo[logo.channel] = logo
            }
        }

        val channels = apiChannels.mapNotNull { c ->
            val streams = streamsByChannel.getValue(c.id)
                .distinctBy { it.url }
                .map { it.toSource() }
                .sortedWith(streamOrder)
            if (streams.isEmpty()) null else Channel(
                id = c.id,
                name = c.name,
                country = c.country!!,
                categories = c.categories,
                logo = bestLogo[c.id]?.url,
                streams = streams,
            )
        }.sortedBy { Translations.normalize(it.name) }

        // 4. Países y categorías que tienen al menos un canal.
        val usedCountries = channels.mapTo(HashSet()) { it.country }
        val countries = ArrayList<Country>()
        "countries.json".sequence(ApiCountry.serializer()) { seq ->
            seq.forEach { c ->
                if (c.code in usedCountries) {
                    countries.add(Country(c.code, Translations.countryName(c.code, c.name), c.flag ?: "🏳️"))
                }
            }
        }

        val usedCategories = channels.flatMapTo(HashSet()) { it.categories }
        val categories = ArrayList<Category>()
        "categories.json".sequence(ApiCategory.serializer()) { seq ->
            seq.forEach { c ->
                if (c.id in usedCategories && c.id !in Translations.hiddenCategories) {
                    categories.add(Category(c.id, Translations.categoryName(c.id, c.name)))
                }
            }
        }

        return Catalog(System.currentTimeMillis(), channels, countries, categories)
    }

    private fun ApiStream.toSource() = StreamSource(
        url = url,
        feed = feed,
        title = title,
        quality = quality,
        referrer = referrer?.takeIf { it.isNotBlank() },
        userAgent = userAgent?.takeIf { it.isNotBlank() },
        geoBlocked = labels.any { it.equals("Geo-blocked", ignoreCase = true) },
        notAlwaysOn = labels.any { it.contains("24/7") },
    )

    private fun isSupportedLogo(logo: ApiLogo): Boolean {
        // Coil no trae decodificador SVG y AVIF no existe en Android antiguos.
        val format = logo.format?.uppercase()
        return format != "SVG" && format != "AVIF" && !logo.url.endsWith(".svg", ignoreCase = true)
    }

    private fun logoScore(logo: ApiLogo): Int {
        var score = 0
        if (logo.feed == null) score += 4000
        if (logo.inUse) score += 2000
        score += (logo.width ?: 0).coerceAtMost(1000)
        return score
    }

    companion object {
        private fun isSpanishFeed(s: StreamSource): Boolean {
            val feed = Translations.normalize(s.feed ?: "")
            return feed.startsWith("spa") || feed.startsWith("esp")
        }

        private fun qualityValue(s: StreamSource): Int =
            s.quality?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0

        /** Primero señales en español, luego las no geobloqueadas, 24/7 y de mejor calidad. */
        val streamOrder: Comparator<StreamSource> = compareByDescending<StreamSource> { isSpanishFeed(it) }
            .thenBy { it.geoBlocked }
            .thenBy { it.notAlwaysOn }
            .thenByDescending { qualityValue(it) }
    }
}

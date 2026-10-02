package com.josenavarro.tvmundo.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.userStore by preferencesDataStore(name = "usuario")

/** Un canal visto: cuándo fue la última vez y cuántos segundos en total. */
@Serializable
data class WatchEntry(val id: String, val lastWatched: Long, val seconds: Long)

data class AppSettings(
    /** Vista previa en vivo (sin sonido) del canal enfocado en el inicio. */
    val livePreview: Boolean = true,
    /** Abrir el último canal visto al iniciar la app. */
    val autoplayLast: Boolean = false,
    /** País que aparece primero y alimenta las filas del inicio. */
    val homeCountry: String = "CO",
)

/** Historial de reproducción y ajustes, guardados con DataStore. */
class UserDataRepository(private val context: Context) {

    private val historyKey = stringPreferencesKey("historial")
    private val livePreviewKey = booleanPreferencesKey("vista_previa")
    private val autoplayKey = booleanPreferencesKey("abrir_ultimo")
    private val countryKey = stringPreferencesKey("pais_principal")

    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(WatchEntry.serializer())

    val history: Flow<List<WatchEntry>> = context.userStore.data.map { prefs ->
        prefs[historyKey]?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }.orEmpty()
    }

    val settings: Flow<AppSettings> = context.userStore.data.map { prefs ->
        AppSettings(
            livePreview = prefs[livePreviewKey] ?: true,
            autoplayLast = prefs[autoplayKey] ?: false,
            homeCountry = prefs[countryKey] ?: "CO",
        )
    }

    /** Suma [seconds] de reproducción al canal y lo marca como el más reciente. */
    suspend fun recordWatch(channelId: String, seconds: Long, now: Long = System.currentTimeMillis()) {
        context.userStore.edit { prefs ->
            val current = prefs[historyKey]?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }.orEmpty()
            prefs[historyKey] = json.encodeToString(listSerializer, WatchHistory.add(current, channelId, seconds, now))
        }
    }

    suspend fun clearHistory() {
        context.userStore.edit { it.remove(historyKey) }
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.userStore.edit { prefs ->
            val current = AppSettings(
                livePreview = prefs[livePreviewKey] ?: true,
                autoplayLast = prefs[autoplayKey] ?: false,
                homeCountry = prefs[countryKey] ?: "CO",
            )
            val next = transform(current)
            prefs[livePreviewKey] = next.livePreview
            prefs[autoplayKey] = next.autoplayLast
            prefs[countryKey] = next.homeCountry
        }
    }
}

/** Lógica pura del historial (probada en tests). */
object WatchHistory {
    const val MAX_ENTRIES = 60

    fun add(history: List<WatchEntry>, id: String, seconds: Long, now: Long): List<WatchEntry> {
        val previous = history.firstOrNull { it.id == id }
        val updated = WatchEntry(id, now, (previous?.seconds ?: 0) + seconds.coerceAtLeast(0))
        return (listOf(updated) + history.filter { it.id != id })
            .sortedByDescending { it.lastWatched }
            .take(MAX_ENTRIES)
    }

    /** "Seguir viendo": lo más reciente primero. */
    fun recent(history: List<WatchEntry>, index: CatalogIndex, limit: Int = 20): List<Channel> =
        history.sortedByDescending { it.lastWatched }.mapNotNull { index.channel(it.id) }.take(limit)
}

/**
 * Recomendaciones "Para ti": aprende de lo que ves (temáticas y países, ponderados
 * por tiempo de visión y recencia) y de tus favoritos, y propone canales parecidos
 * que todavía no has visto.
 */
object Recommender {

    fun forYou(
        index: CatalogIndex,
        history: List<WatchEntry>,
        favorites: Set<String>,
        now: Long = System.currentTimeMillis(),
        limit: Int = 24,
    ): List<Channel> {
        if (history.isEmpty() && favorites.isEmpty()) return emptyList()
        val categoryWeight = HashMap<String, Double>()
        val countryWeight = HashMap<String, Double>()

        fun learn(channel: Channel, weight: Double) {
            channel.categories.forEach { categoryWeight[it] = (categoryWeight[it] ?: 0.0) + weight }
            countryWeight[channel.country] = (countryWeight[channel.country] ?: 0.0) + weight
        }

        history.forEach { entry ->
            val channel = index.channel(entry.id) ?: return@forEach
            val days = (now - entry.lastWatched).coerceAtLeast(0) / 86_400_000.0
            val recency = 1.0 / (1.0 + days / 7.0)
            val minutes = (entry.seconds / 60.0).coerceIn(1.0, 120.0)
            learn(channel, recency * kotlin.math.ln(1.0 + minutes))
        }
        favorites.forEach { id -> index.channel(id)?.let { learn(it, 2.0) } }

        val seen = history.mapTo(HashSet()) { it.id } + favorites
        val topCategories = categoryWeight.entries.sortedByDescending { it.value }.take(4).map { it.key }
        val topCountries = countryWeight.entries.sortedByDescending { it.value }.take(3).map { it.key }

        val candidates = LinkedHashSet<Channel>()
        topCategories.forEach { cat -> candidates += index.byCategory[cat].orEmpty() }
        topCountries.forEach { code -> candidates += index.byCountry[code].orEmpty() }

        return candidates
            .asSequence()
            .filter { it.id !in seen }
            .map { c ->
                val affinity = c.categories.sumOf { categoryWeight[it] ?: 0.0 } + 1.5 * (countryWeight[c.country] ?: 0.0)
                c to affinity
            }
            .sortedWith(compareByDescending<Pair<Channel, Double>> { it.second }.thenComparator { a, b -> index.ranking.compare(a.first, b.first) })
            .map { it.first }
            .take(limit)
            .toList()
    }
}

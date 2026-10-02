package com.josenavarro.tvmundo.ui

import com.josenavarro.tvmundo.data.CatalogIndex
import com.josenavarro.tvmundo.data.Channel
import com.josenavarro.tvmundo.data.EventSchedule
import com.josenavarro.tvmundo.data.Recommender
import com.josenavarro.tvmundo.data.SuggestedEvent
import com.josenavarro.tvmundo.data.Suggestions
import com.josenavarro.tvmundo.data.WatchEntry
import com.josenavarro.tvmundo.data.WatchHistory

/** Un elemento de una fila del inicio. */
sealed interface HomeItem {
    val key: String

    data class ChannelItem(val channel: Channel, val note: String? = null) : HomeItem {
        override val key: String get() = "c:${channel.id}:${channel.streams.first().feed}"
    }

    data class EventItem(val event: SuggestedEvent, val channels: List<Channel>) : HomeItem {
        override val key: String get() = "e:${event.id}"
    }
}

data class HomeRow(
    val key: String,
    val title: String,
    val items: List<HomeItem>,
    /** Etiqueta junto al título, p. ej. "IA". */
    val badge: String? = null,
) {
    /** Canales de la fila, en orden: es la lista que recorre ▲▼ en el reproductor. */
    val playlist: List<Channel> get() = items.mapNotNull { (it as? HomeItem.ChannelItem)?.channel }
}

/** Arma las filas del inicio ("lobby"). Función pura: se prueba en tests unitarios. */
object HomeRows {

    private val categoryRows = listOf(
        "sports" to "⚽ Deportes",
        "news" to "📰 Noticias",
        "movies" to "🎬 Películas",
        "series" to "📺 Series y novelas",
        "kids" to "🧸 Infantil",
        "music" to "🎵 Música",
        "documentary" to "🎥 Documentales",
        "entertainment" to "🎭 Entretenimiento",
    )

    fun build(
        index: CatalogIndex,
        favorites: Set<String>,
        history: List<WatchEntry>,
        suggestions: Suggestions?,
        now: Long,
    ): List<HomeRow> {
        val rows = ArrayList<HomeRow>()
        fun channels(key: String, title: String, list: List<Channel>, badge: String? = null) {
            if (list.isNotEmpty()) rows += HomeRow(key, title, list.map { HomeItem.ChannelItem(it) }, badge)
        }

        val events = EventSchedule.upcoming(suggestions?.events.orEmpty(), now)
        if (events.isNotEmpty()) {
            rows += HomeRow(
                key = "eventos",
                title = "Eventos de hoy",
                items = events.map { e -> HomeItem.EventItem(e, e.channels.mapNotNull { index.resolve(it) }) },
                badge = "✨ IA",
            )
        }

        channels("seguir", "Seguir viendo", WatchHistory.recent(history, index))

        val picks = suggestions?.picks.orEmpty().mapNotNull { pick ->
            index.resolve(pick.channel)?.let { HomeItem.ChannelItem(it, pick.reason) }
        }.distinctBy { it.key }
        if (picks.isNotEmpty()) rows += HomeRow("ia", "Recomendados hoy", picks, badge = "✨ IA")

        channels("parati", "Para ti", Recommender.forYou(index, history, favorites, now))
        channels("destacados", "Destacados", index.featured)
        channels("mundo", "🌎 Los más reconocidos del mundo", index.world)
        channels("favoritos", "★ Tus favoritos", index.channels.filter { it.id in favorites }.sortedWith(index.ranking))

        val home = index.countryNames[index.homeCountry]
        if (home != null) channels("pais", "${home.flag} Lo mejor de ${home.name}", index.topInCountry(home.code))

        categoryRows.forEach { (cat, title) -> channels("cat:$cat", title, index.topInCategory(cat)) }
        return rows
    }
}

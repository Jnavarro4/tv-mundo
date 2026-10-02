package com.josenavarro.tvmundo.data

import com.josenavarro.tvmundo.ui.HomeItem
import com.josenavarro.tvmundo.ui.HomeRows
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogIndexTest {
    private val index = TestCatalog.index()

    @Test
    fun resuelveFeedEspecifico() {
        val f24 = index.resolve("France24.fr@Spanish")!!
        assertEquals("France 24 Español", f24.name)
        assertEquals(listOf("Spanish"), f24.streams.map { it.feed })
        assertNull(index.resolve("NoExiste.xx"))
        // Feed inexistente: devuelve el canal completo.
        assertEquals(2, index.resolve("France24.fr@Klingon")!!.streams.size)
    }

    @Test
    fun detectaCanalesEnEspanol() {
        assertTrue(index.isSpanish(index.channel("CaracolTV.co")!!))
        assertTrue(index.isSpanish(index.channel("ESPNDeportes.us")!!)) // por el nombre
        assertTrue(index.isSpanish(index.channel("France24.fr")!!)) // primer feed en español
        assertFalse(index.isSpanish(index.channel("CNNInt.us")!!))
    }

    @Test
    fun rankingPriorizaEspanolPaisPrincipalYLogo() {
        val sports = index.topInCategory("sports")
        assertEquals("WinSports.co", sports.first().id) // Colombia + español + logo
        assertEquals("ESPN.us", sports.last().id) // sin logo ni español
    }

    @Test
    fun paisPrincipalPrimero() {
        assertEquals("CO", TestCatalog.index("CO").countries.first().first.code)
        assertEquals("AR", TestCatalog.index("AR").countries.first().first.code)
    }
}

class SmartSearchTest {
    private val search = SmartSearch(TestCatalog.index())

    @Test
    fun porNombreSinTildesNiMayusculas() {
        val r = search.search("caracol")
        assertEquals(listOf("CaracolTV.co"), r.channels.map { it.id })
        assertNull(r.interpretation)
    }

    @Test
    fun palabrasEnCualquierOrden() {
        assertEquals("NoticiasRCN.co", search.search("rcn noticias").channels.single().id)
    }

    @Test
    fun entiendeTematicaYPais() {
        val r = search.search("Fútbol argentino")
        assertEquals("Deportes", r.category?.name)
        assertEquals("AR", r.country?.code)
        assertEquals(listOf("TyCSports.ar"), r.channels.map { it.id })
    }

    @Test
    fun entiendeNombreDePaisCompleto() {
        val r = search.search("noticias de colombia")
        assertEquals("news", r.category?.id)
        assertEquals(listOf("NoticiasRCN.co"), r.channels.map { it.id })
    }

    @Test
    fun entiendeIdiomaEspanol() {
        val r = search.search("deportes en español")
        assertTrue(r.spanishOnly)
        assertFalse(r.channels.any { it.id == "ESPN.us" })
        assertTrue(r.channels.any { it.id == "ESPNDeportes.us" })
    }

    @Test
    fun sinonimos() {
        assertEquals("movies", search.search("pelis").category?.id)
        assertEquals("kids", search.search("dibujos para niños").category?.id)
    }

    @Test
    fun buscaEnEventos() {
        val e = SuggestedEvent(id = "1", title = "Millonarios vs Nacional", competition = "Liga BetPlay", startEpoch = 0)
        assertEquals(1, search.search("millonarios", listOf(e)).events.size)
        assertEquals(0, search.search("barcelona", listOf(e)).events.size)
    }

    @Test
    fun eventosPorTematicaNoPorIdioma() {
        val noticias = SuggestedEvent(id = "n", title = "Especial", description = "Cobertura en español", type = "noticias", startEpoch = 0)
        val partido = SuggestedEvent(id = "p", title = "Final", type = "futbol", startEpoch = 0)
        assertEquals(emptyList<SuggestedEvent>(), search.search("películas en español", listOf(noticias, partido)).events)
        assertEquals(listOf("p"), search.search("fútbol", listOf(noticias, partido)).events.map { it.id })
    }
}

private val lenientJson = Json { ignoreUnknownKeys = true }

class EventScheduleTest {
    private val hour = 3_600_000L
    private val now = 1_000_000 * hour
    private fun event(id: String, startOffsetH: Double, durationMin: Int = 120) =
        SuggestedEvent(id = id, title = id, startEpoch = (now + (startOffsetH * hour).toLong()) / 1000, durationMin = durationMin)

    @Test
    fun filtraYOrdena() {
        val list = EventSchedule.upcoming(
            listOf(event("terminado", -5.0), event("manana", 20.0), event("vivo", -1.0), event("lejano", 50.0), event("pronto", 2.0)),
            now,
        )
        assertEquals(listOf("vivo", "pronto", "manana"), list.map { it.id })
        assertTrue(list.first().isLive(now))
    }

    @Test
    fun parseaJsonDelWorkflow() {
        val json = """
            {"version":1,"generado":"2026-10-02T10:00:00Z","fuente":"claude","eventos":[
              {"id":"a","titulo":"Partido","descripcion":"d","tipo":"futbol","competicion":null,
               "inicio_epoch":1790991000,"duracion_min":120,"importancia":5,"canales":["WinSports.co"],"extra":1}],
             "recomendados":[{"canal":"NTN24.co","motivo":"m"}]}
        """.trimIndent()
        val s = lenientJson.decodeFromString(Suggestions.serializer(), json)
        assertEquals("Partido", s.events.single().title)
        assertEquals(1790991000_000L, s.events.single().startMillis)
        assertEquals("⚽", s.events.single().emoji)
        assertEquals("NTN24.co", s.picks.single().channel)
    }
}

class HistoryAndRecommenderTest {
    private val index = TestCatalog.index()
    private val day = 86_400_000L

    @Test
    fun historialAcumulaYOrdena() {
        var h = WatchHistory.add(emptyList(), "A", 60, now = 1000)
        h = WatchHistory.add(h, "B", 30, now = 2000)
        h = WatchHistory.add(h, "A", 40, now = 3000)
        assertEquals(listOf("A", "B"), h.map { it.id })
        assertEquals(100, h.first().seconds)
    }

    @Test
    fun historialTieneLimite() {
        var h = emptyList<WatchEntry>()
        repeat(WatchHistory.MAX_ENTRIES + 10) { h = WatchHistory.add(h, "c$it", 10, now = it.toLong()) }
        assertEquals(WatchHistory.MAX_ENTRIES, h.size)
        assertEquals("c${WatchHistory.MAX_ENTRIES + 9}", h.first().id)
    }

    @Test
    fun recomiendaParecidosNoVistos() {
        val history = listOf(WatchEntry("WinSports.co", lastWatched = 10 * day, seconds = 3600))
        val recs = Recommender.forYou(index, history, favorites = emptySet(), now = 10 * day)
        assertFalse(recs.any { it.id == "WinSports.co" })
        // Deportes y Colombia pesan más que el resto.
        assertTrue(recs.first().id in setOf("TyCSports.ar", "ESPNDeportes.us", "NoticiasRCN.co", "CaracolTV.co"))
        assertTrue(recs.any { it.id == "TyCSports.ar" })
        assertFalse(recs.any { it.id == "Raro.jp" })
    }

    @Test
    fun sinDatosNoRecomienda() {
        assertTrue(Recommender.forYou(index, emptyList(), emptySet()).isEmpty())
    }
}

class HomeRowsTest {
    private val index = TestCatalog.index()
    private val now = 1_790_000_000_000L

    @Test
    fun filasBasicasSinIA() {
        val rows = HomeRows.build(index, favorites = emptySet(), history = emptyList(), suggestions = null, now = now)
        val keys = rows.map { it.key }
        assertFalse("eventos" in keys)
        assertFalse("seguir" in keys)
        assertTrue("pais" in keys)
        assertTrue("cat:sports" in keys)
        assertTrue(rows.all { it.items.isNotEmpty() })
    }

    @Test
    fun filasConIAHistorialYFavoritos() {
        val suggestions = Suggestions(
            events = listOf(SuggestedEvent(id = "e", title = "Final", startEpoch = now / 1000 + 600, channels = listOf("WinSports.co", "NoExiste.xx"))),
            picks = listOf(SuggestedPick("France24.fr@Spanish", "Cobertura especial"), SuggestedPick("NoExiste.xx", "x")),
        )
        val rows = HomeRows.build(
            index,
            favorites = setOf("TN.ar"),
            history = listOf(WatchEntry("CaracolTV.co", now, 600)),
            suggestions = suggestions,
            now = now,
        )
        assertEquals(listOf("eventos", "seguir", "ia", "parati"), rows.take(4).map { it.key })
        val event = rows.first().items.single() as HomeItem.EventItem
        assertEquals(listOf("WinSports.co"), event.channels.map { it.id })
        val pick = rows.first { it.key == "ia" }.items.single() as HomeItem.ChannelItem
        assertEquals("France 24 Español", pick.channel.name)
        assertEquals("Cobertura especial", pick.note)
        assertNotNull(rows.firstOrNull { it.key == "favoritos" })
    }
}

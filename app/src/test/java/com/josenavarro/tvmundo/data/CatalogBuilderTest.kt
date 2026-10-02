package com.josenavarro.tvmundo.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class CatalogBuilderTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    private fun fixtureCatalog(): Catalog = CatalogBuilder(json) { name ->
        requireNotNull(javaClass.classLoader!!.getResourceAsStream("api/$name")) { "Falta el fixture $name" }
    }.build()

    @Test
    fun excluyeNsfwCerradosYSinStream() {
        val ids = fixtureCatalog().channels.map { it.id }.toSet()
        assertEquals(setOf("CaracolTV.co", "France24.fr"), ids)
    }

    @Test
    fun ordenaStreamsYConservaCabeceras() {
        val caracol = fixtureCatalog().channels.first { it.id == "CaracolTV.co" }
        assertEquals(
            listOf("https://a.example/hd.m3u8", "https://a.example/low.m3u8", "https://a.example/geo.m3u8"),
            caracol.streams.map { it.url },
        )
        val hd = caracol.streams.first()
        assertEquals("Mozilla/5.0", hd.userAgent)
        assertEquals("https://caracol.example/", hd.referrer)
        assertTrue(caracol.streams.last().geoBlocked)
    }

    @Test
    fun prefiereFeedEnEspanol() {
        val france = fixtureCatalog().channels.first { it.id == "France24.fr" }
        assertEquals("Spanish", france.streams.first().feed)
    }

    @Test
    fun eligeLogoPrincipalSinSvg() {
        val channels = fixtureCatalog().channels
        assertEquals("https://l.example/caracol.png", channels.first { it.id == "CaracolTV.co" }.logo)
        assertNull(channels.first { it.id == "France24.fr" }.logo)
    }

    @Test
    fun paisesYCategoriasEnEspanolSoloConCanales() {
        val catalog = fixtureCatalog()
        assertEquals(listOf("CO", "FR"), catalog.countries.map { it.code })
        assertEquals("Francia", catalog.countries.first { it.code == "FR" }.name)
        assertEquals(setOf("General", "Noticias"), catalog.categories.map { it.name }.toSet())
    }

    @Test
    fun normalizaParaBuscar() {
        assertEquals("canal rcn espana", Translations.normalize("Canal RCN España"))
    }

    /**
     * Prueba con la API real. Se salta si no hay datos: descarga los JSON
     * de https://iptv-org.github.io/api/ a una carpeta y corre los tests con
     * la variable de entorno IPTV_API_DIR apuntando a ella.
     */
    @Test
    fun apiReal() {
        val dir = System.getenv("IPTV_API_DIR").orEmpty()
        assumeTrue(dir.isNotBlank() && File(dir, "channels.json").exists())
        val catalog = CatalogBuilder(json) { File(dir, it).inputStream() }.build()
        println("Canales: ${catalog.channels.size}, países: ${catalog.countries.size}, categorías: ${catalog.categories.size}")
        println("Con logo: ${catalog.channels.count { it.logo != null }}")
        assertTrue(catalog.channels.size > 1000)
        assertTrue(catalog.channels.all { it.streams.isNotEmpty() })
        assertFalse(catalog.channels.any { "xxx" in it.categories })
        assertNotNull(catalog.countries.firstOrNull { it.code == "CO" })

        val byId = catalog.channels.associateBy { it.id }
        val missing = (FeaturedChannels.ids + WorldChannels.ids).filter { entry ->
            val channel = byId[entry.substringBefore('@')]
            val feed = entry.substringAfter('@', "")
            channel == null || (feed.isNotEmpty() && channel.streams.none { it.feed == feed })
        }
        assertTrue("Destacados/reconocidos que no existen en la API: $missing", missing.isEmpty())
    }
}

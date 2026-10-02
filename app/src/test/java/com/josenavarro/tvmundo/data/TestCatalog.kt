package com.josenavarro.tvmundo.data

/** Catálogo pequeño y controlado para los tests de lógica. */
object TestCatalog {
    fun stream(url: String, feed: String? = null, title: String? = null, quality: String? = "720p", geo: Boolean = false) =
        StreamSource(url = url, feed = feed, title = title, quality = quality, geoBlocked = geo)

    fun channel(id: String, name: String, country: String, vararg categories: String, logo: Boolean = true, streams: List<StreamSource>? = null) =
        Channel(
            id = id,
            name = name,
            country = country,
            categories = categories.toList(),
            logo = if (logo) "https://logo/$id.png" else null,
            streams = streams ?: listOf(stream("https://s/$id.m3u8")),
        )

    val channels = listOf(
        channel("CaracolTV.co", "Caracol TV", "CO", "general"),
        channel("WinSports.co", "Win Sports", "CO", "sports"),
        channel("NoticiasRCN.co", "Noticias RCN", "CO", "news"),
        channel("TyCSports.ar", "TyC Sports", "AR", "sports"),
        channel("TN.ar", "TN", "AR", "news"),
        channel("ESPN.us", "ESPN", "US", "sports", logo = false),
        channel("CNNInt.us", "CNN International", "US", "news"),
        channel("ESPNDeportes.us", "ESPN Deportes", "US", "sports"),
        channel(
            "France24.fr", "France 24", "FR", "news",
            streams = listOf(
                stream("https://f/es.m3u8", feed = "Spanish", title = "France 24 Español"),
                stream("https://f/en.m3u8", feed = "English", title = "France 24 English"),
            ),
        ),
        channel("Cine.mx", "Cine Mexicano", "MX", "movies"),
        channel("Kids.es", "Clan", "ES", "kids"),
        channel("Raro.jp", "NHK Retro", "JP", "classic", logo = false,
            streams = listOf(stream("https://j/x.m3u8", quality = null, geo = true))),
    )

    val catalog = Catalog(
        updatedAt = 0,
        channels = channels,
        countries = listOf(
            Country("CO", "Colombia", "🇨🇴"), Country("AR", "Argentina", "🇦🇷"),
            Country("US", "Estados Unidos", "🇺🇸"), Country("FR", "Francia", "🇫🇷"),
            Country("MX", "México", "🇲🇽"), Country("ES", "España", "🇪🇸"), Country("JP", "Japón", "🇯🇵"),
        ),
        categories = listOf(
            Category("general", "General"), Category("sports", "Deportes"), Category("news", "Noticias"),
            Category("movies", "Películas"), Category("kids", "Infantil"), Category("classic", "Clásicos"),
        ),
    )

    fun index(home: String = "CO") = CatalogIndex(catalog, home)
}

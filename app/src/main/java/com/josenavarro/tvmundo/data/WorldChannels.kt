package com.josenavarro.tvmundo.data

/**
 * "Los más reconocidos del mundo": cadenas internacionales famosas que tienen señal
 * en iptv-org. Mismo formato que [FeaturedChannels] (`id` o `id@feed`).
 * Se prefieren las señales en español cuando existen.
 */
object WorldChannels {
    val ids = listOf(
        // Noticias internacionales
        "BBCNews.uk@LatinAmerica",
        "AlJazeera.qa@English",
        "France24.fr@Spanish",
        "DW.de@Espanol",
        "CGTNSpanish.cn",
        "EuronewsSpanish.fr",
        "BloombergTV.us",
        "SkyNews.ie",
        "NHKWorldJapan.jp",
        "CBSNews247.us",
        "NBCNewsNOW.us",
        "ReutersTV.us",
        "TRTWorld.tr",
        // Deportes
        "RedBullTV.at",
        "FIFAPlus.uk@HispanicAmerica",
        "OlympicChannel.es",
        "beINSPORTSXTRAenEspanol.us",
        "RealMadridTV.es",
        // Entretenimiento y documentales en español
        "Telemundo.us",
        "Univision.us",
        "TVEInternacionalAmerica.es",
        "NationalGeographicLatinAmerica.us",
        "HistoryLatinAmerica.us",
        "DisneyChannelLatinAmerica.ar",
        "NickelodeonLatinAmerica.us",
        "MTVLatinAmerica.us",
        "ComedyCentralLatinAmerica.us",
        "TraceLatina.fr",
        "TastemadeenEspanol.us",
        "LoveNature.ca",
    )
}

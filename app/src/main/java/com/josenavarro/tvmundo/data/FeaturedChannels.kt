package com.josenavarro.tvmundo.data

/**
 * Canales que aparecen en la pestaña "Destacados", en este orden.
 *
 * Cada entrada es el `id` del canal en https://iptv-org.github.io/api/channels.json.
 * Para canales con varias señales (idiomas) se puede fijar una con `@feed`,
 * por ejemplo "France24.fr@Spanish" (el feed sale del campo `feed` de streams.json).
 *
 * Si un id no existe o se queda sin streams, simplemente no se muestra.
 */
object FeaturedChannels {
    val ids = listOf(
        // Colombia
        "CaracolTV.co",
        "CanalRCN.co",
        "NoticiasRCN.co",
        "NTN24.co",
        "WinSports.co",
        "CitytvBogota.co",
        // México
        "LasEstrellas.mx",
        "Canal5.mx",
        "ImagenTV.mx",
        "MilenioTelevision.mx",
        "AztecaDeportesNetwork.mx",
        // Argentina
        "TN.ar",
        "Telefe.ar",
        "ElTrece.ar",
        "TyCSports.ar",
        // España
        "La1.es",
        "Antena3.es",
        "24Horas.es",
        "RealMadridTV.es",
        // Internacionales en español
        "Telemundo.us",
        "ESPNDeportes.us",
        "DW.de@Espanol",
        "France24.fr@Spanish",
        "EuronewsSpanish.fr",
    )
}

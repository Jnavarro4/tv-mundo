package com.josenavarro.tvmundo.data

import kotlinx.serialization.Serializable

/** Una URL reproducible con las cabeceras que exige ese stream. */
@Serializable
data class StreamSource(
    val url: String,
    val feed: String? = null,
    val title: String? = null,
    val quality: String? = null,
    val referrer: String? = null,
    val userAgent: String? = null,
    val geoBlocked: Boolean = false,
    val notAlwaysOn: Boolean = false,
)

/** Canal ya unido con sus streams y su logo. `streams` nunca está vacío. */
@Serializable
data class Channel(
    val id: String,
    val name: String,
    val country: String,
    val categories: List<String>,
    val logo: String? = null,
    val streams: List<StreamSource>,
)

@Serializable
data class Country(
    val code: String,
    val name: String,
    val flag: String,
)

@Serializable
data class Category(
    val id: String,
    val name: String,
)

/** Catálogo procesado y compacto que se guarda en disco. */
@Serializable
data class Catalog(
    val updatedAt: Long,
    val channels: List<Channel>,
    val countries: List<Country>,
    val categories: List<Category>,
)

package com.josenavarro.tvmundo.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Modelos de la API pública de iptv-org (https://iptv-org.github.io/api/).
 * Solo se declaran los campos que usa la app; el resto se ignora al decodificar.
 * Esquema verificado contra los JSON publicados en octubre de 2026.
 */

/** channels.json */
@Serializable
data class ApiChannel(
    val id: String,
    val name: String,
    val country: String? = null,
    val categories: List<String> = emptyList(),
    @SerialName("is_nsfw") val isNsfw: Boolean = false,
    val closed: String? = null,
)

/** streams.json */
@Serializable
data class ApiStream(
    val channel: String? = null,
    val feed: String? = null,
    val title: String? = null,
    val url: String,
    val quality: String? = null,
    val labels: List<String> = emptyList(),
    @SerialName("user_agent") val userAgent: String? = null,
    val referrer: String? = null,
)

/** countries.json */
@Serializable
data class ApiCountry(
    val name: String,
    val code: String,
    val flag: String? = null,
)

/** categories.json */
@Serializable
data class ApiCategory(
    val id: String,
    val name: String,
)

/** logos.json (los logos ya no vienen dentro de channels.json) */
@Serializable
data class ApiLogo(
    val channel: String,
    val feed: String? = null,
    @SerialName("in_use") val inUse: Boolean = true,
    val width: Int? = null,
    val height: Int? = null,
    val format: String? = null,
    val url: String,
)

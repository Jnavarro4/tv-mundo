package com.josenavarro.tvmundo.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/*
 * "Sugeridos con IA": un workflow de GitHub Actions usa Claude con búsqueda web para
 * encontrar los eventos del día (partidos, noticias, especiales) y los relaciona con
 * canales del catálogo. El resultado se publica como JSON en la rama `datos` del repo
 * y la app solo lo descarga: no hay claves de API dentro del APK.
 */

/** Archivo generado por `ia/generar_sugeridos.py`. */
@Serializable
data class Suggestions(
    @SerialName("generado") val generatedAt: String = "",
    @SerialName("fuente") val source: String = "",
    @SerialName("eventos") val events: List<SuggestedEvent> = emptyList(),
    @SerialName("recomendados") val picks: List<SuggestedPick> = emptyList(),
)

@Serializable
data class SuggestedEvent(
    val id: String,
    @SerialName("titulo") val title: String,
    @SerialName("descripcion") val description: String = "",
    /** futbol, deporte, noticias, cine, musica, especial */
    @SerialName("tipo") val type: String = "especial",
    @SerialName("competicion") val competition: String? = null,
    /** Inicio en segundos Unix (UTC). */
    @SerialName("inicio_epoch") val startEpoch: Long,
    @SerialName("duracion_min") val durationMin: Int = 120,
    /** 1 (menor) a 5 (imperdible). */
    @SerialName("importancia") val importance: Int = 3,
    /** Ids de canal (o `id@feed`) donde podría verse o seguirse. */
    @SerialName("canales") val channels: List<String> = emptyList(),
) {
    val startMillis: Long get() = startEpoch * 1000
    val endMillis: Long get() = startMillis + durationMin * 60_000L

    fun isLive(now: Long): Boolean = now in startMillis until endMillis

    val emoji: String
        get() = when (type) {
            "futbol" -> "⚽"
            "deporte" -> "🏆"
            "noticias" -> "📰"
            "cine" -> "🎬"
            "musica" -> "🎵"
            else -> "⭐"
        }
}

@Serializable
data class SuggestedPick(
    @SerialName("canal") val channel: String,
    @SerialName("motivo") val reason: String,
)

/** Reglas de qué eventos mostrar (función pura, probada en tests). */
object EventSchedule {
    const val LOOKAHEAD_MS = 36L * 60 * 60 * 1000

    /** Eventos en vivo o que empiezan en las próximas 36 h: primero los en vivo, luego por hora. */
    fun upcoming(events: List<SuggestedEvent>, now: Long): List<SuggestedEvent> =
        events
            .filter { it.endMillis > now && it.startMillis < now + LOOKAHEAD_MS }
            .sortedWith(
                compareByDescending<SuggestedEvent> { it.isLive(now) }
                    .thenBy { it.startMillis }
                    .thenByDescending { it.importance }
            )
}

@OptIn(ExperimentalSerializationApi::class)
class SuggestionsRepository(
    context: Context,
    private val client: OkHttpClient,
    private val url: String = DEFAULT_URL,
) {
    private val cacheFile = File(context.filesDir, "sugeridos.json")
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    suspend fun loadCached(): Suggestions? = withContext(Dispatchers.IO) {
        if (!cacheFile.exists()) null
        else runCatching { cacheFile.inputStream().use { json.decodeFromStream<Suggestions>(it) } }.getOrNull()
    }

    fun isStale(): Boolean =
        !cacheFile.exists() || System.currentTimeMillis() - cacheFile.lastModified() > MAX_AGE_MS

    /** Descarga la última versión; null si no hay conexión o el archivo aún no existe. */
    suspend fun refresh(): Suggestions? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).header("Cache-Control", "no-cache").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val text = response.body?.string() ?: return@runCatching null
                val parsed = json.decodeFromString<Suggestions>(text)
                cacheFile.writeText(text)
                parsed
            }
        }.getOrNull()
    }

    companion object {
        const val DEFAULT_URL = "https://raw.githubusercontent.com/Jnavarro4/tv-mundo/datos/sugeridos.json"
        const val MAX_AGE_MS = 60L * 60 * 1000
    }
}

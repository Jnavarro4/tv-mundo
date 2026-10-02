package com.josenavarro.tvmundo.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Descarga la API de iptv-org, une canales con streams y logos, y guarda en disco
 * un catálogo compacto. Los JSON originales pesan ~25 MB, así que se procesan en
 * streaming y solo se conserva lo necesario.
 */
@OptIn(ExperimentalSerializationApi::class)
class CatalogRepository(
    context: Context,
    private val client: OkHttpClient,
) {
    private val cacheFile = File(context.filesDir, "catalog.json")
    private val rawDir = File(context.cacheDir, "iptv-api")
    private val mutex = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    /** Catálogo guardado en disco, o null si no hay o está corrupto. */
    suspend fun loadCached(): Catalog? = withContext(Dispatchers.IO) {
        if (!cacheFile.exists()) return@withContext null
        runCatching {
            cacheFile.inputStream().buffered().use { json.decodeFromStream<Catalog>(it) }
        }.getOrNull()
    }

    fun isStale(catalog: Catalog?): Boolean =
        catalog == null || System.currentTimeMillis() - catalog.updatedAt > MAX_AGE_MS

    /** Descarga todo de nuevo, reconstruye el catálogo y lo guarda. */
    suspend fun refresh(): Catalog = mutex.withLock {
        withContext(Dispatchers.IO) {
            rawDir.mkdirs()
            try {
                val files = coroutineScope {
                    ENDPOINTS.map { name -> async { name to download(name) } }.awaitAll().toMap()
                }
                val catalog = CatalogBuilder(json) { name -> files.getValue(name).inputStream() }.build()
                val tmp = File(cacheFile.parentFile, "catalog.json.tmp")
                tmp.outputStream().buffered().use { json.encodeToStream(catalog, it) }
                if (!tmp.renameTo(cacheFile)) {
                    cacheFile.delete()
                    tmp.renameTo(cacheFile)
                }
                catalog
            } finally {
                rawDir.deleteRecursively()
            }
        }
    }

    private fun download(name: String): File {
        val target = File(rawDir, name)
        val request = Request.Builder().url("$API_BASE/$name").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} al descargar $name")
            val body = response.body ?: throw IOException("Respuesta vacía en $name")
            target.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
        return target
    }

    companion object {
        const val API_BASE = "https://iptv-org.github.io/api"
        val ENDPOINTS = listOf("channels.json", "streams.json", "countries.json", "categories.json", "logos.json")
        const val MAX_AGE_MS = 12L * 60 * 60 * 1000
    }
}

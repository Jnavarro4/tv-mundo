package com.josenavarro.tvmundo.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.josenavarro.tvmundo.data.StreamSource

/** Crea la fuente de ExoPlayer respetando el user_agent y el http_referrer de cada stream. */
@OptIn(UnstableApi::class)
object StreamMediaSource {

    /** Agente por defecto cuando el stream no exige uno: muchos servidores rechazan agentes desconocidos. */
    private const val DEFAULT_USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"

    fun create(source: StreamSource): MediaSource {
        val headers = buildMap {
            source.referrer?.let {
                put("Referer", it)
                originOf(it)?.let { origin -> put("Origin", origin) }
            }
        }
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(source.userAgent ?: DEFAULT_USER_AGENT)
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)

        val uri = Uri.parse(source.url)
        val item = MediaItem.Builder().setUri(uri).apply {
            // La mayoría de streams de iptv-org son HLS aunque la URL no termine en .m3u8.
            if (Util.inferContentType(uri) == C.CONTENT_TYPE_OTHER && !looksProgressive(source.url)) {
                setMimeType(MimeTypes.APPLICATION_M3U8)
            }
        }.build()

        return DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(item)
    }

    private fun looksProgressive(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return listOf(".mp4", ".ts", ".mkv", ".mp3", ".aac", ".flv", ".webm").any { path.endsWith(it) }
    }

    private fun originOf(url: String): String? = runCatching {
        val u = Uri.parse(url)
        if (u.scheme == null || u.host == null) null
        else buildString {
            append(u.scheme).append("://").append(u.host)
            if (u.port != -1) append(':').append(u.port)
        }
    }.getOrNull()
}

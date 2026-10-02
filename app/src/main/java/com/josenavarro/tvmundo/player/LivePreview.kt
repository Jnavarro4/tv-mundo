package com.josenavarro.tvmundo.player

import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.josenavarro.tvmundo.R
import com.josenavarro.tvmundo.data.Channel
import com.josenavarro.tvmundo.ui.components.ChannelLogo
import com.josenavarro.tvmundo.ui.components.LiveBadge
import kotlinx.coroutines.delay

/**
 * Vista previa en vivo y sin sonido del canal enfocado (como Pluto TV / Samsung TV Plus).
 * Espera a que el foco se quede quieto [dwellMs] antes de conectar, para no abrir
 * decenas de streams al recorrer una fila. Mientras tanto muestra el logo.
 */
@OptIn(UnstableApi::class)
@Composable
fun LivePreview(
    channel: Channel?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    dwellMs: Long = 1_200,
) {
    val context = LocalContext.current
    var showingVideo by remember { mutableStateOf(false) }
    // Si una señal falla se prueba la siguiente (máximo 3), como en el reproductor.
    var attempt by remember { mutableStateOf(0) }
    var loadedChannel by remember { mutableStateOf<String?>(null) }
    val videoAlpha by animateFloatAsState(if (showingVideo) 1f else 0f, label = "preview")

    val player = remember {
        ExoPlayer.Builder(context)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(2_000, 8_000, 800, 1_500)
                    .build()
            )
            .build()
            .apply {
                volume = 0f
                playWhenReady = true
            }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                showingVideo = true
            }

            override fun onPlayerError(error: PlaybackException) {
                showingVideo = false
                attempt++
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(channel?.id, enabled, attempt) {
        showingVideo = false
        player.stop()
        player.clearMediaItems()
        if (channel?.id != loadedChannel) {
            loadedChannel = channel?.id
            // Canal nuevo: se reinicia el contador (eso vuelve a lanzar este efecto).
            if (attempt != 0) {
                attempt = 0
                return@LaunchedEffect
            }
        }
        if (!enabled || channel == null) return@LaunchedEffect
        val stream = channel.streams.getOrNull(attempt)?.takeIf { attempt < 3 } ?: return@LaunchedEffect
        if (attempt == 0) delay(dwellMs)
        player.setMediaSource(StreamMediaSource.create(stream, fastFail = true))
        player.prepare()
    }

    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Color.Black)) {
        if (channel != null) {
            ChannelLogo(channel, Modifier.fillMaxSize(), padding = 28.dp, initialsSize = 44)
        }
        AndroidView(
            factory = { ctx ->
                (android.view.LayoutInflater.from(ctx).inflate(R.layout.preview_player_view, null) as PlayerView).apply {
                    this.player = player
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .alpha(videoAlpha),
        )
        if (showingVideo) {
            LiveBadge(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
            )
        }
    }
}


package com.josenavarro.tvmundo.player

import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.josenavarro.tvmundo.data.Channel
import com.josenavarro.tvmundo.data.Country
import com.josenavarro.tvmundo.ui.components.RequestFocusWhenReady
import com.josenavarro.tvmundo.ui.theme.TvColors
import kotlinx.coroutines.delay

/**
 * Reproductor a pantalla completa.
 * - Arriba / abajo (o CH+ / CH-): canal anterior / siguiente de la lista.
 * - OK: muestra u oculta la información del canal.
 * - Mantener OK (o botón Menú): agregar / quitar de Favoritos.
 * - Atrás: volver.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    title: String,
    channels: List<Channel>,
    startIndex: Int,
    favorites: Set<String>,
    countryOf: (String) -> Country?,
    onToggleFavorite: (Channel) -> Unit,
    onChannelChanged: (Channel) -> Unit,
) {
    val context = LocalContext.current
    var index by rememberSaveable { mutableIntStateOf(startIndex.coerceIn(0, channels.lastIndex)) }
    val channel = channels[index]
    var streamIndex by remember(index) { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingError by remember { mutableStateOf<PlaybackException?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var overlayVisible by remember { mutableStateOf(true) }
    var overlayNonce by remember { mutableIntStateOf(0) }

    val player = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = true }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                pendingError = error
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    fun showOverlay() {
        overlayVisible = true
        overlayNonce++
    }

    fun changeChannel(delta: Int) {
        if (channels.size <= 1) return
        index = (index + delta + channels.size) % channels.size
    }

    // Cargar el stream actual.
    LaunchedEffect(index, streamIndex) {
        errorMessage = null
        buffering = true
        player.stop()
        player.setMediaSource(StreamMediaSource.create(channel.streams[streamIndex]))
        player.prepare()
        player.play()
    }

    LaunchedEffect(index) {
        onChannelChanged(channel)
        showOverlay()
    }

    // Si un stream falla se prueba el siguiente del mismo canal; si no quedan, se avisa.
    LaunchedEffect(pendingError) {
        val error = pendingError ?: return@LaunchedEffect
        pendingError = null
        if (streamIndex < channel.streams.lastIndex) {
            streamIndex++
        } else {
            player.stop()
            errorMessage = describe(error)
        }
    }

    // El overlay se oculta solo a los 5 segundos.
    LaunchedEffect(overlayNonce) {
        if (overlayVisible) {
            delay(5_000)
            overlayVisible = false
        }
    }

    val rootFocus = remember { FocusRequester() }
    var centerLongPressed by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onKeyEvent { event ->
                val native = event.nativeKeyEvent
                val down = native.action == KeyEvent.ACTION_DOWN
                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                        if (down) changeChannel(-1)
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        if (down) changeChannel(+1)
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (down) {
                            if (native.repeatCount == 0) {
                                centerLongPressed = false
                            } else if (!centerLongPressed) {
                                centerLongPressed = true
                                onToggleFavorite(channel)
                                showOverlay()
                            }
                        } else if (!centerLongPressed) {
                            if (overlayVisible) overlayVisible = false else showOverlay()
                        }
                        true
                    }

                    KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BOOKMARK -> {
                        if (down && native.repeatCount == 0) {
                            onToggleFavorite(channel)
                            showOverlay()
                        }
                        true
                    }

                    KeyEvent.KEYCODE_INFO -> {
                        if (down) showOverlay()
                        true
                    }

                    else -> false
                }
            }
            .focusable(),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    keepScreenOn = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    this.player = player
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (buffering && errorMessage == null) {
            Text(
                "Cargando…",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0x99000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }

        AnimatedVisibility(
            visible = overlayVisible && errorMessage == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ChannelOverlay(
                channel = channel,
                country = countryOf(channel.country),
                position = "${index + 1} / ${channels.size} · $title",
                isFavorite = channel.id in favorites,
                streamInfo = channel.streams[streamIndex].quality,
            )
        }

        errorMessage?.let { message ->
            ErrorPanel(
                channelName = channel.name,
                message = message,
                canSkip = channels.size > 1,
                onNext = { changeChannel(+1) },
                onRetry = {
                    errorMessage = null
                    if (streamIndex == 0) {
                        // Mismo índice: forzar la recarga.
                        player.setMediaSource(StreamMediaSource.create(channel.streams[0]))
                        player.prepare()
                        player.play()
                    } else {
                        streamIndex = 0
                    }
                },
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }

    // Cuando no hay panel de error, el contenedor raíz recibe las teclas.
    RequestFocusWhenReady(rootFocus, errorMessage, enabled = errorMessage == null)
}

@Composable
private fun ChannelOverlay(
    channel: Channel,
    country: Country?,
    position: String,
    isFavorite: Boolean,
    streamInfo: String?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
            .padding(start = 48.dp, end = 48.dp, top = 60.dp, bottom = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 160.dp, height = 90.dp)
                .background(TvColors.LogoBackground, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (channel.logo != null) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                )
            }
        }
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    channel.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                if (isFavorite) {
                    Text("  ★", style = MaterialTheme.typography.headlineMedium, color = TvColors.Accent)
                }
            }
            val countryText = country?.let { "${it.flag} ${it.name}" } ?: channel.country
            Text(
                listOfNotNull(countryText, streamInfo).joinToString(" · "),
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFDDDDDD),
            )
            Text(position, style = MaterialTheme.typography.bodyMedium, color = TvColors.OnSurfaceDim)
        }
        Text(
            "▲▼ cambiar canal\nOK ocultar · mantener OK: favorito\nAtrás: volver",
            style = MaterialTheme.typography.bodySmall,
            color = TvColors.OnSurfaceDim,
        )
    }
}

@Composable
private fun ErrorPanel(
    channelName: String,
    message: String,
    canSkip: Boolean,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstButton = remember { FocusRequester() }
    Column(
        modifier
            .width(560.dp)
            .background(Color(0xF01A1F27), RoundedCornerShape(16.dp))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "No se pudo reproducir «$channelName»",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = TvColors.OnSurfaceDim,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (canSkip) {
                Button(onClick = onNext, modifier = Modifier.focusRequester(firstButton)) {
                    Text("Siguiente canal")
                }
                OutlinedButton(onClick = onRetry) { Text("Reintentar") }
            } else {
                Button(onClick = onRetry, modifier = Modifier.focusRequester(firstButton)) {
                    Text("Reintentar")
                }
            }
        }
        Text(
            "▲▼ también cambian de canal · Atrás para volver",
            style = MaterialTheme.typography.bodySmall,
            color = TvColors.OnSurfaceDim,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
    RequestFocusWhenReady(firstButton, message)
}

private fun describe(error: PlaybackException): String = when (error.errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
        "No hay conexión con el servidor del canal."

    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
        "El servidor rechazó la conexión (puede estar caído o bloqueado en tu país)."

    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
        "El stream ya no existe."

    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
        "El formato del stream no es compatible con este equipo."

    else -> "El canal no está disponible en este momento (${error.errorCodeName})."
}

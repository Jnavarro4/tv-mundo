package com.josenavarro.tvmundo.player

import android.os.SystemClock
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.josenavarro.tvmundo.data.Channel
import com.josenavarro.tvmundo.data.Country
import com.josenavarro.tvmundo.ui.components.ChannelLogo
import com.josenavarro.tvmundo.ui.components.LiveBadge
import com.josenavarro.tvmundo.ui.components.RequestFocusWhenReady
import com.josenavarro.tvmundo.ui.theme.TvColors
import kotlinx.coroutines.delay

/**
 * Reproductor a pantalla completa.
 * - Arriba / abajo (o CH+ / CH-): canal anterior / siguiente de la lista.
 * - Izquierda: lista de canales para saltar directo a otro.
 * - OK: muestra u oculta la información del canal.
 * - Mantener OK (o botón Menú): agregar / quitar de Favoritos.
 * - Atrás: cerrar la lista o volver.
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
    onWatched: (Channel, Long) -> Unit,
    onExit: () -> Unit,
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
    var listVisible by remember { mutableStateOf(false) }
    var resolution by remember { mutableStateOf<String?>(null) }

    // Tiempo de reproducción real del canal actual (para el historial y "Para ti").
    var playingSince by remember { mutableLongStateOf(0L) }
    val currentChannel by rememberUpdatedState(channel)
    val watched by rememberUpdatedState(onWatched)
    fun flushWatchTime() {
        if (playingSince > 0) {
            val seconds = (SystemClock.elapsedRealtime() - playingSince) / 1000
            playingSince = 0
            if (seconds >= 5) watched(currentChannel, seconds)
        }
    }

    val player = remember {
        // Si la TV no trae decodificador para el audio (MP2, AC-3…), se usa FFmpeg por software.
        val renderers = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        ExoPlayer.Builder(context, renderers)
            // Arranque y zapping más rápidos que los valores por defecto (2,5 s de búfer inicial).
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(15_000, 50_000, 1_200, 2_500)
                    .build()
            )
            .build()
            .apply { playWhenReady = true }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                pendingError = error
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    if (playingSince == 0L) playingSince = SystemClock.elapsedRealtime()
                } else {
                    flushWatchTime()
                }
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                resolution = if (videoSize.height > 0) "${videoSize.height}p" else null
            }
        }
        player.addListener(listener)
        onDispose {
            flushWatchTime()
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
        resolution = null
        flushWatchTime()
        player.stop()
        player.setMediaSource(StreamMediaSource.create(channel.streams[streamIndex], fastFail = channel.streams.size > 1))
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
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            // El directo avanzó más que el búfer: se vuelve al punto en vivo.
            player.seekToDefaultPosition()
            player.prepare()
            return@LaunchedEffect
        }
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
            // Atrás se maneja aquí: si no, Compose lo usa para sacar el foco de los botones
            // del panel de error o de la lista hacia este contenedor y la tecla "se pierde".
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_BACK) return@onPreviewKeyEvent false
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                    if (listVisible) listVisible = false else onExit()
                }
                true
            }
            .onKeyEvent { event ->
                if (listVisible) return@onKeyEvent false
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

                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        // Con el panel de error visible, ◀ ▶ se mueven entre sus botones.
                        if (errorMessage != null) {
                            false
                        } else {
                            if (down) listVisible = true
                            true
                        }
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
            LoadingIndicator(channel, Modifier.align(Alignment.Center))
        }

        AnimatedVisibility(
            visible = overlayVisible && errorMessage == null && !listVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ChannelOverlay(
                channel = channel,
                number = index + 1,
                total = channels.size,
                listTitle = title,
                country = countryOf(channel.country),
                isFavorite = channel.id in favorites,
                quality = resolution ?: channel.streams[streamIndex].quality,
                next = channels.getOrNull((index + 1) % channels.size)?.takeIf { channels.size > 1 },
            )
        }

        AnimatedVisibility(
            visible = listVisible,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            ChannelListPanel(
                title = title,
                channels = channels,
                current = index,
                favorites = favorites,
                onSelect = {
                    index = it
                    listVisible = false
                },
                onClose = { listVisible = false },
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

    // Cuando no hay panel de error ni lista, el contenedor raíz recibe las teclas.
    RequestFocusWhenReady(rootFocus, errorMessage to listVisible, enabled = errorMessage == null && !listVisible)
}

@Composable
private fun LoadingIndicator(channel: Channel, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "loading")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ChannelLogo(
            channel,
            Modifier
                .size(width = 192.dp, height = 108.dp)
                .clip(RoundedCornerShape(12.dp))
                .alpha(pulse),
            padding = 14.dp,
        )
        Text(
            "Conectando con ${channel.name}…",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

@Composable
private fun ChannelOverlay(
    channel: Channel,
    number: Int,
    total: Int,
    listTitle: String,
    country: Country?,
    isFavorite: Boolean,
    quality: String?,
    next: Channel?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE000000))))
            .padding(start = 48.dp, end = 48.dp, top = 70.dp, bottom = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(
            channel,
            Modifier
                .size(width = 160.dp, height = 90.dp)
                .clip(RoundedCornerShape(10.dp)),
            padding = 10.dp,
        )
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$number",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TvColors.Accent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 14.dp),
                )
                Text(
                    channel.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isFavorite) {
                    Text("  ★", style = MaterialTheme.typography.headlineMedium, color = TvColors.Accent)
                }
                Spacer(Modifier.width(14.dp))
                LiveBadge()
            }
            val countryText = country?.let { "${it.flag} ${it.name}" } ?: channel.country
            Text(
                listOfNotNull(countryText, quality).joinToString(" · "),
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFDDDDDD),
            )
            Text(
                "$number de $total · $listTitle" + (next?.let { " · Siguiente: ${it.name}" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = TvColors.OnSurfaceDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "▲▼ cambiar canal · ◀ lista\nOK ocultar · mantener OK: favorito\nAtrás: volver",
            style = MaterialTheme.typography.bodySmall,
            color = TvColors.OnSurfaceDim,
        )
    }
}

/** Lista lateral de canales (como en TiviMate): salto directo a cualquier canal. */
@Composable
private fun ChannelListPanel(
    title: String,
    channels: List<Channel>,
    current: Int,
    favorites: Set<String>,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
    val currentItem = remember { FocusRequester() }
    Column(
        Modifier
            .fillMaxHeight()
            .width(380.dp)
            .background(Brush.horizontalGradient(listOf(Color(0xF20E1116), Color(0xD90E1116))))
            .onKeyEvent {
                // ▶ cierra la lista.
                if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    onClose()
                    true
                } else false
            },
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp),
        )
        Text(
            "${channels.size} canales · OK para cambiar · ▶ cerrar",
            style = MaterialTheme.typography.bodySmall,
            color = TvColors.OnSurfaceDim,
            modifier = Modifier.padding(start = 24.dp, bottom = 10.dp),
        )
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(channels, key = { i, c -> "$i:${c.id}" }) { i, c ->
                Surface(
                    onClick = { onSelect(i) },
                    modifier = (if (i == current) Modifier.focusRequester(currentItem) else Modifier).fillMaxWidth(),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (i == current) Color(0x33FFB300) else Color.Transparent,
                        contentColor = Color.White,
                        focusedContainerColor = TvColors.OnSurface,
                        focusedContentColor = Color.Black,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${i + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.width(36.dp),
                        )
                        ChannelLogo(
                            c,
                            Modifier
                                .size(width = 56.dp, height = 32.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            padding = 3.dp,
                            initialsSize = 12,
                        )
                        Text(
                            (if (c.id in favorites) "★ " else "") + c.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        }
    }
    RequestFocusWhenReady(currentItem, Unit)
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

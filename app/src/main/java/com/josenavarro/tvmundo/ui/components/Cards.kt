package com.josenavarro.tvmundo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.josenavarro.tvmundo.data.Channel
import com.josenavarro.tvmundo.data.SuggestedEvent
import com.josenavarro.tvmundo.ui.theme.TvColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val CardShape = RoundedCornerShape(12.dp)

@Composable
fun TvCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    focusedScale: Float = 1.08f,
    content: @Composable () -> Unit,
) {
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = CardDefaults.shape(CardShape),
        colors = CardDefaults.colors(
            containerColor = TvColors.Surface,
            contentColor = TvColors.OnSurface,
            focusedContainerColor = TvColors.SurfaceFocused,
            focusedContentColor = Color.White,
        ),
        scale = CardDefaults.scale(focusedScale = focusedScale),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, TvColors.FocusBorder), shape = CardShape),
        ),
        glow = CardDefaults.glow(),
    ) {
        content()
    }
}

/** Logo del canal sobre fondo neutro (o sus iniciales si no tiene logo). */
@Composable
fun ChannelLogo(channel: Channel, modifier: Modifier = Modifier, padding: Dp = 12.dp, initialsSize: Int = 28) {
    Box(modifier.background(TvColors.LogoBackground), contentAlignment = Alignment.Center) {
        if (channel.logo != null) {
            AsyncImage(
                model = channel.logo,
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        } else {
            Text(
                channel.name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase(),
                fontSize = initialsSize.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1F27),
            )
        }
    }
}

@Composable
fun ChannelCard(
    channel: Channel,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (() -> Unit)? = null,
) {
    TvCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = if (onFocused != null) modifier.onFocusChanged { if (it.isFocused) onFocused() } else modifier,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            ) {
                ChannelLogo(channel, Modifier.fillMaxSize())
                if (isFavorite) {
                    Text(
                        "★",
                        color = TvColors.Accent,
                        fontSize = 18.sp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                            .background(Color(0x99000000), CircleShape)
                            .padding(horizontal = 5.dp),
                    )
                }
            }
            Text(
                channel.name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

/** Tarjeta de un evento sugerido por IA (partido, noticia, especial). */
@Composable
fun EventCard(
    event: SuggestedEvent,
    channels: List<Channel>,
    now: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (() -> Unit)? = null,
) {
    val live = event.isLive(now)
    TvCard(
        onClick = onClick,
        modifier = if (onFocused != null) modifier.onFocusChanged { if (it.isFocused) onFocused() } else modifier,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(124.dp)
                .background(Brush.linearGradient(listOf(eventColor(event.type), TvColors.Surface)))
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(event.emoji, fontSize = 18.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    event.competition ?: eventTypeName(event.type),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xDDFFFFFF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (live) LiveBadge()
            }
            Text(
                event.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (live) "Ahora" else formatEventTime(event.startMillis, now),
                    style = MaterialTheme.typography.labelMedium,
                    color = TvColors.Accent,
                    modifier = Modifier.weight(1f),
                )
                channels.take(3).forEach { c ->
                    ChannelLogo(
                        c,
                        Modifier
                            .padding(start = 4.dp)
                            .size(width = 36.dp, height = 22.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        padding = 2.dp,
                        initialsSize = 9,
                    )
                }
            }
        }
    }
}

@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Text(
        "● EN VIVO",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier
            .background(TvColors.Live, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier, color: Color = Color(0x33FFFFFF)) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        maxLines = 1,
        modifier = modifier
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

fun eventColor(type: String): Color = when (type) {
    "futbol" -> Color(0xFF1B5E20)
    "deporte" -> Color(0xFF0D47A1)
    "noticias" -> Color(0xFF8E1B1B)
    "cine" -> Color(0xFF4A148C)
    "musica" -> Color(0xFF880E4F)
    else -> Color(0xFF5D4037)
}

fun eventTypeName(type: String): String = when (type) {
    "futbol" -> "Fútbol"
    "deporte" -> "Deportes"
    "noticias" -> "Noticias"
    "cine" -> "Cine"
    "musica" -> "Música"
    else -> "Especial"
}

/** "Hoy 20:30", "Mañana 15:00" o "vie 3 oct 18:00" en la hora local de la TV. */
fun formatEventTime(millis: Long, now: Long): String {
    val event = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(event.time)
    val sameDay = { a: Calendar, b: Calendar -> a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR) }
    val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
    return when {
        sameDay(event, today) -> "Hoy $time"
        sameDay(event, tomorrow) -> "Mañana $time"
        else -> SimpleDateFormat("EEE d MMM HH:mm", Locale.forLanguageTag("es")).format(event.time)
    }
}

/** Tarjeta de país o temática: un emoji grande, título y cantidad de canales. */
@Composable
fun TileCard(
    emoji: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvCard(onClick = onClick, modifier = modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(emoji, fontSize = 36.sp)
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TvColors.OnSurfaceDim)
        }
    }
}

/**
 * Pide foco para un elemento que puede no estar compuesto todavía (listas perezosas):
 * reintenta unos cuantos frames antes de rendirse.
 */
@Composable
fun RequestFocusWhenReady(requester: FocusRequester, key: Any?, enabled: Boolean = true) {
    LaunchedEffect(key, enabled) {
        if (!enabled) return@LaunchedEffect
        repeat(20) {
            val result = runCatching { requester.requestFocus() }
            if (result.isSuccess && result.getOrNull() != false) return@LaunchedEffect
            delay(50)
        }
    }
}

/** Grilla genérica con restauración de foco al elemento `focusIndex`. */
@Composable
fun <T> FocusGrid(
    items: List<T>,
    minCellWidth: Int,
    focusIndex: Int,
    requestFocus: Boolean,
    modifier: Modifier = Modifier,
    itemContent: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    val start = focusIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val state = rememberLazyGridState(initialFirstVisibleItemIndex = start)
    val requester = remember { FocusRequester() }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minCellWidth.dp),
        state = state,
        modifier = modifier,
        contentPadding = PaddingValues(start = 32.dp, end = 40.dp, top = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        itemsIndexed(items) { i, item ->
            itemContent(item, if (i == start) Modifier.focusRequester(requester) else Modifier)
        }
    }
    RequestFocusWhenReady(requester, items, enabled = requestFocus && items.isNotEmpty())
}

@Composable
fun ChannelGrid(
    channels: List<Channel>,
    favorites: Set<String>,
    focusChannelId: String?,
    requestFocus: Boolean,
    onPlay: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusIndex = remember(channels, focusChannelId) {
        channels.indexOfFirst { it.id == focusChannelId }.coerceAtLeast(0)
    }
    FocusGrid(channels, 160, focusIndex, requestFocus, modifier) { channel, itemModifier ->
        ChannelCard(
            channel = channel,
            isFavorite = channel.id in favorites,
            onClick = { onPlay(channel) },
            onLongClick = { onToggleFavorite(channel) },
            modifier = itemModifier,
        )
    }
}

@Composable
fun CenteredMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = TvColors.OnSurfaceDim,
            textAlign = TextAlign.Center,
        )
    }
}

/** Encabezado de pantalla: título grande y una línea de ayuda. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 32.dp, end = 40.dp, top = 28.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TvColors.OnSurfaceDim)
        }
    }
}

fun channelCount(count: Int): String = if (count == 1) "1 canal" else "$count canales"

fun categoryEmoji(id: String): String = when (id) {
    "sports" -> "⚽"
    "news" -> "📰"
    "movies" -> "🎬"
    "series" -> "📺"
    "entertainment" -> "🎭"
    "music" -> "🎵"
    "kids" -> "🧸"
    "animation" -> "🎨"
    "documentary" -> "🎥"
    "general" -> "📡"
    "comedy" -> "😂"
    "classic" -> "🎞️"
    "culture" -> "🏛️"
    "cooking" -> "🍳"
    "education" -> "🎓"
    "business" -> "💼"
    "auto" -> "🚗"
    "family" -> "👪"
    "lifestyle" -> "✨"
    "outdoor" -> "🏕️"
    "public" -> "🏢"
    "relax" -> "🧘"
    "religious" -> "🙏"
    "science" -> "🔬"
    "shop" -> "🛍️"
    "travel" -> "✈️"
    "weather" -> "⛅"
    "legislative" -> "⚖️"
    "interactive" -> "🕹️"
    else -> "📡"
}

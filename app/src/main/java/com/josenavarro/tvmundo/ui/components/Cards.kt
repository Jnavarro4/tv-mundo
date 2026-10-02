package com.josenavarro.tvmundo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.josenavarro.tvmundo.data.Channel
import com.josenavarro.tvmundo.ui.theme.TvColors
import kotlinx.coroutines.delay

private val CardShape = RoundedCornerShape(12.dp)

@Composable
private fun TvCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
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
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, TvColors.FocusBorder), shape = CardShape),
        ),
    ) {
        content()
    }
}

@Composable
fun ChannelCard(
    channel: Channel,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvCard(onClick = onClick, onLongClick = onLongClick, modifier = modifier) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(TvColors.LogoBackground),
                contentAlignment = Alignment.Center,
            ) {
                if (channel.logo != null) {
                    AsyncImage(
                        model = channel.logo,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                    )
                } else {
                    Text(
                        channel.name.take(2).uppercase(),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = TvColors.OnSurfaceDim,
                    )
                }
                if (isFavorite) {
                    Text(
                        "★",
                        color = TvColors.Accent,
                        fontSize = 20.sp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                    )
                }
            }
            Text(
                channel.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
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
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
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

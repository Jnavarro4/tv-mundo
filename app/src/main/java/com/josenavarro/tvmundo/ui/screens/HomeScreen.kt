package com.josenavarro.tvmundo.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.josenavarro.tvmundo.data.CatalogIndex
import com.josenavarro.tvmundo.data.Channel
import com.josenavarro.tvmundo.player.LivePreview
import com.josenavarro.tvmundo.ui.HomeItem
import com.josenavarro.tvmundo.ui.HomeRow
import com.josenavarro.tvmundo.ui.HomeRows
import com.josenavarro.tvmundo.ui.MainViewModel
import com.josenavarro.tvmundo.ui.Screen
import com.josenavarro.tvmundo.ui.components.CenteredMessage
import com.josenavarro.tvmundo.ui.components.ChannelCard
import com.josenavarro.tvmundo.ui.components.Chip
import com.josenavarro.tvmundo.ui.components.EventCard
import com.josenavarro.tvmundo.ui.components.LiveBadge
import com.josenavarro.tvmundo.ui.components.RequestFocusWhenReady
import com.josenavarro.tvmundo.ui.components.eventColor
import com.josenavarro.tvmundo.ui.components.eventTypeName
import com.josenavarro.tvmundo.ui.components.formatEventTime
import com.josenavarro.tvmundo.ui.theme.TvColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Inicio ("lobby") con el patrón de lista inmersiva de Google TV: arriba un panel con
 * la información y la vista previa en vivo del elemento enfocado; abajo, filas.
 */
@Composable
fun HomeScreen(vm: MainViewModel, index: CatalogIndex) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val suggestions = vm.suggestions
    // Las filas se recalculan cada 10 minutos como mucho (eventos que empiezan/terminan).
    val clockBucket = vm.now / 600_000
    val rows = remember(index, favorites, history, suggestions, clockBucket) {
        HomeRows.build(index, favorites, history, suggestions, vm.now)
    }

    if (rows.isEmpty()) {
        CenteredMessage("No hay canales para mostrar.")
        return
    }

    // A dónde va el foco al entrar: al elemento de donde se salió o al primero.
    val takeFocus = remember { vm.consumeRestoreFocus() || !vm.homeVisited }
    vm.homeVisited = true
    val target = remember(rows) { locate(rows, vm.homeFocusKey) }
    val focused = remember { mutableStateOf(rows[target.first].items.getOrNull(target.second)) }

    Column(Modifier.fillMaxSize()) {
        HomeHeader(
            focused = focused,
            index = index,
            favorites = favorites,
            now = vm.now,
            livePreview = settings.livePreview,
            refreshing = vm.refreshing,
        )

        val listState = rememberLazyListState(initialFirstVisibleItemIndex = target.first)
        val requester = remember { FocusRequester() }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 40.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            itemsIndexed(rows, key = { _, row -> row.key }) { rowIndex, row ->
                val isTarget = rowIndex == target.first
                HomeRowView(
                    row = row,
                    initialItem = if (isTarget) target.second else 0,
                    requester = if (isTarget) requester else null,
                    favorites = favorites,
                    now = vm.now,
                    onFocus = { item ->
                        focused.value = item
                        vm.homeFocusKey = "${row.key}|${item.key}"
                    },
                    onOpen = { item ->
                        when (item) {
                            is HomeItem.ChannelItem -> vm.play(row.title.trimStart('★', ' '), row.playlist, item.channel)
                            is HomeItem.EventItem -> vm.navigate(Screen.EventDetail(item.event.id))
                        }
                    },
                    onLongPress = { item ->
                        if (item is HomeItem.ChannelItem) vm.toggleFavorite(item.channel)
                    },
                )
            }
        }
        RequestFocusWhenReady(requester, Unit, enabled = takeFocus)
    }
}

private fun locate(rows: List<HomeRow>, key: String?): Pair<Int, Int> {
    if (key != null) {
        val rowKey = key.substringBefore('|')
        val itemKey = key.substringAfter('|')
        val r = rows.indexOfFirst { it.key == rowKey }
        if (r >= 0) {
            val i = rows[r].items.indexOfFirst { it.key == itemKey }
            return r to i.coerceAtLeast(0)
        }
    }
    return 0 to 0
}

@Composable
private fun HomeRowView(
    row: HomeRow,
    initialItem: Int,
    requester: FocusRequester?,
    favorites: Set<String>,
    now: Long,
    onFocus: (HomeItem) -> Unit,
    onOpen: (HomeItem) -> Unit,
    onLongPress: (HomeItem) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 32.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(row.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (row.badge != null) {
                Chip(row.badge, Modifier.padding(start = 10.dp), color = Color(0x55FFB300))
            }
        }
        val state = rememberLazyListState(initialFirstVisibleItemIndex = initialItem)
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(row.items, key = { _, item -> item.key }) { i, item ->
                val modifier = if (requester != null && i == initialItem) Modifier.focusRequester(requester) else Modifier
                when (item) {
                    is HomeItem.ChannelItem -> ChannelCard(
                        channel = item.channel,
                        isFavorite = item.channel.id in favorites,
                        onClick = { onOpen(item) },
                        onLongClick = { onLongPress(item) },
                        onFocused = { onFocus(item) },
                        modifier = modifier.width(168.dp),
                    )

                    is HomeItem.EventItem -> EventCard(
                        event = item.event,
                        channels = item.channels,
                        now = now,
                        onClick = { onOpen(item) },
                        onFocused = { onFocus(item) },
                        modifier = modifier.width(256.dp),
                    )
                }
            }
        }
    }
}

/** Panel superior: información del elemento enfocado + vista previa en vivo. */
@Composable
private fun HomeHeader(
    focused: MutableState<HomeItem?>,
    index: CatalogIndex,
    favorites: Set<String>,
    now: Long,
    livePreview: Boolean,
    refreshing: Boolean,
) {
    val item = focused.value
    val previewChannel: Channel? = when (item) {
        is HomeItem.ChannelItem -> item.channel
        is HomeItem.EventItem -> item.channels.firstOrNull()
        null -> null
    }
    val tint = when (item) {
        is HomeItem.EventItem -> eventColor(item.event.type).copy(alpha = 0.55f)
        else -> Color(0xFF1C2A3E)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(262.dp)
            .background(Brush.horizontalGradient(listOf(tint, TvColors.Background))),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = 32.dp, end = 40.dp, top = 20.dp, bottom = 8.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(end = 28.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TV Mundo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TvColors.Accent)
                    if (refreshing) {
                        Text("  · actualizando lista…", style = MaterialTheme.typography.labelMedium, color = TvColors.OnSurfaceDim)
                    }
                }
                Spacer(Modifier.height(14.dp))
                when (item) {
                    is HomeItem.ChannelItem -> ChannelInfo(item, index, item.channel.id in favorites)
                    is HomeItem.EventItem -> EventInfo(item, now)
                    null -> Unit
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)),
                    style = MaterialTheme.typography.titleMedium,
                    color = TvColors.OnSurfaceDim,
                )
                Spacer(Modifier.height(8.dp))
                LivePreview(
                    channel = previewChannel,
                    enabled = livePreview,
                    modifier = Modifier
                        .width(368.dp)
                        .aspectRatio(16f / 9f),
                )
            }
        }
    }
}

@Composable
private fun ChannelInfo(item: HomeItem.ChannelItem, index: CatalogIndex, isFavorite: Boolean) {
    val channel = item.channel
    val country = index.countryNames[channel.country]
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        LiveBadge()
        if (country != null) Chip("${country.flag} ${country.name}")
        channel.categories.take(2).mapNotNull { index.categoryNames[it]?.name }.forEach { Chip(it) }
        if (isFavorite) Chip("★ Favorito", color = Color(0x55FFB300))
    }
    Text(
        channel.name,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 10.dp),
    )
    Text(
        item.note?.let { "✨ $it" } ?: describe(channel, index),
        style = MaterialTheme.typography.bodyLarge,
        color = TvColors.OnSurfaceDim,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 6.dp),
    )
    Spacer(Modifier.height(10.dp))
    Text(
        "OK: ver  ·  Mantén OK: favorito",
        style = MaterialTheme.typography.labelMedium,
        color = TvColors.OnSurfaceDim,
    )
}

@Composable
private fun EventInfo(item: HomeItem.EventItem, now: Long) {
    val event = item.event
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (event.isLive(now)) LiveBadge() else Chip("🕒 ${formatEventTime(event.startMillis, now)}")
        Chip("${event.emoji} ${event.competition ?: eventTypeName(event.type)}")
        Chip("✨ Sugerido por IA", color = Color(0x55FFB300))
    }
    Text(
        event.title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 10.dp),
    )
    Text(
        event.description,
        style = MaterialTheme.typography.bodyLarge,
        color = TvColors.OnSurfaceDim,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 6.dp),
    )
    Spacer(Modifier.height(10.dp))
    Text(
        if (item.channels.isEmpty()) "Sin señal gratuita confirmada · OK para ver opciones"
        else "Dónde verlo: " + item.channels.take(3).joinToString(", ") { it.name } + "  ·  OK para elegir",
        style = MaterialTheme.typography.labelMedium,
        color = TvColors.OnSurfaceDim,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Descripción automática de un canal a partir de sus datos. */
fun describe(channel: Channel, index: CatalogIndex): String {
    val categories = channel.categories.mapNotNull { index.categoryNames[it]?.name?.lowercase() }
    val country = index.countryNames[channel.country]?.name ?: channel.country
    val kind = if (categories.isEmpty()) "Canal" else "Canal de ${categories.joinToString(" y ")}"
    val signals = if (channel.streams.size == 1) "1 señal disponible" else "${channel.streams.size} señales disponibles"
    val quality = channel.streams.first().quality?.let { " · hasta $it" }.orEmpty()
    val language = if (index.isSpanish(channel)) " en español" else ""
    return "$kind$language de $country, en vivo. $signals$quality."
}

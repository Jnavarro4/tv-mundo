package com.josenavarro.tvmundo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.josenavarro.tvmundo.data.CatalogIndex
import com.josenavarro.tvmundo.ui.MainViewModel
import com.josenavarro.tvmundo.ui.components.CenteredMessage
import com.josenavarro.tvmundo.ui.components.ChannelGrid
import com.josenavarro.tvmundo.ui.components.Chip
import com.josenavarro.tvmundo.ui.components.LiveBadge
import com.josenavarro.tvmundo.ui.components.eventColor
import com.josenavarro.tvmundo.ui.components.eventTypeName
import com.josenavarro.tvmundo.ui.components.formatEventTime
import com.josenavarro.tvmundo.ui.theme.TvColors

/** Detalle de un evento sugerido por IA y los canales donde podría verse. */
@Composable
fun EventDetailScreen(vm: MainViewModel, index: CatalogIndex, eventId: String) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    remember { vm.consumeRestoreFocus() }
    val event = vm.event(eventId)
    if (event == null) {
        CenteredMessage("Este evento ya no está disponible.")
        return
    }
    val channels = remember(event, index) { event.channels.mapNotNull { index.resolve(it) } }
    // Sin canal confirmado: se ofrecen los mejores canales del tipo de evento.
    val fallback = remember(event, index) {
        val category = when (event.type) {
            "futbol", "deporte" -> "sports"
            "noticias" -> "news"
            "cine" -> "movies"
            "musica" -> "music"
            else -> "general"
        }
        index.topInCategory(category, 16)
    }
    val list = channels.ifEmpty { fallback }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(eventColor(event.type).copy(alpha = 0.6f), TvColors.Background)))
                .padding(start = 32.dp, end = 40.dp, top = 28.dp, bottom = 16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (event.isLive(vm.now)) LiveBadge() else Chip("🕒 ${formatEventTime(event.startMillis, vm.now)}")
                Chip("${event.emoji} ${event.competition ?: eventTypeName(event.type)}")
                Chip("✨ Sugerido por IA", color = Color(0x55FFB300))
            }
            Text(
                event.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp),
            )
            if (event.description.isNotBlank()) {
                Text(
                    event.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TvColors.OnSurfaceDim,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Text(
                if (channels.isNotEmpty()) "Dónde verlo o seguirlo:"
                else "No encontramos una señal gratuita confirmada para este evento. Estos canales podrían cubrirlo:",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                "Los derechos de transmisión cambian según el país: la señal puede no estar disponible.",
                style = MaterialTheme.typography.labelMedium,
                color = TvColors.OnSurfaceDim,
            )
        }
        ChannelGrid(
            channels = list,
            favorites = favorites,
            focusChannelId = vm.lastChannelId,
            requestFocus = true,
            onPlay = { vm.play(event.title, list, it) },
            onToggleFavorite = vm::toggleFavorite,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

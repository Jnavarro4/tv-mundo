package com.josenavarro.tvmundo.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.josenavarro.tvmundo.data.Channel
import com.josenavarro.tvmundo.ui.MainViewModel
import com.josenavarro.tvmundo.ui.components.CenteredMessage
import com.josenavarro.tvmundo.ui.components.ChannelGrid
import com.josenavarro.tvmundo.ui.components.channelCount
import com.josenavarro.tvmundo.ui.theme.TvColors

/** Grilla de canales de un país o de una temática. */
@Composable
fun ChannelListScreen(
    vm: MainViewModel,
    title: String,
    channels: List<Channel>,
) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    // Se consume para que la pantalla de inicio no robe el foco al volver.
    remember { vm.consumeRestoreFocus() }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 48.dp, end = 48.dp, top = 24.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "${channelCount(channels.size)} · Mantén pulsado OK para agregar a Favoritos",
                style = MaterialTheme.typography.bodyMedium,
                color = TvColors.OnSurfaceDim,
            )
        }
        if (channels.isEmpty()) {
            CenteredMessage("No hay canales disponibles.")
        } else {
            ChannelGrid(
                channels = channels,
                favorites = favorites,
                focusChannelId = vm.lastChannelId,
                requestFocus = true,
                onPlay = { vm.play(title, channels, it) },
                onToggleFavorite = vm::toggleFavorite,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

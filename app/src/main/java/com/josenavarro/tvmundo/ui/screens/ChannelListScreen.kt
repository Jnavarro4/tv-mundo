package com.josenavarro.tvmundo.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.josenavarro.tvmundo.data.Channel
import com.josenavarro.tvmundo.ui.MainViewModel
import com.josenavarro.tvmundo.ui.components.CenteredMessage
import com.josenavarro.tvmundo.ui.components.ChannelGrid
import com.josenavarro.tvmundo.ui.components.ScreenHeader
import com.josenavarro.tvmundo.ui.components.channelCount

/** Grilla de canales de un país o de una temática. */
@Composable
fun ChannelListScreen(
    vm: MainViewModel,
    title: String,
    channels: List<Channel>,
) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    // Se consume para que la pantalla anterior no robe el foco al volver.
    remember { vm.consumeRestoreFocus() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, "${channelCount(channels.size)} · Mantén pulsado OK para agregar a Favoritos")
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
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

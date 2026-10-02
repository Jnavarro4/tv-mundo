package com.josenavarro.tvmundo.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.josenavarro.tvmundo.data.CatalogIndex
import com.josenavarro.tvmundo.ui.MainViewModel
import com.josenavarro.tvmundo.ui.Screen
import com.josenavarro.tvmundo.ui.components.CenteredMessage
import com.josenavarro.tvmundo.ui.components.ChannelGrid
import com.josenavarro.tvmundo.ui.components.FocusGrid
import com.josenavarro.tvmundo.ui.components.ScreenHeader
import com.josenavarro.tvmundo.ui.components.TileCard
import com.josenavarro.tvmundo.ui.components.categoryEmoji
import com.josenavarro.tvmundo.ui.components.channelCount

/** Grilla de países con bandera y cantidad de canales (país principal primero). */
@Composable
fun CountriesScreen(vm: MainViewModel, index: CatalogIndex) {
    val restore = remember { vm.consumeRestoreFocus() }
    val countries = index.countries
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Países", "${countries.size} países · ${channelCount(index.channels.size)}")
        val focusIndex = countries.indexOfFirst { it.first.code == vm.lastCountryCode }
        FocusGrid(countries, 150, focusIndex, restore, Modifier.fillMaxWidth().weight(1f)) { (country, count), itemModifier ->
            TileCard(
                emoji = country.flag,
                title = country.name,
                subtitle = channelCount(count),
                onClick = {
                    vm.lastCountryCode = country.code
                    vm.navigate(Screen.CountryChannels(country.code))
                },
                modifier = itemModifier,
            )
        }
    }
}

/** Grilla de temáticas traducidas al español. */
@Composable
fun CategoriesScreen(vm: MainViewModel, index: CatalogIndex) {
    val restore = remember { vm.consumeRestoreFocus() }
    val categories = index.categories
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Temáticas", "Deportes, noticias, películas, música y más")
        val focusIndex = categories.indexOfFirst { it.first.id == vm.lastCategoryId }
        FocusGrid(categories, 150, focusIndex, restore, Modifier.fillMaxWidth().weight(1f)) { (category, count), itemModifier ->
            TileCard(
                emoji = categoryEmoji(category.id),
                title = category.name,
                subtitle = channelCount(count),
                onClick = {
                    vm.lastCategoryId = category.id
                    vm.navigate(Screen.CategoryChannels(category.id))
                },
                modifier = itemModifier,
            )
        }
    }
}

@Composable
fun FavoritesScreen(vm: MainViewModel, index: CatalogIndex) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val restore = remember { vm.consumeRestoreFocus() }
    val list = remember(index, favorites) { index.channels.filter { it.id in favorites } }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Favoritos", if (list.isEmpty()) null else "${channelCount(list.size)} · Mantén OK para quitar")
        if (list.isEmpty()) {
            CenteredMessage("Aún no hay favoritos.\nMantén pulsado OK sobre cualquier canal para agregarlo.")
        } else {
            ChannelGrid(
                channels = list,
                favorites = favorites,
                focusChannelId = vm.lastChannelId,
                requestFocus = restore,
                onPlay = { vm.play("Favoritos", list, it) },
                onToggleFavorite = vm::toggleFavorite,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

package com.josenavarro.tvmundo.ui.screens

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import com.josenavarro.tvmundo.ui.CatalogIndex
import com.josenavarro.tvmundo.ui.HomeTab
import com.josenavarro.tvmundo.ui.MainViewModel
import com.josenavarro.tvmundo.ui.Screen
import com.josenavarro.tvmundo.ui.components.CenteredMessage
import com.josenavarro.tvmundo.ui.components.ChannelGrid
import com.josenavarro.tvmundo.ui.components.FocusGrid
import com.josenavarro.tvmundo.ui.components.RequestFocusWhenReady
import com.josenavarro.tvmundo.ui.components.TileCard
import com.josenavarro.tvmundo.ui.components.categoryEmoji
import com.josenavarro.tvmundo.ui.components.channelCount
import com.josenavarro.tvmundo.ui.theme.TvColors

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(vm: MainViewModel, index: CatalogIndex) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()

    // Al volver de otra pantalla, el foco va al elemento de donde se salió (solo una vez).
    var restoreTab by remember { mutableStateOf(if (vm.consumeRestoreFocus()) vm.selectedTab else null) }
    LaunchedEffect(vm.selectedTab) {
        if (vm.selectedTab != restoreTab) restoreTab = null
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "TV Mundo",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TvColors.Accent,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (vm.refreshing) "Actualizando lista…" else channelCount(index.channels.size),
                style = MaterialTheme.typography.bodyMedium,
                color = TvColors.OnSurfaceDim,
            )
        }

        val tabRequester = remember { FocusRequester() }
        TabRow(
            selectedTabIndex = vm.selectedTab.ordinal,
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .focusRestorer(tabRequester)
                .focusGroup(),
        ) {
            HomeTab.entries.forEach { tab ->
                Tab(
                    selected = vm.selectedTab == tab,
                    onFocus = { vm.selectedTab = tab },
                    modifier = if (vm.selectedTab == tab) Modifier.focusRequester(tabRequester) else Modifier,
                ) {
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
        RequestFocusWhenReady(tabRequester, Unit, enabled = restoreTab == null)

        val restore = restoreTab == vm.selectedTab
        val contentModifier = Modifier
            .fillMaxWidth()
            .weight(1f)

        when (vm.selectedTab) {
            HomeTab.Featured -> {
                val list = index.featured
                if (list.isEmpty()) {
                    CenteredMessage("Ninguno de los canales destacados está disponible ahora.", contentModifier)
                } else {
                    ChannelGrid(
                        channels = list,
                        favorites = favorites,
                        focusChannelId = vm.lastChannelId,
                        requestFocus = restore,
                        onPlay = { vm.play("Destacados", list, it) },
                        onToggleFavorite = vm::toggleFavorite,
                        modifier = contentModifier,
                    )
                }
            }

            HomeTab.Countries -> {
                val countries = index.countries
                val focusIndex = countries.indexOfFirst { it.first.code == vm.lastCountryCode }
                FocusGrid(countries, 150, focusIndex, restore, contentModifier) { (country, count), itemModifier ->
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

            HomeTab.Categories -> {
                val categories = index.categories
                val focusIndex = categories.indexOfFirst { it.first.id == vm.lastCategoryId }
                FocusGrid(categories, 150, focusIndex, restore, contentModifier) { (category, count), itemModifier ->
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

            HomeTab.Favorites -> {
                val list = remember(index, favorites) { index.channels.filter { it.id in favorites } }
                if (list.isEmpty()) {
                    CenteredMessage(
                        "Aún no hay favoritos.\nMantén pulsado OK sobre un canal para agregarlo.",
                        contentModifier,
                    )
                } else {
                    ChannelGrid(
                        channels = list,
                        favorites = favorites,
                        focusChannelId = vm.lastChannelId,
                        requestFocus = restore,
                        onPlay = { vm.play("Favoritos", list, it) },
                        onToggleFavorite = vm::toggleFavorite,
                        modifier = contentModifier,
                    )
                }
            }

            HomeTab.Search -> SearchTab(vm, index, favorites, restore, contentModifier)
        }
    }
}

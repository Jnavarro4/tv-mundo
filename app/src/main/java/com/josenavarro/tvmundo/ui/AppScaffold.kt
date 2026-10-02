package com.josenavarro.tvmundo.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.josenavarro.tvmundo.data.CatalogIndex
import com.josenavarro.tvmundo.player.PlayerScreen
import com.josenavarro.tvmundo.ui.screens.CategoriesScreen
import com.josenavarro.tvmundo.ui.screens.ChannelListScreen
import com.josenavarro.tvmundo.ui.screens.CountriesScreen
import com.josenavarro.tvmundo.ui.screens.EventDetailScreen
import com.josenavarro.tvmundo.ui.screens.FavoritesScreen
import com.josenavarro.tvmundo.ui.screens.HomeScreen
import com.josenavarro.tvmundo.ui.screens.SearchScreen
import com.josenavarro.tvmundo.ui.screens.SettingsScreen
import com.josenavarro.tvmundo.ui.theme.TvColors

private val RailCollapsed = 72.dp
private val RailExpanded = 212.dp

private val Section.icon: ImageVector
    get() = when (this) {
        Section.Home -> Icons.Filled.Home
        Section.Countries -> Icons.Filled.Place
        Section.Categories -> Icons.AutoMirrored.Filled.List
        Section.Favorites -> Icons.Filled.Favorite
        Section.Search -> Icons.Filled.Search
        Section.Settings -> Icons.Filled.Settings
    }

/** Estructura principal: barra lateral + contenido, o el reproductor a pantalla completa. */
@Composable
fun AppScaffold(vm: MainViewModel, index: CatalogIndex) {
    val top = vm.backStack.lastOrNull()
    val activity = LocalContext.current as? Activity
    val railFocus = remember { FocusRequester() }
    var railHasFocus by remember { mutableStateOf(false) }

    // Atrás: cierra la pantalla actual; en el Inicio primero lleva a la barra y luego sale.
    BackHandler {
        if (!vm.back()) {
            if (railHasFocus) activity?.finish()
            else runCatching { railFocus.requestFocus() }
        }
    }

    if (top is Screen.Player) {
        val favorites by vm.favorites.collectAsStateWithLifecycle()
        PlayerScreen(
            title = top.title,
            channels = top.channels,
            startIndex = top.startIndex,
            favorites = favorites,
            countryOf = { index.countryNames[it] },
            onToggleFavorite = vm::toggleFavorite,
            onChannelChanged = { vm.lastChannelId = it.id },
            onWatched = vm::recordWatch,
            onExit = { vm.back() },
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = RailCollapsed),
        ) {
            when (top) {
                is Screen.CountryChannels -> {
                    val country = index.countryNames[top.code]
                    ChannelListScreen(
                        vm = vm,
                        title = country?.let { "${it.flag}  ${it.name}" } ?: top.code,
                        channels = remember(index, top.code) { index.byCountry[top.code].orEmpty().sortedWith(index.ranking) },
                    )
                }

                is Screen.CategoryChannels -> ChannelListScreen(
                    vm = vm,
                    title = index.categoryNames[top.id]?.name ?: top.id,
                    channels = remember(index, top.id) { index.byCategory[top.id].orEmpty().sortedWith(index.ranking) },
                )

                is Screen.EventDetail -> EventDetailScreen(vm, index, top.eventId)

                else -> when (vm.section) {
                    Section.Home -> HomeScreen(vm, index)
                    Section.Countries -> CountriesScreen(vm, index)
                    Section.Categories -> CategoriesScreen(vm, index)
                    Section.Favorites -> FavoritesScreen(vm, index)
                    Section.Search -> SearchScreen(vm, index)
                    Section.Settings -> SettingsScreen(vm, index)
                }
            }
        }
        NavRail(
            current = vm.section,
            selectedRequester = railFocus,
            onSelect = vm::select,
            onFocusChanged = { railHasFocus = it },
        )
    }
}

/** Barra lateral con íconos que se expande (mostrando los nombres) al recibir el foco. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NavRail(
    current: Section,
    selectedRequester: FocusRequester,
    onSelect: (Section) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (expanded) RailExpanded else RailCollapsed, label = "rail")
    Column(
        Modifier
            .fillMaxHeight()
            .width(width)
            .background(
                if (expanded) Brush.horizontalGradient(listOf(TvColors.Rail, TvColors.Rail.copy(alpha = 0.92f)))
                else Brush.horizontalGradient(listOf(TvColors.Rail, TvColors.Rail))
            )
            .onFocusChanged {
                expanded = it.hasFocus
                onFocusChanged(it.hasFocus)
            }
            .focusRestorer(selectedRequester)
            .focusGroup()
            .padding(horizontal = 12.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 18.dp)) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(TvColors.Accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("TV", fontWeight = FontWeight.Black, color = Color.Black, style = MaterialTheme.typography.titleSmall)
            }
            if (expanded) {
                Text(
                    "TV Mundo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 12.dp),
                    maxLines = 1,
                )
            }
        }
        Section.entries.forEach { section ->
            RailItem(
                section = section,
                selected = section == current,
                expanded = expanded,
                modifier = if (section == current) Modifier.focusRequester(selectedRequester) else Modifier,
                onClick = { onSelect(section) },
            )
            if (section == Section.Search) Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun RailItem(
    section: Section,
    selected: Boolean,
    expanded: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color(0x26FFFFFF) else Color.Transparent,
            contentColor = if (selected) TvColors.Accent else TvColors.OnSurfaceDim,
            focusedContainerColor = TvColors.OnSurface,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(section.icon, contentDescription = section.label, modifier = Modifier.size(24.dp))
            if (expanded) {
                Text(
                    section.label,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 14.dp),
                    maxLines = 1,
                )
            }
        }
    }
}

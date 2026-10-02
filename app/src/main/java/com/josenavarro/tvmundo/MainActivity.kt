package com.josenavarro.tvmundo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.josenavarro.tvmundo.player.PlayerScreen
import com.josenavarro.tvmundo.ui.CatalogIndex
import com.josenavarro.tvmundo.ui.LoadState
import com.josenavarro.tvmundo.ui.MainViewModel
import com.josenavarro.tvmundo.ui.Screen
import com.josenavarro.tvmundo.ui.components.CenteredMessage
import com.josenavarro.tvmundo.ui.components.RequestFocusWhenReady
import com.josenavarro.tvmundo.ui.screens.ChannelListScreen
import com.josenavarro.tvmundo.ui.screens.HomeScreen
import com.josenavarro.tvmundo.ui.theme.TvColors
import com.josenavarro.tvmundo.ui.theme.TvMundoTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TvMundoTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(TvColors.Background),
                ) {
                    when (val state = vm.loadState) {
                        is LoadState.Loading -> CenteredMessage(state.message)
                        is LoadState.Error -> ErrorScreen(state.message, onRetry = vm::refresh)
                        is LoadState.Ready -> Navigation(vm, state.index)
                    }
                    MessageToast(vm)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshIfStale()
    }
}

@Composable
private fun Navigation(vm: MainViewModel, index: CatalogIndex) {
    val screen = vm.backStack.last()
    BackHandler(enabled = vm.backStack.size > 1) { vm.back() }

    when (screen) {
        Screen.Home -> HomeScreen(vm, index)

        is Screen.CountryChannels -> {
            val country = index.countryNames[screen.code]
            ChannelListScreen(
                vm = vm,
                title = country?.let { "${it.flag}  ${it.name}" } ?: screen.code,
                channels = index.byCountry[screen.code].orEmpty(),
            )
        }

        is Screen.CategoryChannels -> {
            val category = index.categories.firstOrNull { it.first.id == screen.id }?.first
            ChannelListScreen(
                vm = vm,
                title = category?.name ?: screen.id,
                channels = index.byCategory[screen.id].orEmpty(),
            )
        }

        is Screen.Player -> {
            val favorites by vm.favorites.collectAsStateWithLifecycle()
            PlayerScreen(
                title = screen.title,
                channels = screen.channels,
                startIndex = screen.startIndex,
                favorites = favorites,
                countryOf = { index.countryNames[it] },
                onToggleFavorite = vm::toggleFavorite,
                onChannelChanged = { vm.lastChannelId = it.id },
            )
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    val requester = remember { FocusRequester() }
    Column(
        Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp),
        )
        Button(onClick = onRetry, modifier = Modifier.focusRequester(requester)) {
            Text("Reintentar")
        }
    }
    RequestFocusWhenReady(requester, message)
}

@Composable
private fun MessageToast(vm: MainViewModel) {
    val message = vm.message
    LaunchedEffect(message) {
        if (message != null) {
            delay(2_500)
            vm.message = null
        }
    }
    Box(Modifier.fillMaxSize().padding(top = 32.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(visible = message != null, enter = fadeIn(), exit = fadeOut()) {
            Text(
                message.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = TvColors.OnSurface,
                modifier = Modifier
                    .background(TvColors.SurfaceFocused, RoundedCornerShape(24.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}

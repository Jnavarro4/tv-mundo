package com.josenavarro.tvmundo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.josenavarro.tvmundo.ui.AppScaffold
import com.josenavarro.tvmundo.ui.LoadState
import com.josenavarro.tvmundo.ui.MainViewModel
import com.josenavarro.tvmundo.ui.components.RequestFocusWhenReady
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
                        is LoadState.Loading -> LoadingScreen(state.message)
                        is LoadState.Error -> ErrorScreen(state.message, onRetry = vm::refresh)
                        is LoadState.Ready -> AppScaffold(vm, state.index)
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
private fun LoadingScreen(message: String) {
    val transition = rememberInfiniteTransition(label = "splash")
    val pulse by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulse",
    )
    Column(
        Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(88.dp)
                .alpha(pulse)
                .background(TvColors.Accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("TV", fontWeight = FontWeight.Black, color = Color.Black, style = MaterialTheme.typography.headlineMedium)
        }
        Text(
            "TV Mundo",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            message,
            style = MaterialTheme.typography.titleMedium,
            color = TvColors.OnSurfaceDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
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
        verticalArrangement = Arrangement.Center,
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
    Box(Modifier.fillMaxSize().padding(top = 28.dp), contentAlignment = Alignment.TopCenter) {
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

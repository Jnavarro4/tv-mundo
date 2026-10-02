package com.josenavarro.tvmundo.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.josenavarro.tvmundo.data.CatalogIndex
import com.josenavarro.tvmundo.ui.MainViewModel
import com.josenavarro.tvmundo.ui.Screen
import com.josenavarro.tvmundo.ui.components.CenteredMessage
import com.josenavarro.tvmundo.ui.components.ChannelGrid
import com.josenavarro.tvmundo.ui.components.Chip
import com.josenavarro.tvmundo.ui.components.EventCard
import com.josenavarro.tvmundo.ui.components.channelCount
import com.josenavarro.tvmundo.ui.theme.TvColors
import kotlinx.coroutines.delay

private val letterRows = "ABCDEFGHIJKLMNÑOPQRSTUVWXYZ1234567890".chunked(7)

private val exampleQueries = listOf(
    "Fútbol argentino",
    "Noticias de Colombia",
    "Películas en español",
    "Dibujos para niños",
    "Música",
    "Documentales",
)

@Composable
fun SearchScreen(vm: MainViewModel, index: CatalogIndex) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val restore = remember { vm.consumeRestoreFocus() }
    val query = vm.searchQuery
    val result = remember(index, query, vm.suggestions) { vm.search(index, query) }
    // Al elegir una sugerencia el botón desaparece: el foco pasa al primer resultado.
    var focusResults by remember { mutableStateOf(false) }
    LaunchedEffect(focusResults) {
        if (focusResults) {
            delay(1_500)
            focusResults = false
        }
    }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) vm.searchQuery = text
        }
    }
    val startVoice = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-419")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Di qué quieres ver: \"noticias de Colombia\", \"fútbol\"…")
        try {
            voice.launch(intent)
        } catch (_: ActivityNotFoundException) {
            vm.message = "La búsqueda por voz no está disponible en este equipo"
        }
    }

    Row(Modifier.padding(start = 32.dp, top = 28.dp)) {
        Column(Modifier.width(380.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(TvColors.Surface, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    if (query.isEmpty()) "🔎 Busca canales, países o temas…" else query,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (query.isEmpty()) TvColors.OnSurfaceDim else TvColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OnScreenKeyboard(
                onKey = { vm.searchQuery = (query + it).take(40) },
                onSpace = { if (query.isNotEmpty() && !query.endsWith(" ")) vm.searchQuery = "$query " },
                onDelete = { vm.searchQuery = query.dropLast(1) },
                onClear = { vm.searchQuery = "" },
                onVoice = startVoice,
                requestFocus = restore,
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 8.dp),
        ) {
            when {
                query.isBlank() -> {
                    Text(
                        "Búsqueda inteligente: escribe o dicta frases como",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 24.dp, bottom = 12.dp),
                    )
                    Column(Modifier.padding(start = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        exampleQueries.forEach { example ->
                            SmallButton("“$example”") {
                                vm.searchQuery = example
                                focusResults = true
                            }
                        }
                    }
                    Text(
                        "Usa 🎤 o el botón de micrófono del control para buscar por voz.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TvColors.OnSurfaceDim,
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                    )
                }

                result.channels.isEmpty() && result.events.isEmpty() ->
                    CenteredMessage("No se encontraron canales para «$query».")

                else -> {
                    Row(Modifier.padding(start = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            channelCount(result.channels.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        result.interpretation?.let {
                            Chip("✨ Entendí: $it", Modifier.padding(start = 10.dp), color = Color(0x55FFB300))
                        }
                    }
                    if (result.events.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(result.events, key = { it.id }) { event ->
                                EventCard(
                                    event = event,
                                    channels = event.channels.mapNotNull { index.resolve(it) },
                                    now = vm.now,
                                    onClick = { vm.navigate(Screen.EventDetail(event.id)) },
                                    modifier = Modifier.width(240.dp),
                                )
                            }
                        }
                    }
                    ChannelGrid(
                        channels = result.channels,
                        favorites = favorites,
                        focusChannelId = vm.lastChannelId,
                        requestFocus = focusResults,
                        onPlay = { vm.play("Búsqueda: $query", result.channels, it) },
                        onToggleFavorite = vm::toggleFavorite,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun OnScreenKeyboard(
    onKey: (String) -> Unit,
    onSpace: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onVoice: () -> Unit,
    requestFocus: Boolean,
    modifier: Modifier = Modifier,
) {
    // Al entrar al teclado el foco va a la "A" (o a la última tecla usada).
    val firstKey = remember { FocusRequester() }
    Column(
        modifier
            .focusRestorer(firstKey)
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        letterRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { ch ->
                    val keyModifier = Modifier.size(width = 46.dp, height = 42.dp)
                    Key(ch.toString(), if (ch == 'A') keyModifier.focusRequester(firstKey) else keyModifier) { onKey(ch.toString()) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key("🎤 Voz", Modifier.size(width = 100.dp, height = 42.dp), onVoice)
            Key("Espacio", Modifier.size(width = 100.dp, height = 42.dp), onSpace)
            Key("⌫", Modifier.size(width = 62.dp, height = 42.dp), onDelete)
            Key("Borrar", Modifier.size(width = 62.dp, height = 42.dp), onClear)
        }
    }
    com.josenavarro.tvmundo.ui.components.RequestFocusWhenReady(firstKey, Unit, enabled = requestFocus)
}

@Composable
private fun Key(label: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvColors.Surface,
            contentColor = TvColors.OnSurface,
            focusedContainerColor = TvColors.Accent,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, TvColors.FocusBorder), shape = shape),
        ),
    ) {
        Box(Modifier.align(Alignment.Center)) {
            Text(label, fontSize = if (label.length > 1) 15.sp else 19.sp)
        }
    }
}

/** Botón de texto compacto (sugerencias, ajustes). */
@Composable
fun SmallButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvColors.Surface,
            contentColor = TvColors.OnSurface,
            focusedContainerColor = TvColors.OnSurface,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
        )
    }
}

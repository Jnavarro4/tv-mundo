package com.josenavarro.tvmundo.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.josenavarro.tvmundo.ui.CatalogIndex
import com.josenavarro.tvmundo.ui.MainViewModel
import com.josenavarro.tvmundo.ui.components.CenteredMessage
import com.josenavarro.tvmundo.ui.components.ChannelGrid
import com.josenavarro.tvmundo.ui.theme.TvColors

private val letterRows = "ABCDEFGHIJKLMNÑOPQRSTUVWXYZ1234567890".chunked(7)

@Composable
fun SearchTab(
    vm: MainViewModel,
    index: CatalogIndex,
    favorites: Set<String>,
    restoreFocus: Boolean,
    modifier: Modifier = Modifier,
) {
    val query = vm.searchQuery
    val results = remember(index, query) { vm.search(index, query) }

    Row(modifier.padding(start = 48.dp, top = 16.dp)) {
        Column(Modifier.width(400.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(TvColors.Surface, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    if (query.isEmpty()) "Escribe el nombre del canal…" else query,
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
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        Box(Modifier.weight(1f).fillMaxHeight()) {
            when {
                query.isBlank() -> CenteredMessage("Usa el teclado para buscar entre ${index.channels.size} canales.")
                results.isEmpty() -> CenteredMessage("No se encontraron canales para «$query».")
                else -> ChannelGrid(
                    channels = results,
                    favorites = favorites,
                    focusChannelId = vm.lastChannelId,
                    requestFocus = restoreFocus,
                    onPlay = { vm.play("Búsqueda: $query", results, it) },
                    onToggleFavorite = vm::toggleFavorite,
                )
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
    modifier: Modifier = Modifier,
) {
    // Al entrar al teclado desde la barra de pestañas el foco va a la "A" (o a la última tecla usada).
    val firstKey = remember { FocusRequester() }
    Column(modifier.focusRestorer(firstKey).focusGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        letterRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { ch ->
                    val keyModifier = Modifier.size(width = 48.dp, height = 44.dp)
                    Key(ch.toString(), if (ch == 'A') keyModifier.focusRequester(firstKey) else keyModifier) { onKey(ch.toString()) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key("Espacio", Modifier.size(width = 160.dp, height = 44.dp), onSpace)
            Key("⌫", Modifier.size(width = 104.dp, height = 44.dp), onDelete)
            Key("Borrar", Modifier.size(width = 104.dp, height = 44.dp), onClear)
        }
    }
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
            Text(label, fontSize = if (label.length > 1) 16.sp else 20.sp)
        }
    }
}

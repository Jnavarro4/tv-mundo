package com.josenavarro.tvmundo.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.josenavarro.tvmundo.data.CatalogIndex
import com.josenavarro.tvmundo.ui.MainViewModel
import com.josenavarro.tvmundo.ui.components.RequestFocusWhenReady
import com.josenavarro.tvmundo.ui.components.ScreenHeader
import com.josenavarro.tvmundo.ui.components.channelCount
import com.josenavarro.tvmundo.ui.theme.TvColors
import java.text.DateFormat
import java.util.Date

private val homeCountryOptions = listOf("CO", "MX", "AR", "ES", "CL", "PE", "VE", "EC", "US")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, index: CatalogIndex) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val restore = remember { vm.consumeRestoreFocus() }
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    val first = remember { FocusRequester() }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader("Ajustes")
        Column(
            Modifier.padding(start = 32.dp, end = 40.dp, top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingRow(
                title = "Vista previa en vivo en el Inicio",
                subtitle = "Reproduce sin sonido el canal enfocado. Desactívala si tu TV va lenta.",
                value = if (settings.livePreview) "Activada" else "Desactivada",
                modifier = Modifier.focusRequester(first),
            ) { vm.updateSettings { it.copy(livePreview = !it.livePreview) } }

            SettingRow(
                title = "Abrir el último canal al iniciar",
                subtitle = "Como una TV tradicional: al abrir la app sigue el último canal que viste.",
                value = if (settings.autoplayLast) "Activado" else "Desactivado",
            ) { vm.updateSettings { it.copy(autoplayLast = !it.autoplayLast) } }

            Text("País principal", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Aparece primero en Países y tiene su propia fila en el Inicio.",
                style = MaterialTheme.typography.bodyMedium,
                color = TvColors.OnSurfaceDim,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                homeCountryOptions.mapNotNull { index.countryNames[it] }.forEach { country ->
                    val selected = country.code == settings.homeCountry
                    SmallButton(
                        (if (selected) "✓ " else "") + "${country.flag} ${country.name}",
                    ) { vm.updateSettings { it.copy(homeCountry = country.code) } }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                SmallButton(if (vm.refreshing) "Actualizando…" else "↻ Actualizar lista de canales") { vm.refresh() }
                SmallButton("🗑 Borrar historial (${history.size})") { vm.clearHistory() }
            }

            Text("Información", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            InfoLine("Versión", version)
            InfoLine("Canales", "${channelCount(index.channels.size)} de ${index.countries.size} países")
            InfoLine("Lista actualizada", dateFormat.format(Date(index.updatedAt)) + " (se refresca sola cada 12 h)")
            val suggestions = vm.suggestions
            InfoLine(
                "Sugeridos con IA",
                if (suggestions == null || suggestions.generatedAt.isBlank()) "Aún no hay sugeridos publicados"
                else "${suggestions.events.size} eventos · ${suggestions.source} · ${suggestions.generatedAt.take(16).replace('T', ' ')} UTC",
            )
            InfoLine("Fuente de canales", "iptv-org (github.com/iptv-org/iptv) · sin cuentas ni anuncios")
        }
    }
    RequestFocusWhenReady(first, Unit, enabled = restore)
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvColors.Surface,
            contentColor = TvColors.OnSurface,
            focusedContainerColor = TvColors.SurfaceFocused,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, TvColors.FocusBorder), shape = shape),
        ),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TvColors.OnSurfaceDim)
            }
            Spacer(Modifier.width(16.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = TvColors.Accent)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TvColors.OnSurfaceDim, modifier = Modifier.width(180.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

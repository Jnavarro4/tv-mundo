package com.josenavarro.tvmundo.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josenavarro.tvmundo.TvMundoApplication
import com.josenavarro.tvmundo.data.Catalog
import com.josenavarro.tvmundo.data.CatalogRepository
import com.josenavarro.tvmundo.data.Category
import com.josenavarro.tvmundo.data.Channel
import com.josenavarro.tvmundo.data.Country
import com.josenavarro.tvmundo.data.FeaturedChannels
import com.josenavarro.tvmundo.data.Translations
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Datos del catálogo ya indexados para las pantallas. */
class CatalogIndex(catalog: Catalog) {
    val updatedAt = catalog.updatedAt
    val channels: List<Channel> = catalog.channels
    private val byId: Map<String, Channel> = channels.associateBy { it.id }

    val byCountry: Map<String, List<Channel>> = channels.groupBy { it.country }
    val byCategory: Map<String, List<Channel>> =
        channels.flatMap { c -> c.categories.map { it to c } }.groupBy({ it.first }, { it.second })

    val countryNames: Map<String, Country> = catalog.countries.associateBy { it.code }

    /** Colombia primero y el resto en orden alfabético. */
    val countries: List<Pair<Country, Int>> = catalog.countries
        .map { it to (byCountry[it.code]?.size ?: 0) }
        .filter { it.second > 0 }
        .sortedWith(compareBy<Pair<Country, Int>> { it.first.code != "CO" }.thenBy { Translations.normalize(it.first.name) })

    val categories: List<Pair<Category, Int>> = catalog.categories
        .map { it to (byCategory[it.id]?.size ?: 0) }
        .filter { it.second > 0 }
        .sortedWith(
            compareBy<Pair<Category, Int>> {
                Translations.categoryOrder.indexOf(it.first.id).let { i -> if (i < 0) Int.MAX_VALUE else i }
            }.thenBy { Translations.normalize(it.first.name) }
        )

    val featured: List<Channel> = FeaturedChannels.ids.mapNotNull { entry ->
        val id = entry.substringBefore('@')
        val feed = entry.substringAfter('@', "").ifBlank { null }
        val channel = byId[id] ?: return@mapNotNull null
        if (feed == null) return@mapNotNull channel
        val streams = channel.streams.filter { it.feed.equals(feed, ignoreCase = true) }
        if (streams.isEmpty()) channel
        else channel.copy(name = streams.first().title ?: "${channel.name} $feed", streams = streams)
    }

    fun channel(id: String): Channel? = byId[id]
}

sealed interface Screen {
    data object Home : Screen
    data class CountryChannels(val code: String) : Screen
    data class CategoryChannels(val id: String) : Screen
    data class Player(val title: String, val channels: List<Channel>, val startIndex: Int) : Screen
}

enum class HomeTab(val label: String) {
    Featured("Destacados"),
    Countries("Países"),
    Categories("Temáticas"),
    Favorites("Favoritos"),
    Search("Buscar"),
}

sealed interface LoadState {
    data class Loading(val message: String) : LoadState
    data class Error(val message: String) : LoadState
    data class Ready(val index: CatalogIndex) : LoadState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TvMundoApplication
    private val repository = app.catalogRepository
    private val favoritesRepository = app.favoritesRepository

    var loadState: LoadState by mutableStateOf(LoadState.Loading("Cargando canales…"))
        private set

    /** True mientras se actualiza el catálogo en segundo plano. */
    var refreshing by mutableStateOf(false)
        private set

    val backStack = mutableStateListOf<Screen>(Screen.Home)

    var selectedTab by mutableStateOf(HomeTab.Featured)
    var searchQuery by mutableStateOf("")

    /** Último canal reproducido, para devolverle el foco al salir del reproductor. */
    var lastChannelId by mutableStateOf<String?>(null)
    var lastCountryCode by mutableStateOf<String?>(null)
    var lastCategoryId by mutableStateOf<String?>(null)

    /** Se activa al volver atrás para que la pantalla anterior recupere el foco. */
    private var restoreFocus = false

    fun consumeRestoreFocus(): Boolean = restoreFocus.also { restoreFocus = false }

    /** Mensaje corto tipo "toast". */
    var message by mutableStateOf<String?>(null)

    val favorites: StateFlow<Set<String>> = favoritesRepository.favorites
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private var refreshJob: Job? = null

    /** False hasta terminar de leer la caché de disco (evita descargar dos veces al abrir). */
    private var cacheLoaded = false

    init {
        viewModelScope.launch {
            val cached = repository.loadCached()
            if (cached != null) {
                loadState = LoadState.Ready(CatalogIndex(cached))
            }
            cacheLoaded = true
            refreshIfStale()
        }
    }

    /** Refresca en segundo plano si la caché tiene más de 12 h (o no existe). */
    fun refreshIfStale() {
        if (!cacheLoaded) return
        val current = (loadState as? LoadState.Ready)?.index
        if (current == null || System.currentTimeMillis() - current.updatedAt > CatalogRepository.MAX_AGE_MS) refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val hadData = loadState is LoadState.Ready
            if (!hadData) loadState = LoadState.Loading("Descargando la lista de canales de iptv-org…\nLa primera vez puede tardar un poco.")
            refreshing = true
            try {
                val catalog = repository.refresh()
                loadState = LoadState.Ready(CatalogIndex(catalog))
            } catch (e: Exception) {
                if (!hadData) loadState = LoadState.Error("No se pudo descargar la lista de canales.\n${e.message ?: e::class.simpleName}")
            } finally {
                refreshing = false
            }
        }
    }

    fun navigate(screen: Screen) {
        backStack.add(screen)
    }

    /** Devuelve false si ya estamos en la pantalla inicial. */
    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        restoreFocus = true
        return true
    }

    fun play(title: String, channels: List<Channel>, channel: Channel) {
        val index = channels.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
        lastChannelId = channel.id
        navigate(Screen.Player(title, channels, index))
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            val added = favoritesRepository.toggle(channel.id)
            message = if (added) "★ ${channel.name} agregado a Favoritos" else "${channel.name} quitado de Favoritos"
        }
    }

    fun search(index: CatalogIndex, query: String): List<Channel> {
        val q = Translations.normalize(query.trim())
        if (q.isEmpty()) return emptyList()
        val (starts, contains) = index.channels
            .asSequence()
            .map { it to Translations.normalize(it.name) }
            .filter { (_, name) -> name.contains(q) }
            .partition { (_, name) -> name.startsWith(q) }
        return (starts + contains).map { it.first }.take(200)
    }
}

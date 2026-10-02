package com.josenavarro.tvmundo.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josenavarro.tvmundo.TvMundoApplication
import com.josenavarro.tvmundo.data.AppSettings
import com.josenavarro.tvmundo.data.Catalog
import com.josenavarro.tvmundo.data.CatalogIndex
import com.josenavarro.tvmundo.data.CatalogRepository
import com.josenavarro.tvmundo.data.Channel
import com.josenavarro.tvmundo.data.EventSchedule
import com.josenavarro.tvmundo.data.SmartSearch
import com.josenavarro.tvmundo.data.SuggestedEvent
import com.josenavarro.tvmundo.data.Suggestions
import com.josenavarro.tvmundo.data.WatchEntry
import com.josenavarro.tvmundo.data.WatchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Secciones de la barra lateral. */
enum class Section(val label: String) {
    Home("Inicio"),
    Countries("Países"),
    Categories("Temáticas"),
    Favorites("Favoritos"),
    Search("Buscar"),
    Settings("Ajustes"),
}

/** Pantallas que se apilan encima de la sección actual. */
sealed interface Screen {
    data class CountryChannels(val code: String) : Screen
    data class CategoryChannels(val id: String) : Screen
    data class EventDetail(val eventId: String) : Screen
    data class Player(val title: String, val channels: List<Channel>, val startIndex: Int) : Screen
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
    private val userData = app.userDataRepository
    private val suggestionsRepository = app.suggestionsRepository

    var loadState: LoadState by mutableStateOf(LoadState.Loading("Cargando canales…"))
        private set

    /** True mientras se actualiza el catálogo en segundo plano. */
    var refreshing by mutableStateOf(false)
        private set

    var section by mutableStateOf(Section.Home)
    val backStack = mutableStateListOf<Screen>()

    var searchQuery by mutableStateOf("")

    /** Último canal reproducido / país / temática, para devolver el foco al volver. */
    var lastChannelId by mutableStateOf<String?>(null)
    var lastCountryCode by mutableStateOf<String?>(null)
    var lastCategoryId by mutableStateOf<String?>(null)

    /**
     * Elemento del inicio que tenía el foco ("fila|elemento"), para restaurarlo.
     * Variable normal (no estado de Compose) para no recomponer al mover el foco.
     */
    var homeFocusKey: String? = null
    var homeVisited = false

    /** Mensaje corto tipo "toast". */
    var message by mutableStateOf<String?>(null)

    /** Sugeridos del día generados con IA (null si aún no hay). */
    var suggestions by mutableStateOf<Suggestions?>(null)
        private set

    /** Reloj que avanza cada minuto: decide qué eventos están "en vivo". */
    var now by mutableLongStateOf(System.currentTimeMillis())
        private set

    val favorites: StateFlow<Set<String>> = favoritesRepository.favorites
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val history: StateFlow<List<WatchEntry>> = userData.history
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val settings: StateFlow<AppSettings> = userData.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** Se activa al volver atrás para que la pantalla anterior recupere el foco. */
    private var restoreFocus = false

    fun consumeRestoreFocus(): Boolean = restoreFocus.also { restoreFocus = false }

    private var catalog: Catalog? = null
    private var cacheLoaded = false
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            val t0 = android.os.SystemClock.elapsedRealtime()
            // Ajustes y caché se leen en paralelo para mostrar el Inicio lo antes posible.
            val homeCountry = async { userData.settings.first().homeCountry }
            val cachedSuggestions = async { suggestionsRepository.loadCached() }
            val cached = repository.loadCached()
            val t1 = android.os.SystemClock.elapsedRealtime()
            // Los sugeridos guardados se aplican antes de mostrar el Inicio para que las filas
            // no cambien de lugar (ni el foco) un instante después.
            suggestions = cachedSuggestions.await()
            if (cached != null) publish(cached, homeCountry.await())
            android.util.Log.i("TvMundoPerf", "cache=${t1 - t0}ms index=${android.os.SystemClock.elapsedRealtime() - t1}ms")
            cacheLoaded = true
            refreshIfStale()
            maybeAutoplayLast()
        }
        // Si cambia el país principal se reconstruye el índice (orden de filas y países).
        viewModelScope.launch {
            userData.settings.map { it.homeCountry }.distinctUntilChanged().collect { code ->
                val current = catalog ?: return@collect
                val ready = loadState as? LoadState.Ready
                if (ready == null || ready.index.homeCountry != code) publish(current, code)
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                now = System.currentTimeMillis()
            }
        }
    }

    private suspend fun publish(newCatalog: Catalog, homeCountry: String) {
        catalog = newCatalog
        val index = withContext(Dispatchers.Default) { CatalogIndex(newCatalog, homeCountry) }
        loadState = LoadState.Ready(index)
    }

    private suspend fun maybeAutoplayLast() {
        if (!userData.settings.first().autoplayLast) return
        val index = (loadState as? LoadState.Ready)?.index ?: return
        val recent = WatchHistory.recent(userData.history.first(), index)
        if (recent.isNotEmpty() && backStack.none { it is Screen.Player }) play("Seguir viendo", recent, recent.first())
    }

    /** Refresca en segundo plano lo que esté viejo (catálogo > 12 h, sugeridos > 1 h). */
    fun refreshIfStale() {
        if (!cacheLoaded) return
        val current = (loadState as? LoadState.Ready)?.index
        if (current == null || System.currentTimeMillis() - current.updatedAt > CatalogRepository.MAX_AGE_MS) refresh()
        refreshSuggestionsIfStale()
        now = System.currentTimeMillis()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val hadData = loadState is LoadState.Ready
            if (!hadData) loadState = LoadState.Loading("Descargando la lista de canales de iptv-org…\nLa primera vez puede tardar un poco.")
            refreshing = true
            try {
                val fresh = repository.refresh()
                publish(fresh, settings.value.homeCountry)
                if (hadData) message = "Lista de canales actualizada"
            } catch (e: Exception) {
                if (!hadData) loadState = LoadState.Error("No se pudo descargar la lista de canales.\n${e.message ?: e::class.simpleName}")
                else message = "No se pudo actualizar la lista (sin conexión)"
            } finally {
                refreshing = false
            }
        }
    }

    private fun refreshSuggestionsIfStale() {
        if (!suggestionsRepository.isStale()) return
        viewModelScope.launch {
            suggestionsRepository.refresh()?.let { suggestions = it }
        }
    }

    /** Eventos del día que todavía no terminaron. */
    fun upcomingEvents(): List<SuggestedEvent> = EventSchedule.upcoming(suggestions?.events.orEmpty(), now)

    fun event(id: String): SuggestedEvent? = suggestions?.events?.firstOrNull { it.id == id }

    /** Cambia de sección desde la barra lateral; el contenido nuevo toma el foco. */
    fun select(newSection: Section) {
        backStack.clear()
        section = newSection
        restoreFocus = true
    }

    fun navigate(screen: Screen) {
        backStack.add(screen)
    }

    /** Devuelve false si no hay nada que cerrar (en ese caso Atrás va al Inicio o sale). */
    fun back(): Boolean {
        if (backStack.isNotEmpty()) {
            backStack.removeAt(backStack.lastIndex)
            restoreFocus = true
            return true
        }
        if (section != Section.Home) {
            section = Section.Home
            restoreFocus = true
            return true
        }
        return false
    }

    fun play(title: String, channels: List<Channel>, channel: Channel) {
        if (channels.isEmpty()) return
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

    fun recordWatch(channel: Channel, seconds: Long) {
        if (seconds <= 0) return
        viewModelScope.launch { userData.recordWatch(channel.id, seconds) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            userData.clearHistory()
            message = "Historial borrado"
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { userData.update(transform) }
    }

    fun search(index: CatalogIndex, query: String): SmartSearch.Result =
        SmartSearch(index).search(query, upcomingEvents())
}

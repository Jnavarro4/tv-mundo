package com.josenavarro.tvmundo.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.favoritesStore by preferencesDataStore(name = "favoritos")

/** Ids de canales favoritos, guardados con DataStore. */
class FavoritesRepository(private val context: Context) {

    private val key = stringSetPreferencesKey("channel_ids")

    val favorites: Flow<Set<String>> = context.favoritesStore.data.map { it[key] ?: emptySet() }

    /** Marca o desmarca un canal. Devuelve true si quedó como favorito. */
    suspend fun toggle(channelId: String): Boolean {
        var added = false
        context.favoritesStore.edit { prefs ->
            val current = prefs[key] ?: emptySet()
            added = channelId !in current
            prefs[key] = if (added) current + channelId else current - channelId
        }
        return added
    }
}

package com.josenavarro.tvmundo

import android.app.Application
import com.josenavarro.tvmundo.data.CatalogRepository
import com.josenavarro.tvmundo.data.FavoritesRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Contenedor de dependencias sencillo, sin framework de inyección. */
class TvMundoApplication : Application() {

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val catalogRepository by lazy { CatalogRepository(this, httpClient) }
    val favoritesRepository by lazy { FavoritesRepository(this) }
}

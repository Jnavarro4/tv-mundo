package com.josenavarro.tvmundo

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.josenavarro.tvmundo.data.CatalogRepository
import com.josenavarro.tvmundo.data.FavoritesRepository
import com.josenavarro.tvmundo.data.SuggestionsRepository
import com.josenavarro.tvmundo.data.UserDataRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Contenedor de dependencias sencillo, sin framework de inyección. */
class TvMundoApplication : Application(), SingletonImageLoader.Factory {

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val catalogRepository by lazy { CatalogRepository(this, httpClient) }
    val favoritesRepository by lazy { FavoritesRepository(this) }
    val userDataRepository by lazy { UserDataRepository(this) }
    val suggestionsRepository by lazy { SuggestionsRepository(this, httpClient) }

    /** Logos: caché en memoria y en disco para que las filas se vean al instante. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { httpClient }))
            }
            .memoryCache {
                MemoryCache.Builder().maxSizePercent(context, 0.20).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("logos"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .crossfade(150)
            .build()
}

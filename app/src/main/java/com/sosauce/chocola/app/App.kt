@file:OptIn(KoinExperimentalAPI::class)

package com.sosauce.chocola.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.sosauce.chocola.app.di.appModule
import com.sosauce.chocola.app.di.bakeryModule
import com.sosauce.chocola.core.data.diagnostics.DiagBootstrap
import com.sosauce.chocola.core.data.plugin.PluginNetwork
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.androix.startup.KoinStartup
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinConfiguration

class App : Application(), KoinStartup, SingletonImageLoader.Factory {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Install as early as possible so crashes during startup are also recorded.
        DiagBootstrap.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        // Re-check the switch after the app context is fully ready, and open log
        // storage if the user already enabled recording in a previous session.
        DiagBootstrap.ensureReady(this)
    }

    override fun onKoinStartup() = koinConfiguration {
        androidContext(this@App)
        modules(appModule, bakeryModule)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        // Network images (plugin artwork / covers) share PluginNetwork's public-HTTPS client.
        // Local file:// and content:// still use Coil's default fetchers.
        val client = runCatching {
            org.koin.core.context.GlobalContext.get().get<OkHttpClient>()
        }.getOrElse { PluginNetwork.client() }
        return ImageLoader.Builder(context)
            .crossfade(true)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { client }))
            }
            .build()
    }
}

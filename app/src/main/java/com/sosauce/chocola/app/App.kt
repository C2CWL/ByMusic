@file:OptIn(KoinExperimentalAPI::class)

package com.sosauce.chocola.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import com.sosauce.chocola.app.di.appModule
import com.sosauce.chocola.core.data.diagnostics.DiagBootstrap
import com.sosauce.chocola.app.di.bakeryModule
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

    override fun onKoinStartup() = koinConfiguration {
        androidContext(this@App)
        modules(appModule, bakeryModule)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .crossfade(true)
            .build()
    }
}
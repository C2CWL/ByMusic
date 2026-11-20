package com.sosauce.chocola.app.di

import androidx.room.Room
import com.sosauce.chocola.core.data.audio.EqualizerManager
import com.sosauce.chocola.core.data.auto.AndroidAutoHelper
import com.sosauce.chocola.core.data.datastore.UserPreferences
import com.sosauce.chocola.core.data.download.DownloadTransfer
import com.sosauce.chocola.core.data.download.OnlineDownloadPipeline
import com.sosauce.chocola.core.data.library.AbstractTracksScanner
import com.sosauce.chocola.core.data.library.FoldersClassifier
import com.sosauce.chocola.core.data.library.FoldersRepository
import com.sosauce.chocola.core.data.library.SafManager
import com.sosauce.chocola.core.data.local.FavoritesDatabase
import com.sosauce.chocola.core.data.local.MIGRATION_1_2
import com.sosauce.chocola.core.data.local.MIGRATION_2_3
import com.sosauce.chocola.core.data.local.PlaylistCleanup
import com.sosauce.chocola.core.data.local.PlaylistDatabase
import com.sosauce.chocola.core.data.lyrics.LyricsParser
import com.sosauce.chocola.core.data.plugin.AssetPluginLibrarySource
import com.sosauce.chocola.core.data.plugin.HttpPluginSourceFetcher
import com.sosauce.chocola.core.data.plugin.OkHttpPluginHttpClient
import com.sosauce.chocola.core.data.plugin.OnlineMusicItemStore
import com.sosauce.chocola.core.data.plugin.PluginHost
import com.sosauce.chocola.core.data.plugin.PluginHttpClient
import com.sosauce.chocola.core.data.plugin.PluginLogcat
import com.sosauce.chocola.core.data.plugin.PluginManager
import com.sosauce.chocola.core.data.plugin.PluginMusicService
import com.sosauce.chocola.core.data.plugin.PluginNetwork
import com.sosauce.chocola.core.data.plugin.PluginOnlineLyricsProvider
import com.sosauce.chocola.core.data.plugin.PluginOnlineMediaResolver
import com.sosauce.chocola.core.data.plugin.PluginStore
import com.sosauce.chocola.core.domain.online.OnlineLyricsProvider
import com.sosauce.chocola.core.domain.online.OnlineMediaResolver
import com.sosauce.chocola.feature.online.OnlineSearchViewModel
import com.sosauce.chocola.feature.plugins.presentation.PluginsViewModel
import com.sosauce.chocola.core.data.widgets.WidgetsHelper
import com.sosauce.chocola.core.presentation.components.dialogs.DeletionViewModel
import com.sosauce.chocola.core.presentation.components.dialogs.tracksDetails.TracksDetailsDialogViewModel
import com.sosauce.chocola.core.presentation.player.MusicViewModel
import com.sosauce.chocola.feature.library.presentation.album.AlbumDetailsViewModel
import com.sosauce.chocola.feature.library.presentation.album.AlbumsViewModel
import com.sosauce.chocola.feature.library.presentation.artist.ArtistDetailsViewModel
import com.sosauce.chocola.feature.library.presentation.artist.ArtistsViewModel
import com.sosauce.chocola.feature.library.presentation.main.MainViewModel
import com.sosauce.chocola.feature.lyrics.presentation.LyricsEditorViewModel
import com.sosauce.chocola.feature.metadata.presentation.MetadataViewModel
import com.sosauce.chocola.feature.playlists.presentation.PlaylistDetailsViewModel
import com.sosauce.chocola.feature.playlists.presentation.PlaylistViewModel
import com.sosauce.chocola.feature.quickplay.presentation.QuickPlayViewModel
import com.sosauce.chocola.feature.recognition.data.AndroidAudioRecorder
import com.sosauce.chocola.feature.recognition.data.RecognitionAudioFiles
import com.sosauce.chocola.feature.recognition.data.RecognitionHistoryStore
import com.sosauce.chocola.feature.recognition.data.RecognitionSession
import com.sosauce.chocola.feature.recognition.domain.EmptyRecognitionPluginRegistry
import com.sosauce.chocola.feature.recognition.domain.RecognitionPluginRegistry
import com.sosauce.chocola.feature.recognition.presentation.RecognitionViewModel
import com.sosauce.chocola.feature.settings.presentation.BackupViewModel
import com.sosauce.chocola.feature.settings.presentation.PlaybackSettingsViewModel
import com.sosauce.chocola.feature.settings.presentation.SettingsLibraryViewModel
import com.sosauce.chocola.feature.settings.presentation.nowplayingbuttons.NowPlayingButtonsViewModel
import com.sosauce.chocola.feature.setup.presentation.SetupViewModel
import com.sosauce.chocola.feature.transformer.presentation.TransformerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import com.sosauce.chocola.core.data.plugin.PluginNetwork
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

val appModule = module {
    single {
        Room.databaseBuilder(
            context = androidApplication(),
            klass = PlaylistDatabase::class.java,
            name = "playlist.db"
        )
            .addMigrations(MIGRATION_1_2)
            .addMigrations(MIGRATION_2_3)
            //.addCallback(DEFAULT_PLAYLISTS_CALLBACK)
            .build()
            .dao
    }

    single {
        Room.databaseBuilder(androidApplication(), FavoritesDatabase::class.java, "favorites.db")
            .build().dao
    }

    single { CoroutineScope(Dispatchers.IO + SupervisorJob()) }

    // Online playback foundation. Koin singles are created on first use only.
    single {
        PluginNetwork.client(
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        )
    }
    // MusicFree-format plugins imported by the user. ByMusic ships no plugin and no source;
    // without an imported plugin nothing here makes a network request.
    single<PluginHttpClient> { OkHttpPluginHttpClient(get()) }
    single {
        val app = androidApplication()
        val version = runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName
        }.getOrNull() ?: "0.0.0"
        PluginHost(
            libraries = AssetPluginLibrarySource(app),
            http = get(),
            log = PluginLogcat::log,
            appVersion = version
        )
    }
    single { PluginStore(java.io.File(androidApplication().filesDir, PluginStore.DIR_NAME)) }
    single {
        OnlineMusicItemStore(
            java.io.File(
                java.io.File(androidApplication().filesDir, PluginStore.DIR_NAME),
                OnlineMusicItemStore.FILE_NAME
            )
        )
    }
    single { PluginManager(get(), get(), HttpPluginSourceFetcher(get())) }
    single { PluginMusicService(get(), get()) }
    single { OnlineDownloadPipeline(androidApplication(), get(), DownloadTransfer(get())) }
    // getMediaSource from the track's plugin, highest quality first, one tier down at a time.
    single<OnlineMediaResolver> { PluginOnlineMediaResolver(get()) }
    single<OnlineLyricsProvider> { PluginOnlineLyricsProvider(get()) }
    viewModelOf(::PluginsViewModel)
    viewModelOf(::OnlineSearchViewModel)

    singleOf(::AbstractTracksScanner)
    singleOf(::LyricsParser)
    singleOf(::FoldersRepository)
    singleOf(::SafManager)
    singleOf(::UserPreferences)
    singleOf(::EqualizerManager)
    singleOf(::AndroidAutoHelper)
    singleOf(::WidgetsHelper)
    singleOf(::PlaylistCleanup)
    singleOf(::FoldersClassifier)



    viewModelOf(::MusicViewModel)
    viewModelOf(::MetadataViewModel)
    viewModelOf(::PlaylistViewModel)
    viewModelOf(::PlaylistDetailsViewModel)
    viewModelOf(::QuickPlayViewModel)
    viewModelOf(::ArtistsViewModel)
    viewModelOf(::ArtistDetailsViewModel)
    viewModelOf(::AlbumsViewModel)
    viewModelOf(::AlbumDetailsViewModel)
    viewModelOf(::MainViewModel)
    viewModelOf(::PlaybackSettingsViewModel)
    viewModelOf(::NowPlayingButtonsViewModel)
    viewModelOf(::TransformerViewModel)
    viewModelOf(::DeletionViewModel)
    viewModelOf(::LyricsEditorViewModel)
    viewModelOf(::SettingsLibraryViewModel)
    viewModelOf(::SetupViewModel)
    viewModelOf(::TracksDetailsDialogViewModel)
    viewModelOf(::BackupViewModel)

    // Song recognition: plugin-provided only. No plugin runtime yet, so the registry is
    // empty and no entry is shown. No built-in service, no keys.
    single<RecognitionPluginRegistry> { EmptyRecognitionPluginRegistry }
    single {
        RecognitionHistoryStore(
            java.io.File(androidApplication().filesDir, RecognitionHistoryStore.FILE_NAME)
        )
    }
    single { RecognitionAudioFiles(androidApplication().cacheDir) }
    factory {
        RecognitionSession(
            recorder = AndroidAudioRecorder(androidApplication()),
            audioFiles = get(),
            registry = get(),
            history = get()
        )
    }
    viewModelOf(::RecognitionViewModel)
}

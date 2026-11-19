package com.sosauce.chocola.core.domain.online

/**
 * Lyrics of an online track as the plugin returned them (MusicFree `getLyric`):
 * [raw] is `rawLrc` (LRC or TTML), [translation] is the optional `translation` LRC.
 */
data class OnlineLyricsText(
    val raw: String,
    val translation: String? = null
)

/** Lyrics for an online track, from the plugin that provided it. */
fun interface OnlineLyricsProvider {
    /** Returns null when the plugin has no lyrics. Never touches local files. */
    suspend fun lyrics(id: OnlineMediaId): OnlineLyricsText?
}

object NoOnlineLyrics : OnlineLyricsProvider {
    override suspend fun lyrics(id: OnlineMediaId): OnlineLyricsText? = null
}

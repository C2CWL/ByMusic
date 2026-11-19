package com.sosauce.chocola.core.domain.online

/** Lyrics text (LRC or TTML) for an online track, from the plugin that provided it. */
fun interface OnlineLyricsProvider {
    /** Returns null when the plugin has no lyrics. Never touches local files. */
    suspend fun rawLyrics(id: OnlineMediaId): String?
}

object NoOnlineLyrics : OnlineLyricsProvider {
    override suspend fun rawLyrics(id: OnlineMediaId): String? = null
}

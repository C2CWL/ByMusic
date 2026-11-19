package com.sosauce.chocola.core.data.lyrics

/**
 * Finds a lyrics file next to a song with the same base name.
 * An existing `.lrc` keeps priority so current libraries behave exactly as before;
 * a `.ttml` file is used only when there is no `.lrc`.
 */
internal object LyricsSidecar {

    enum class Kind { LRC, TTML }

    data class Found(val path: String, val kind: Kind)

    fun candidates(songPath: String): List<Found> {
        val parent = songPath.substringBeforeLast('/')
        val fileName = songPath.substringAfterLast('/')
        val base = if (fileName.contains('.')) fileName.substringBeforeLast('.') else fileName
        return listOf(
            Found("$parent/$base.lrc", Kind.LRC),
            Found("$parent/$base.ttml", Kind.TTML)
        )
    }

    fun find(songPath: String, exists: (String) -> Boolean): Found? =
        candidates(songPath).firstOrNull { exists(it.path) }

    /** Whether a picked file name or URI looks like a lyrics file we can read. */
    fun isSupportedLyricsName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".lrc") || lower.endsWith(".ttml")
    }
}

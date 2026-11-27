package com.sosauce.chocola.feature.playing.presentation.expand

internal class PlayerDockEntry(val route: Any, val instance: Long)

/** Outgoing and incoming entries can coexist, including while a dock is still measuring. */
internal class PlayerDockRegistry<T>(private val entries: MutableMap<PlayerDockEntry, T?>) {
    private var sequence = 0L

    fun newEntry(route: Any) = PlayerDockEntry(route, ++sequence)

    fun attach(entry: PlayerDockEntry) {
        if (!entries.containsKey(entry)) entries[entry] = null
    }

    fun detach(entry: PlayerDockEntry) {
        entries.remove(entry)
    }

    fun update(entry: PlayerDockEntry, descriptor: T?) {
        if (entries.containsKey(entry)) entries[entry] = descriptor
    }

    fun active(route: Any): PlayerDockEntry? = entries.keys
        .asSequence()
        .filter { it.route == route }
        .maxByOrNull { it.instance }

    fun descriptor(entry: PlayerDockEntry?): T? = if (entry != null) entries[entry] else null
}

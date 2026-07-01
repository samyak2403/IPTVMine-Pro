package com.samyak.player

import java.io.Serializable

/**
 * A single episode entry passed to [PlayerActivity] so it can offer "Next Episode"
 * playback. [link] is the provider's (unresolved) episode link which is turned into a
 * playable stream on demand via [StreamResolver].
 */
data class EpisodeItem(
    val title: String,
    val link: String,
    val type: String,
    val headers: HashMap<String, String>? = null
) : Serializable

/**
 * A single playable stream candidate (a mirror/quality) for an item. The player tries
 * them in order, falling back to the next when one can't be played.
 */
data class StreamOption(
    val url: String,
    val headers: HashMap<String, String>? = null
) : Serializable

/**
 * Resolves a provider episode link into playable stream candidates.
 *
 * The Player module is standalone and cannot depend on the app's scraper code, so the
 * app registers an implementation (backed by its provider runner) into
 * [StreamResolverHolder]. The contract is callback based, letting the implementation do
 * its async work and report back on the main thread.
 */
interface StreamResolver {
    fun resolve(
        providerUrl: String,
        scraperValue: String,
        link: String,
        type: String,
        callback: (streams: List<StreamOption>?, error: String?) -> Unit
    )
}

/** Process-wide service locator for the active [StreamResolver]. */
object StreamResolverHolder {
    @Volatile
    var resolver: StreamResolver? = null
}

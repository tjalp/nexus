package net.tjalp.nexus.api.hologram

import java.util.*

/**
 * Represents the visibility of a [Hologram]
 */
sealed interface HologramVisibility {

    /**
     * Represents a hologram that is visible to all players.
     */
    data object Global : HologramVisibility

    /**
     * Represents a hologram that is visible only to specific players.
     *
     * @param uniqueIds The unique identifiers of the players who can see the hologram.
     */
    data class Players(val uniqueIds: Collection<UUID>) : HologramVisibility
}
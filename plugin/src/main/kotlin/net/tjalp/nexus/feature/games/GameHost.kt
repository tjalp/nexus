package net.tjalp.nexus.feature.games

import java.util.*

/**
 * Respresents the host of a game, which can either be a player or the system.
 */
sealed interface GameHost {

    /**
     * Represents a player host of the game, identified by their unique ID.
     *
     * @param uniqueId The unique identifier of the player.
     */
    data class Player(val uniqueId: UUID) : GameHost

    /**
     * Represents the system as the host of the game. This is a singleton object.
     */
    data object System : GameHost
}

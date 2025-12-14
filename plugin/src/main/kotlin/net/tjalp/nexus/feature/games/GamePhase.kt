package net.tjalp.nexus.feature.games

import org.bukkit.entity.Player
import org.spongepowered.configurate.reactive.Disposable

/**
 * Represents a phase within a game, handling loading, starting, and player management.
 */
interface GamePhase : Disposable {

    /**
     * Loads the game phase, preparing it for start.
     *
     * @param previous The previous game phase, if any.
     */
    suspend fun load(previous: GamePhase?) {}

    /**
     * Starts the game phase, making it active.
     *
     * @param previous The previous game phase, if any.
     */
    suspend fun start(previous: GamePhase?)

    /**
     * Handles a player joining the game phase.
     *
     * @param player The player joining.
     * @return The result of the join attempt.
     */
    suspend fun onJoin(player: Player): JoinResult

    /**
     * Handles a player leaving the game phase.
     *
     * @param player The player leaving.
     */
    fun onLeave(player: Player)
}
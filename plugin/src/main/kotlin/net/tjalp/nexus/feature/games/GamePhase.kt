package net.tjalp.nexus.feature.games

import org.bukkit.entity.Entity
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
     * Determines if an entity can join the game phase.
     *
     * @param entity The entity attempting to join.
     * @return The result of the join attempt.
     */
    suspend fun canJoin(entity: Entity): JoinResult = JoinResult.Success

    /**
     * Handles an entity joining the game phase.
     *
     * @param entity The entity joining.
     */
    suspend fun onJoin(entity: Entity)

    /**
     * Handles an entity leaving the game phase.
     *
     * @param entity The entity leaving.
     */
    fun onLeave(entity: Entity)
}
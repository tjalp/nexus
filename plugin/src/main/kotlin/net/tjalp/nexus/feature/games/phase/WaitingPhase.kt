package net.tjalp.nexus.feature.games.phase

import net.tjalp.nexus.feature.games.Game
import net.tjalp.nexus.feature.games.GamePhase
import org.bukkit.entity.Entity

/**
 * A generic waiting phase for games.
 * This phase handles players joining and prepares for the next phase.
 */
open class WaitingPhase(private val game: Game) : GamePhase {

    override suspend fun load(previous: GamePhase?) {}

    override suspend fun start(previous: GamePhase?) {}

    override suspend fun onJoin(entity: Entity) {}

    override fun onLeave(entity: Entity) {}

    override fun dispose() {}
}
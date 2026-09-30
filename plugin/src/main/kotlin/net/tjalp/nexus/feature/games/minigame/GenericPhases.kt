package net.tjalp.nexus.feature.games.minigame

import net.tjalp.nexus.feature.games.GamePhase
import org.bukkit.entity.Entity

class GenericLobbyPhase : GamePhase {
    override suspend fun start(previous: GamePhase?) {}
    override suspend fun onJoin(entity: Entity) {}
    override fun onLeave(entity: Entity) {}
    override fun dispose() {}
}

class GenericActivePhase : GamePhase {
    override suspend fun start(previous: GamePhase?) {}
    override suspend fun onJoin(entity: Entity) {}
    override fun onLeave(entity: Entity) {}
    override fun dispose() {}
}

class GenericFinishedPhase : GamePhase {
    override suspend fun start(previous: GamePhase?) {}
    override suspend fun onJoin(entity: Entity) {}
    override fun onLeave(entity: Entity) {}
    override fun dispose() {}
}

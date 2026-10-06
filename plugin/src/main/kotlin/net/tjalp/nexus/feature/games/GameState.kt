package net.tjalp.nexus.feature.games

import org.bukkit.entity.Entity

interface GameState {
    suspend fun enter(previous: GameState?)
    suspend fun leave(next: GameState?)
    suspend fun canJoin(entity: Entity): JoinResult = JoinResult.Success
    suspend fun onJoin(entity: Entity)
    fun onLeave(entity: Entity)
    fun dispose()
}
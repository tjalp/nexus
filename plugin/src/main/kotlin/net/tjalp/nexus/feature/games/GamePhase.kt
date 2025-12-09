package net.tjalp.nexus.feature.games

import org.bukkit.entity.Player
import org.spongepowered.configurate.reactive.Disposable

interface GamePhase : Disposable {

    suspend fun load(previous: GamePhase?)

    suspend fun start(previous: GamePhase?)

    suspend fun join(player: Player)

    suspend fun leave(player: Player)
}
package net.tjalp.nexus.feature.games

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityRemoveEvent
import org.bukkit.event.player.PlayerQuitEvent

class GameListener(
    private val game: Game
) : Listener {

    @EventHandler
    fun on(event: PlayerQuitEvent) {
        game.leave(event.player)
    }

    @EventHandler
    fun on(event: EntityRemoveEvent) {
        game.leave(event.entity)
    }
}
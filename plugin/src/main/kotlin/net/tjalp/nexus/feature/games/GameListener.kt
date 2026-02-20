package net.tjalp.nexus.feature.games

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent

class GameListener(
    private val gamesFeature: GamesFeature
) : Listener {

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        gamesFeature.getGameFor(event.player)?.leave(event.player)
    }

    @EventHandler
    fun onPlayerKick(event: PlayerKickEvent) {
        gamesFeature.getGameFor(event.player)?.leave(event.player)
    }

    @EventHandler
    fun onEntityRemoved(event: EntityRemoveFromWorldEvent) {
        val entity = event.entity
        if (entity is org.bukkit.entity.Player) return

        gamesFeature.getGameFor(entity)?.leave(entity)
    }
}

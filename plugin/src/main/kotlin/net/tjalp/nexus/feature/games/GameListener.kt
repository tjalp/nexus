package net.tjalp.nexus.feature.games

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
}

package net.tjalp.nexus.feature.playerlist

import net.kyori.adventure.text.Component
import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.FeatureKeys.PLAYER_LIST
import net.tjalp.nexus.util.defaultWaypointColor
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.toTextColor
import net.tjalp.nexus.util.unregister
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class PlayerListFeature : Feature(PLAYER_LIST), Listener {

    override fun onEnable() {
        register()

        scheduler.repeat(interval = 20L) {
            if (!NexusPlugin.configuration.features.playerList.defaultColorIsWaypointColor) return@repeat

            for (player in NexusPlugin.server.onlinePlayers) {
                player.playerListName(componentFor(player))
            }
        }
    }

    override fun onDisposed() {
        unregister()

        for (player in NexusPlugin.server.onlinePlayers) {
            player.playerListName(null)
        }
    }

    private fun componentFor(player: Player): Component {
        return player.playerListName()
            .colorIfAbsent(player.waypointColor?.toTextColor() ?: player.defaultWaypointColor)
    }

    @EventHandler
    fun on(event: PlayerJoinEvent) {
        if (!NexusPlugin.configuration.features.playerList.defaultColorIsWaypointColor) return

        event.player.playerListName(componentFor(event.player))
    }
}
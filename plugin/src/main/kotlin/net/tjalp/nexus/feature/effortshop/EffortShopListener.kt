package net.tjalp.nexus.feature.effortshop

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class EffortShopListener(private val feature: EffortShopFeature) : Listener {

    @EventHandler
    fun on(event: PlayerJoinEvent) {
        feature.sendFooter(event.player)
    }
}
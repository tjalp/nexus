package net.tjalp.nexus.gamerules

import org.bukkit.Material
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityInteractEvent
import org.bukkit.event.player.PlayerInteractEvent

class CropTramplingListener(private val feature: GameRulesFeature) : Listener {

    @EventHandler
    fun on(event: PlayerInteractEvent) {
        val block = event.clickedBlock

         if (block == null || block.type != Material.FARMLAND || event.action != Action.PHYSICAL) return

        // todo make this an actual gamerule
        val cropTramplingEnabled = feature.plugin.config.getBoolean("modules.${feature.name}.crop-trampling", true)

        if (cropTramplingEnabled) return

        event.setUseInteractedBlock(Event.Result.DENY)
    }

    @EventHandler
    fun on(event: EntityInteractEvent) {
        val block = event.block

        if (block.type != Material.FARMLAND) return

        // todo make this an actual gamerule
        val cropTramplingEnabled = feature.plugin.config.getBoolean("modules.${feature.name}.crop-trampling", true)

        if (cropTramplingEnabled) return

        event.isCancelled = true
    }
}
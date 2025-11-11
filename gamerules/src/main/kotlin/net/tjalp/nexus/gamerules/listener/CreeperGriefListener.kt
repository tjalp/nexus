package net.tjalp.nexus.gamerules.listener

import net.tjalp.nexus.gamerules.GameRulesFeature
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityExplodeEvent

class CreeperGriefListener(private val feature: GameRulesFeature) : Listener {

    @EventHandler
    fun on(event: EntityExplodeEvent) {
        val entity = event.entity
        val creeperGrievingEnabled = feature.plugin.config.getBoolean("modules.${feature.name}.creeper-grieving", true)

        if (entity.type != EntityType.CREEPER || creeperGrievingEnabled) return

        event.blockList().clear()
    }
}
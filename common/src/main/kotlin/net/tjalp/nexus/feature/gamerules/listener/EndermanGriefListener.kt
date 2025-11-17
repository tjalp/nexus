package net.tjalp.nexus.feature.gamerules.listener

import net.tjalp.nexus.feature.gamerules.GameRulesFeature
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityChangeBlockEvent

class EndermanGriefListener(private val feature: GameRulesFeature) : Listener {

    @EventHandler
    fun on(event: EntityChangeBlockEvent) {
        val entity = event.entity
        val endermanGrievingEnabled = feature.plugin.config.getBoolean("modules.${feature.name}.enderman-grieving", true)

        if (entity.type != EntityType.ENDERMAN || endermanGrievingEnabled) return

        event.isCancelled = true
    }
}
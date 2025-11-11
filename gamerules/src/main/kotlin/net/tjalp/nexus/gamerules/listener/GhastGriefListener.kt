package net.tjalp.nexus.gamerules.listener

import net.tjalp.nexus.gamerules.GameRulesFeature
import org.bukkit.entity.Fireball
import org.bukkit.entity.Ghast
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityExplodeEvent

class GhastGriefListener(private val feature: GameRulesFeature) : Listener {

    @EventHandler
    fun on(event: EntityExplodeEvent) {
        val entity = event.entity
        val ghastGrievingEnabled = feature.plugin.config.getBoolean("modules.${feature.name}.ghast-grieving", true)

        if (entity !is Fireball || entity.shooter !is Ghast || ghastGrievingEnabled) return

        event.blockList().clear()
    }
}
package net.tjalp.nexus.feature.gamerules

import net.tjalp.nexus.feature.Feature
import net.tjalp.nexus.feature.gamerules.listener.CreeperGriefListener
import net.tjalp.nexus.feature.gamerules.listener.CropTramplingListener
import net.tjalp.nexus.feature.gamerules.listener.EndermanGriefListener
import net.tjalp.nexus.feature.gamerules.listener.GhastGriefListener
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.bukkit.event.Listener

object GameRulesFeature : Feature("gamerules") {

    private val listeners = mutableListOf<Listener>()

    override fun enable() {
        super.enable()

        listeners += CreeperGriefListener(this)
        listeners += CropTramplingListener(this)
        listeners += EndermanGriefListener(this)
        listeners += GhastGriefListener(this)

        listeners.forEach { it.register() }
    }

    override fun disable() {
        // run unregister() on all listeners and clear the map
        listeners.forEach { it.unregister() }
        listeners.clear()

        super.disable()
    }
}
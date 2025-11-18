package net.tjalp.nexus.feature.gamerules

import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.NexusServices
import net.tjalp.nexus.feature.gamerules.listener.CreeperGriefListener
import net.tjalp.nexus.feature.gamerules.listener.CropTramplingListener
import net.tjalp.nexus.feature.gamerules.listener.EndermanGriefListener
import net.tjalp.nexus.feature.gamerules.listener.GhastGriefListener
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.bukkit.event.Listener

class GameRulesFeature : Feature {

    override val name: String = "gamerules"

    val plugin: NexusPlugin = NexusServices.get<NexusPlugin>()

    private val listeners = mutableListOf<Listener>()

    override fun enable() {
        listeners += CreeperGriefListener(this)
        listeners += CropTramplingListener(this)
        listeners += EndermanGriefListener(this)
        listeners += GhastGriefListener(this)

        listeners.forEach { it.register() }

//        NexusGameRules.init()
    }

    override fun disable() {
        // run unregister() on all listeners and clear the map
        listeners.forEach { it.unregister() }
        listeners.clear()
    }
}
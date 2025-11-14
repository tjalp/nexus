package net.tjalp.nexus.gamerules

import net.tjalp.nexus.common.Feature
import net.tjalp.nexus.common.NexusServices
import net.tjalp.nexus.common.register
import net.tjalp.nexus.common.unregister
import net.tjalp.nexus.gamerules.listener.CreeperGriefListener
import net.tjalp.nexus.gamerules.listener.CropTramplingListener
import net.tjalp.nexus.gamerules.listener.EndermanGriefListener
import net.tjalp.nexus.gamerules.listener.GhastGriefListener
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

class GameRulesFeature : Feature {

    override val name: String = "gamerules"

    val plugin: JavaPlugin = NexusServices.get<JavaPlugin>()

    private val listeners = mutableListOf<Listener>()

    override fun enable() {
        listeners += CreeperGriefListener(this)
        listeners += CropTramplingListener(this)
        listeners += EndermanGriefListener(this)
        listeners += GhastGriefListener(this)

        listeners.forEach { it.register(plugin) }

//        NexusGameRules.init()
    }

    override fun disable() {
        // run unregister() on all listeners and clear the map
        listeners.forEach { it.unregister() }
        listeners.clear()
    }
}
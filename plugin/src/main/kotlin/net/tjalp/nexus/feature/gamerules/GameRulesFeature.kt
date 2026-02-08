package net.tjalp.nexus.feature.gamerules

import net.tjalp.nexus.Feature
import net.tjalp.nexus.feature.FeatureKeys.GAMERULES
import net.tjalp.nexus.feature.gamerules.listener.CreeperGriefListener
import net.tjalp.nexus.feature.gamerules.listener.CropTramplingListener
import net.tjalp.nexus.feature.gamerules.listener.EndermanGriefListener
import net.tjalp.nexus.feature.gamerules.listener.GhastGriefListener
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.bukkit.event.Listener

class GameRulesFeature : Feature(GAMERULES) {

    private val listeners = mutableListOf<Listener>()

    override fun onEnable() {
        listeners += CreeperGriefListener(this)
        listeners += CropTramplingListener(this)
        listeners += EndermanGriefListener(this)
        listeners += GhastGriefListener(this)

        listeners.forEach { it.register() }
    }

    override fun onDisposed() {
        listeners.forEach { it.unregister() }
    }
}
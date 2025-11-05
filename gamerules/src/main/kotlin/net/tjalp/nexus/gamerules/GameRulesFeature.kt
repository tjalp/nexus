package net.tjalp.nexus.gamerules

import net.tjalp.nexus.common.Feature
import net.tjalp.nexus.common.register
import org.bukkit.plugin.java.JavaPlugin

class GameRulesFeature : Feature {

    override val name: String = "gamerules"

    lateinit var plugin: JavaPlugin; private set

    override fun enable(plugin: JavaPlugin) {
        this.plugin = plugin

        CropTramplingListener(this).register(plugin)

//        NexusGameRules.init()
    }

    override fun disable() {

    }
}
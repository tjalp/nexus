package net.tjalp.nexus.plugin

import net.tjalp.nexus.chat.ChatFeature
import net.tjalp.nexus.common.Feature
import org.bukkit.plugin.java.JavaPlugin

class NexusPlugin : JavaPlugin() {
    private val features = listOf<Feature>(
        ChatFeature()
    )

    override fun onEnable() {
        saveDefaultConfig()
        features.filter { config.getBoolean("modules.${it.name}.enabled", true) }
            .forEach { it.enable(this) }
    }

    override fun onDisable() {
        features.forEach { it.disable() }
    }
}
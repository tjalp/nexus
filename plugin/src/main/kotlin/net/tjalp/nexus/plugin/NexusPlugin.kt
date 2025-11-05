package net.tjalp.nexus.plugin

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.tjalp.nexus.chat.ChatFeature
import net.tjalp.nexus.common.Feature
import net.tjalp.nexus.plugin.command.NexusCommand
import org.bukkit.plugin.java.JavaPlugin

class NexusPlugin : JavaPlugin() {

    val features = listOf<Feature>(
        ChatFeature()
    )

    override fun onEnable() {
        saveDefaultConfig()

        features.filter { config.getBoolean("modules.${it.name}.enabled", true) }
            .forEach { it.enable(this) }

        this.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { commands ->
            commands.registrar().register(NexusCommand.create(this), "Nexus-specific commands")
        }
    }

    override fun onDisable() {
        features.forEach { it.disable() }
    }
}
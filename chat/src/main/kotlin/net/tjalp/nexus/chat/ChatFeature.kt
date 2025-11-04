package net.tjalp.nexus.chat

import net.tjalp.nexus.chat.listener.ChatListener
import net.tjalp.nexus.common.Feature
import org.bukkit.plugin.java.JavaPlugin

/**
 * The Chat feature, responsible for handling chat-related functionality.
 */
class ChatFeature : Feature {

    override val name: String = "chat"

    override fun enable(plugin: JavaPlugin) {
        ChatListener(ChatService()).also {
            plugin.server.pluginManager.registerEvents(it, plugin)
        }
    }

    override fun disable() {

    }
}
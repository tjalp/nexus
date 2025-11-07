package net.tjalp.nexus.chat

import net.tjalp.nexus.chat.listener.ChatListener
import net.tjalp.nexus.common.Feature
import net.tjalp.nexus.common.register
import net.tjalp.nexus.common.unregister
import org.bukkit.plugin.java.JavaPlugin

/**
 * The Chat feature, responsible for handling chat-related functionality.
 */
class ChatFeature : Feature {

    override val name: String = "chat"

    lateinit var plugin: JavaPlugin; private set
    lateinit var chatService: ChatService; private set

    private lateinit var listener: ChatListener

    override fun enable(plugin: JavaPlugin) {
        this.plugin = plugin
        this.chatService = ChatService()

        listener = ChatListener(this).also { it.register(plugin) }
    }

    override fun disable() {
        listener.unregister()
    }
}
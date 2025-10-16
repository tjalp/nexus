package net.tjalp.nexus.chat

import net.tjalp.nexus.chat.listener.ChatListener
import org.bukkit.plugin.java.JavaPlugin

class NexusChat : JavaPlugin() {

    override fun onEnable() {
        ChatListener(ChatService()).also {
            server.pluginManager.registerEvents(it, this)
        }
    }
}
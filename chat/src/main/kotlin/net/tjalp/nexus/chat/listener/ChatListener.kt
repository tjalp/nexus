package net.tjalp.nexus.chat.listener

import io.papermc.paper.event.player.AsyncChatEvent
import net.tjalp.nexus.chat.ChatService
import net.tjalp.nexus.chat.NexusChatRenderer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class ChatListener(private val chatService: ChatService) : Listener {

    @EventHandler
    fun on(event: AsyncChatEvent) {
        event.renderer(NexusChatRenderer.renderer(event.signedMessage()))
    }
}
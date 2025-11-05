package net.tjalp.nexus.chat.listener

import io.papermc.paper.event.player.AsyncChatCommandDecorateEvent
import io.papermc.paper.event.player.AsyncChatDecorateEvent
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.tjalp.nexus.chat.ChatService
import net.tjalp.nexus.chat.NexusChatRenderer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class ChatListener(private val chatService: ChatService) : Listener {

    @EventHandler
    fun on(event: AsyncChatEvent) {
        event.renderer(NexusChatRenderer.renderer(event.signedMessage()))
    }

    @Suppress("UnstableApiUsage")
    @EventHandler
    fun on(event: AsyncChatDecorateEvent) {
        processDecorateEvent(event)
    }

    @Suppress("UnstableApiUsage")
    @EventHandler
    fun on(event: AsyncChatCommandDecorateEvent) {
        processDecorateEvent(event)
    }

    /**
     * Processes a chat decorate event, decorating the message if the player has permission.
     *
     * @param event The chat decorate event to process.
     */
    @Suppress("UnstableApiUsage")
    fun processDecorateEvent(event: AsyncChatDecorateEvent) {
        val canDecorate = event.player()?.hasPermission("nexus.chat.decorate") ?: false

        if (!canDecorate) return

        val plainMessage = PlainTextComponentSerializer.plainText().serialize(event.originalMessage())
        val decorated = chatService.decorate(plainMessage)

        event.result(decorated)
    }
}
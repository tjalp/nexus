package net.tjalp.nexus.feature.chat.listener

import io.papermc.paper.event.player.AsyncChatCommandDecorateEvent
import io.papermc.paper.event.player.AsyncChatDecorateEvent
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.minimessage.MiniMessage.miniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.tjalp.nexus.Constants.MONOCHROME_COLOR
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.chat.ChatFeature
import net.tjalp.nexus.feature.chat.NexusChatRenderer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class ChatListener(private val feature: ChatFeature) : Listener {

    @EventHandler
    fun on(event: AsyncChatEvent) {
        val format = NexusPlugin.configuration.features.chat.format

        event.renderer(NexusChatRenderer.renderer(format, event.signedMessage()))
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
        val decorated = feature.chatService.decorate(plainMessage)

        event.result(decorated)
    }

    @EventHandler
    fun on(event: PlayerJoinEvent) {
        val newMessageString = NexusPlugin.configuration.features.chat.joinMessage

        if (newMessageString.isBlank()) {
            event.joinMessage(null) // If the join message is blank, remove it
            return
        }

        val joinMessage = event.joinMessage() ?: return
        val newJoinMessage = eventMessage(newMessageString, joinMessage, event.player.teamDisplayName())

        event.joinMessage(newJoinMessage)
    }

    @EventHandler
    fun on(event: PlayerQuitEvent) {
        val newMessageString = NexusPlugin.configuration.features.chat.quitMessage

        if (newMessageString.isBlank()) {
            event.quitMessage(null) // If the quit message is blank, remove it
            return
        }

        val quitMessage = event.quitMessage() ?: return
        val newJoinMessage = eventMessage(newMessageString, quitMessage, event.player.teamDisplayName())

        event.quitMessage(newJoinMessage)
    }

    @EventHandler
    fun on(event: PlayerDeathEvent) {
        val newMessageString = NexusPlugin.configuration.features.chat.deathMessage

        if (newMessageString.isBlank()) {
            event.deathMessage(null) // If the death message is blank, remove it
            return
        }

        val deathMessage = event.deathMessage() ?: return
        val coloredDeathMessage: ComponentLike = (deathMessage as? TranslatableComponent)?.let { component ->
            val newArgs = component.arguments().map { arg ->
                arg.asComponent().colorIfAbsent(MONOCHROME_COLOR)
            }
            component.arguments(newArgs)
        } ?: deathMessage

        val newDeathMessage = eventMessage(newMessageString, coloredDeathMessage, event.player.teamDisplayName())

        event.deathMessage(newDeathMessage)
    }

    /**
     * Generates a new event message based on the provided format, original message, and player name.
     *
     * @param messageFormat The format string for the event message.
     * @param original The original message component.
     * @param name The player's name component.
     * @return A new Component representing the formatted event message.
     */
    private fun eventMessage(messageFormat: String, original: ComponentLike, name: ComponentLike): Component {
        return miniMessage().deserialize(
            messageFormat,
            Placeholder.component("message", original.asComponent()),
            Placeholder.component("name", name.asComponent())
        )
    }
}
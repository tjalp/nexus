package net.tjalp.nexus.chat.listener

import io.papermc.paper.event.player.AsyncChatCommandDecorateEvent
import io.papermc.paper.event.player.AsyncChatDecorateEvent
import io.papermc.paper.event.player.AsyncChatEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor.GRAY
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.tjalp.nexus.chat.ChatFeature
import net.tjalp.nexus.chat.ChatKeys
import net.tjalp.nexus.chat.NexusChatRenderer
import net.tjalp.nexus.chat.store.ChatTable
import net.tjalp.nexus.common.NexusServices
import net.tjalp.nexus.common.profile.ProfileId
import net.tjalp.nexus.common.profile.ProfilesService
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.update

class ChatListener(private val feature: ChatFeature) : Listener {

    init {
        CoroutineScope(Dispatchers.Unconfined).launch {
            NexusServices.get<ProfilesService>().updates.collect { event ->
                val attachment = event.new.getAttachment(ChatKeys.CHAT)

                Bukkit.getPlayer(event.id.value)?.sendActionBar {
                    text("Your chat message count is now ${attachment?.messageCount ?: 0}", GRAY)
                }
            }
        }
    }

    @EventHandler
    fun on(event: AsyncChatEvent) {
        val format = feature.plugin.config.getString("modules.${feature.name}.format", "<<name>> <message>")!!

        CoroutineScope(Dispatchers.IO).launch {
            var profile = NexusServices.get<ProfilesService>().get(ProfileId(event.player.uniqueId)) ?: return@launch
            profile = profile.update(additionalStatements = arrayOf({
                ChatTable.update({ ChatTable.profileId eq profile.id.value }) {
                    it[messageCount] = messageCount + 1
                }
            }))
        }

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
}
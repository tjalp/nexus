package net.tjalp.nexus.feature.chat

import io.papermc.paper.chat.ChatRenderer
import net.kyori.adventure.chat.SignedMessage
import net.kyori.adventure.text.Component.*
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY
import net.kyori.adventure.text.format.NamedTextColor.RED
import net.kyori.adventure.text.format.TextDecoration.BOLD
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.permissions.Permissible
import org.bukkit.permissions.Permission

object NexusChatRenderer {

    private val miniMessage = MiniMessage.miniMessage()

    /**
     * Creates a chat renderer. The renderer adds a delete button to messages if the viewer has permission to delete messages.
     *
     * @param format The format of the message.
     * @param signedMessage The signed message.
     * @return A ChatRenderer that adds a delete button to messages.
     */
    fun renderer(
        format: String,
        signedMessage: SignedMessage
    ): ChatRenderer = ChatRenderer { source, sourceDisplayName, message, viewer ->
        val base = miniMessage.deserialize(
            format,
            Placeholder.component("name", sourceDisplayName),
            Placeholder.component("message", message)
        )

        val canDeleteAll = (viewer as? Permissible)?.hasPermission(Permission("nexus.chat.delete_all")) ?: false

        if (viewer != source && !canDeleteAll) return@ChatRenderer base

        val deleteBase = textOfChildren(
            text("\u274C", DARK_GRAY, BOLD),
        )

        val delete = deleteBase
            .hoverEvent(text("Click to delete this message!", RED))
            .clickEvent(ClickEvent.callback { _ -> Bukkit.getServer().deleteMessage(signedMessage) })

        return@ChatRenderer textOfChildren(delete, space(), base)
    }
}
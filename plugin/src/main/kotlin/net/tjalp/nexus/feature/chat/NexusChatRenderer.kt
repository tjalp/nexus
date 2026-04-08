package net.tjalp.nexus.feature.chat

import io.papermc.paper.chat.ChatRenderer
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.chat.SignedMessage
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.server.ServerInfo
import net.tjalp.nexus.util.miniMessage

object NexusChatRenderer {

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
    ): ChatRenderer = ChatRenderer { source, displayName, message, viewer ->
        val base = render(
            format,
            source.teamDisplayName(),
            message,
            viewer,
            serverInfo = NexusPlugin.servers?.serverInfo
        )

        return@ChatRenderer base // temporary because I don't want to handle deleting messages anymore, lol

//        val canDeleteAll = (viewer as? Permissible)?.hasPermission(Permission("nexus.chat.delete_all")) ?: false
//
//        if (viewer != source && !canDeleteAll) return@ChatRenderer base
//
//        val deleteBase = textOfChildren(
//            text("\u274C", DARK_GRAY, BOLD),
//        )
//
//        val delete = deleteBase
//            .hoverEvent(text("Click to delete this message!", RED))
//            .clickEvent(ClickEvent.callback { _ -> Bukkit.getServer().deleteMessage(signedMessage) })
//
//        return@ChatRenderer textOfChildren(delete, space(), base)
    }

    /**
     * Render a chat message. This message cannot be deleted.
     *
     * @param format The format of the message
     * @param sourceDisplayName The display name of the source of the message
     * @param message The message to render
     * @param viewer The viewer of the message
     * @param serverInfo The server info of the source of the message, if available.
     *
     * @return The rendered component.
     */
    fun render(
        format: String,
        sourceDisplayName: Component,
        message: Component,
        viewer: Audience,
        serverInfo: ServerInfo? = null
    ): Component {
        return miniMessage.deserialize(
            format,
            Placeholder.component("name", sourceDisplayName),
            Placeholder.component("message", message),
            Placeholder.parsed("server_name", serverInfo?.name ?: "n/a"),
            Placeholder.parsed("server_id", serverInfo?.id ?: "n/a")
        )
    }
}
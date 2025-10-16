package net.tjalp.nexus.chat

import io.papermc.paper.chat.ChatRenderer
import net.kyori.adventure.chat.SignedMessage
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor.*
import net.kyori.adventure.text.format.TextDecoration.BOLD
import org.bukkit.Bukkit

object NexusChatRenderer {

    fun renderer(
        signedMessage: SignedMessage
    ): ChatRenderer = ChatRenderer { source, sourceDisplayName, message, viewer ->
        val base = Component.textOfChildren(
            sourceDisplayName,
            Component.text(": "),
            message
        )

        if (viewer != source) return@ChatRenderer base

        val deleteBase = Component.textOfChildren(
            Component.text("[", DARK_GRAY),
            Component.text("X", DARK_RED, BOLD),
            Component.text("]", DARK_GRAY)
        )

        val delete = deleteBase
            .hoverEvent(Component.text("Click to delete your message!", RED))
            .clickEvent(ClickEvent.callback { audience -> Bukkit.getServer().deleteMessage(signedMessage) })

        return@ChatRenderer delete
    }
}
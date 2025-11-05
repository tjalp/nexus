package net.tjalp.nexus.chat

import io.papermc.paper.chat.ChatRenderer
import net.kyori.adventure.chat.SignedMessage
import net.kyori.adventure.text.Component.*
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY
import net.kyori.adventure.text.format.NamedTextColor.RED
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration.BOLD
import org.bukkit.Bukkit
import org.bukkit.permissions.Permissible
import org.bukkit.permissions.Permission

object NexusChatRenderer {

    fun renderer(
        signedMessage: SignedMessage
    ): ChatRenderer = ChatRenderer { source, sourceDisplayName, message, viewer ->
        val base = textOfChildren(
            text("<"),
            sourceDisplayName,
            text("> "),
            message
        )

        val canDeleteAll = (viewer as? Permissible)?.hasPermission(Permission("nexus.chat.delete_all")) ?: false

        if (viewer != source && !canDeleteAll) return@ChatRenderer base

        val deleteBase = textOfChildren(
            text("\u274C", TextColor.color(0xa81e1e), BOLD),
        )

        val delete = deleteBase
            .hoverEvent(text("Click to delete this message!", RED))
            .clickEvent(ClickEvent.callback { audience -> Bukkit.getServer().deleteMessage(signedMessage) })

        return@ChatRenderer textOfChildren(delete, space(), base)
    }
}
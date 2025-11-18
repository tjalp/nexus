package net.tjalp.nexus.feature.chat

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage

class ChatService {

    private val miniMessage = MiniMessage.miniMessage()

    fun decorate(message: String): Component {
        return miniMessage.deserialize(message)
    }
}
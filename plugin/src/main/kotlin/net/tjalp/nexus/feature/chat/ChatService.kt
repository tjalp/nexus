package net.tjalp.nexus.feature.chat

import net.kyori.adventure.text.Component
import net.tjalp.nexus.util.miniMessage

class ChatService {

    fun decorate(message: String): Component {
        return miniMessage.deserialize(message)
    }
}
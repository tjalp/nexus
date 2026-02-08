package net.tjalp.nexus.feature.chat

import net.tjalp.nexus.Feature
import net.tjalp.nexus.feature.FeatureKeys.CHAT
import net.tjalp.nexus.feature.chat.listener.ChatListener
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister

/**
 * The Chat feature, responsible for handling chat-related functionality.
 */
class ChatFeature : Feature(CHAT) {

    lateinit var chatService: ChatService; private set

    private lateinit var listener: ChatListener

    override fun onEnable() {
        chatService = ChatService()
        listener = ChatListener(this).also { it.register() }
    }

    override fun onDisposed() {
        listener.unregister()
    }
}
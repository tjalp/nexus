package net.tjalp.nexus.feature.chat

import net.tjalp.nexus.Feature
import net.tjalp.nexus.feature.chat.listener.ChatListener
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister

/**
 * The Chat feature, responsible for handling chat-related functionality.
 */
object ChatFeature : Feature {

    override val name: String = "chat"

    private var _isEnabled: Boolean = false
    override val isEnabled: Boolean
        get() = _isEnabled

    lateinit var chatService: ChatService; private set

    private lateinit var listener: ChatListener

    override fun enable() {
        this._isEnabled = true

        this.chatService = ChatService()

        listener = ChatListener(this).also { it.register() }
    }

    override fun disable() {
        listener.unregister()

        this._isEnabled = false
    }
}
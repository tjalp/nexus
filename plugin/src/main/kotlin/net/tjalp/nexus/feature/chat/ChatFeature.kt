package net.tjalp.nexus.feature.chat

import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.NexusServices
import net.tjalp.nexus.feature.chat.listener.ChatListener
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * The Chat feature, responsible for handling chat-related functionality.
 */
object ChatFeature : Feature {

    override val name: String = "chat"

    private var _isEnabled: Boolean = false
    override val isEnabled: Boolean
        get() = _isEnabled

    val plugin: NexusPlugin; get() = NexusServices.get<NexusPlugin>()
    val database: Database; get() = NexusServices.get<Database>()

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
package net.tjalp.nexus.chat

import net.tjalp.nexus.chat.listener.ChatListener
import net.tjalp.nexus.common.Feature
import net.tjalp.nexus.common.NexusServices
import net.tjalp.nexus.common.profile.ProfileModule
import net.tjalp.nexus.common.register
import net.tjalp.nexus.common.unregister
import org.bukkit.plugin.java.JavaPlugin
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * The Chat feature, responsible for handling chat-related functionality.
 */
class ChatFeature : Feature {

    override val name: String = "chat"
    override val profileModules: Collection<ProfileModule>
        get() = listOf(ChatModule(database))

    val plugin: JavaPlugin = NexusServices.get<JavaPlugin>()
    val database: Database = NexusServices.get<Database>()

    lateinit var chatService: ChatService; private set

    private lateinit var listener: ChatListener

    override fun enable() {
        this.chatService = ChatService()

        listener = ChatListener(this).also { it.register(plugin) }
    }

    override fun disable() {
        listener.unregister()
    }
}
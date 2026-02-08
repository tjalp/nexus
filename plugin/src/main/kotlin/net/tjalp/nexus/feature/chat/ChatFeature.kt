package net.tjalp.nexus.feature.chat

import io.papermc.paper.adventure.PaperAdventure
import net.kyori.adventure.text.TranslatableComponent
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.tjalp.nexus.Constants.MONOCHROME_COLOR
import net.tjalp.nexus.Constants.PRIMARY_COLOR
import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.FeatureKeys.CHAT
import net.tjalp.nexus.feature.chat.listener.ChatListener
import net.tjalp.nexus.util.PacketAction
import net.tjalp.nexus.util.PacketManager
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.bukkit.entity.Player
import org.spongepowered.configurate.reactive.Disposable

/**
 * The Chat feature, responsible for handling chat-related functionality.
 */
class ChatFeature : Feature(CHAT) {

    lateinit var chatService: ChatService; private set

    private lateinit var listener: ChatListener
    private lateinit var packetListener: Disposable

    override fun onEnable() {
        chatService = ChatService()
        listener = ChatListener(this).also { it.register() }
        packetListener = PacketManager.addPacketListener(ClientboundSystemChatPacket::class, ::interceptChatPacket)
    }

    override fun onDisposed() {
        listener.unregister()
        packetListener.dispose()
    }

    private fun interceptChatPacket(packet: ClientboundSystemChatPacket, player: Player?): PacketAction {
        if (!NexusPlugin.configuration.features.chat.modifySystemChatMessageStyle) return PacketAction.Continue

        val component = PaperAdventure.asAdventure(packet.content)

        if (component !is TranslatableComponent) return PacketAction.Continue

        val newArgs = component.arguments().map { arg -> arg.asComponent().colorIfAbsent(MONOCHROME_COLOR) }
        val newComponent = component.colorIfAbsent(PRIMARY_COLOR).arguments(newArgs)
        val newPacket = ClientboundSystemChatPacket(PaperAdventure.asVanilla(newComponent), packet.overlay())

        return PacketAction.Replace(newPacket)
    }
}
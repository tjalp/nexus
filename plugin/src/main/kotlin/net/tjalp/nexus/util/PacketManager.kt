package net.tjalp.nexus.util

import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.papermc.paper.network.ChannelInitializeListenerHolder
import net.kyori.adventure.key.Key
import net.minecraft.network.protocol.Packet
import org.apache.commons.collections4.MultiValuedMap
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.spongepowered.configurate.reactive.Disposable
import kotlin.reflect.KClass

typealias PacketListener<T> = (T, Player?) -> PacketAction

/**
 * Action to take on a packet
 */
sealed class PacketAction {
    data object Cancel : PacketAction()
    data object Continue : PacketAction()
    data class Replace<T : Packet<*>>(val packet: T) : PacketAction()
}

/**
 * Packet manager for handling incoming and outgoing packets
 */
object PacketManager {

    private val listeners: MultiValuedMap<Any, Any> = ArrayListValuedHashMap()

    init {
        // Add before Minecraft's packet handler
        ChannelInitializeListenerHolder.addListener(Key.key("nexus")) { channel ->
            channel.pipeline().addBefore("packet_handler", "nexus_packet_interceptor", PacketInterceptor())
        }

        LoginListener().register()
    }

    /**
     * Add a packet listener
     *
     * @param packet The packet class to add
     * @param callback The callback
     *
     * @return A disposable to remove the listener
     */
    fun <T : Packet<*>> addPacketListener(packet: KClass<T>, callback: PacketListener<T>): Disposable {
        listeners.put(packet, callback)

        return Disposable {
            listeners.removeMapping(packet, callback)
        }
    }

    private data class InterceptResponse(
        val cancel: Boolean,
        val packet: Packet<*>
    )

    @Suppress("UNCHECKED_CAST")
    private class PacketInterceptor : ChannelDuplexHandler() {
        // will be updated by the player join event
        // There might be an earlier event, but you'll have to look into that
        // PlayerLoginEvent gave NPE on connection.connection
        var player: Player? = null

        private fun intercept(msg: Packet<*>): InterceptResponse {
            var cancel = false
            var packet = msg

            for (listener in listeners[msg::class]) {
                listener as PacketListener<Packet<*>>

                when (val result = listener(msg, player)) {
                    PacketAction.Cancel -> {
                        cancel = true
                    }
                    is PacketAction.Replace<*> -> {
                        packet = result.packet
                    }
                    PacketAction.Continue -> continue
                }
            }

            return InterceptResponse(cancel, packet)
        }

        override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
            if (msg !is Packet<*>) return super.channelRead(ctx, msg)

            val (cancel, packet) = intercept(msg)

            if (!cancel) super.channelRead(ctx, packet)
        }

        override fun write(ctx: ChannelHandlerContext?, msg: Any?, promise: ChannelPromise?) {
            if (msg !is Packet<*>) return super.write(ctx, msg, promise)

            val (cancel, packet) = intercept(msg)

            if (!cancel) super.write(ctx, packet, promise)
        }
    }

    private class LoginListener : Listener {

        @EventHandler(priority = EventPriority.LOWEST)
        fun onJoin(event: PlayerJoinEvent) {
            val player = event.player
            val channel = (player as CraftPlayer).handle.connection.connection.channel

            // inject the player into the packet interceptor
            (channel.pipeline().get("nexus_packet_interceptor") as PacketInterceptor?)?.player = player
        }
    }
}
package net.tjalp.nexus.feature.servers

import io.papermc.paper.event.player.AsyncChatEvent
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component.*
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.chat.ChatMessageSignal
import net.tjalp.nexus.redis.RedisController
import net.tjalp.nexus.redis.Signals
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.spongepowered.configurate.reactive.Disposable

class GlobalChatHandler(
    private val feature: ServersFeature,
    private val redis: RedisController
) : Disposable, Listener {

    val scheduler = feature.scheduler.fork("global_chat")

    init {
        register()

        scheduler.launch {
            redis.subscribe(Signals.CHAT_MESSAGE).collect(::receiveMessage)
        }
    }

    fun receiveMessage(signal: ChatMessageSignal) {
        val message = try {
            GsonComponentSerializer.gson().deserialize(signal.message)
        } catch (e: Exception) {
            NexusPlugin.logger.warning("Error while parsing message: ${e.message}")
            e.printStackTrace()

            text(signal.message)
        }

        NexusPlugin.server.broadcast(textOfChildren(
            text(signal.origin.name),
            space(),
            text(signal.playerName),
            space(),
            message
        ))
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun on(event: AsyncChatEvent) {
        scheduler.launch {
            val signal = ChatMessageSignal(
                origin = feature.serverInfo,
                playerId = event.player.uniqueId,
                playerName = event.player.name,
                message = GsonComponentSerializer.gson().serialize(event.message())
            )

            redis.publish(Signals.CHAT_MESSAGE, signal)
        }
    }

    override fun dispose() {
        unregister()
        scheduler.dispose()
    }
}
package net.tjalp.nexus.feature.servers

import com.destroystokyo.paper.event.server.PaperServerListPingEvent
import io.papermc.paper.event.player.AsyncChatEvent
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.chat.ChatMessageSignal
import net.tjalp.nexus.feature.chat.NexusChatRenderer
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
        if (signal.origin.id == feature.serverInfo.id) return

        val message = try {
            GsonComponentSerializer.gson().deserialize(signal.message)
        } catch (e: Exception) {
            NexusPlugin.logger.warning("Error while parsing message: ${e.message}")
            e.printStackTrace()

            text(signal.message)
        }

        NexusPlugin.server.forEachAudience { audience ->
            val message = NexusChatRenderer.render(
                NexusPlugin.configuration.features.chat.format,
                text(signal.playerName)
                    .clickEvent(ClickEvent.suggestCommand("/tell ${signal.playerName} ")),
                message,
                audience,
                serverInfo = signal.origin
            )

            audience.sendMessage(message)
        }
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

    @EventHandler
    fun on(event: PaperServerListPingEvent) {
        val players = runBlocking { feature.playerRegistry?.getOnlinePlayers() }

        players?.let {
            event.numPlayers = it.size
            event.listedPlayers.clear()
            event.listedPlayers += it.map { player ->
                PaperServerListPingEvent.ListedPlayerInfo(player.username, player.id)
            }
        }
    }

    override fun dispose() {
        unregister()
        scheduler.dispose()
    }
}
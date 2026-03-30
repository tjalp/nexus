package net.tjalp.nexus.feature.servers

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.papermc.paper.event.player.AsyncChatEvent
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.kyori.adventure.text.Component.*
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.chat.ChatMessageSignal
import net.tjalp.nexus.redis.Signals
import net.tjalp.nexus.server.P2PServerRegistry
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.spongepowered.configurate.reactive.Disposable

class GlobalChatHandler(
    private val feature: ServersFeature
) : Disposable, Listener, P2PApiServer.GlobalChatHandlerInterface {

    val scheduler = feature.scheduler.fork("global_chat")
    private val useP2PMode: Boolean
    private var httpClient: HttpClient? = null

    init {
        register()

        // Determine mode based on feature's registry type
        useP2PMode = feature.serverRegistry is P2PServerRegistry

        if (useP2PMode) {
            // Initialize HTTP client for P2P mode
            httpClient = HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        prettyPrint = true
                    })
                }
                engine {
                    requestTimeout = 5000
                }
            }

            // Register with P2P API server to receive chat messages
            (feature as? ServersFeature)?.let { sf ->
                sf.p2pApiServer?.setGlobalChatHandler(this)
            }
        } else {
            // Subscribe to Redis messages
            scheduler.launch {
                NexusPlugin.redis.subscribe(Signals.CHAT_MESSAGE).collect(::receiveMessage)
            }
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

    /**
     * Receive P2P chat message from another server
     */
    override suspend fun receiveP2PMessage(message: P2PApiServer.ChatMessage) {
        val component = try {
            GsonComponentSerializer.gson().deserialize(message.message)
        } catch (e: Exception) {
            NexusPlugin.logger.warning("Error while parsing P2P message: ${e.message}")
            text(message.message)
        }

        NexusPlugin.server.broadcast(textOfChildren(
            text(message.senderServer),
            space(),
            text(message.senderName),
            space(),
            component
        ))
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun on(event: AsyncChatEvent) {
        scheduler.launch {
            if (useP2PMode) {
                broadcastP2PMessage(event)
            } else {
                broadcastRedisMessage(event)
            }
        }
    }

    private suspend fun broadcastRedisMessage(event: AsyncChatEvent) {
        val signal = ChatMessageSignal(
            origin = feature.serverInfo,
            playerId = event.player.uniqueId,
            playerName = event.player.name,
            message = GsonComponentSerializer.gson().serialize(event.message())
        )

        NexusPlugin.redis.publish(Signals.CHAT_MESSAGE, signal)
    }

    private suspend fun broadcastP2PMessage(event: AsyncChatEvent) {
        val message = P2PApiServer.ChatMessage(
            senderId = event.player.uniqueId.toString(),
            senderName = event.player.name,
            senderServer = feature.serverInfo.name,
            message = GsonComponentSerializer.gson().serialize(event.message()),
            timestamp = System.currentTimeMillis()
        )

        // Broadcast to all other servers
        val servers = feature.serverRegistry.getOnlineServers()

        for (server in servers) {
            if (server.id == feature.serverInfo.id) continue

            try {
                httpClient?.post("http://${server.host}:8080/chat-message") {
                    contentType(ContentType.Application.Json)
                    setBody(message)
                }
            } catch (e: Exception) {
                // Failed to send to this server, continue
                NexusPlugin.logger.warning("Failed to send chat message to ${server.name}: ${e.message}")
            }
        }
    }

    override fun dispose() {
        unregister()
        httpClient?.close()
        scheduler.dispose()
    }
}

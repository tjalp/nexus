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
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.spongepowered.configurate.reactive.Disposable

/**
 * Handles global chat broadcasting across all servers in the P2P network.
 * Messages are sent to all servers via HTTP POST to /chat-message endpoint.
 */
class GlobalChatHandler(
    private val feature: ServersFeature
) : Disposable, Listener, P2PApiServer.GlobalChatHandlerInterface {

    val scheduler = feature.scheduler.fork("global_chat")
    private val httpClient: HttpClient

    init {
        register()

        // Initialize HTTP client for P2P communication
        httpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = false
                })
            }
            engine {
                requestTimeout = 5000
            }
        }

        // Register with P2P API server to receive chat messages
        feature.p2pApiServer?.setGlobalChatHandler(this)
    }

    /**
     * Receive chat message from another server via P2P
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
            broadcastMessage(event)
        }
    }

    /**
     * Broadcast chat message to all other servers in the P2P network
     */
    private suspend fun broadcastMessage(event: AsyncChatEvent) {
        val message = P2PApiServer.ChatMessage(
            senderId = event.player.uniqueId.toString(),
            senderName = event.player.name,
            senderServer = feature.serverInfo.name,
            message = GsonComponentSerializer.gson().serialize(event.message()),
            timestamp = System.currentTimeMillis()
        )

        // Broadcast to all other servers
        val servers = feature.serverRegistry.getOnlineServers()
        val apiPort = NexusPlugin.configuration.features.servers.p2p.apiPort

        for (server in servers) {
            if (server.id == feature.serverInfo.id) continue

            try {
                httpClient.post("http://${server.host}:${apiPort}/chat-message") {
                    contentType(ContentType.Application.Json)
                    setBody(message)
                }
            } catch (e: Exception) {
                // Failed to send to this server, log and continue
                NexusPlugin.logger.warning("Failed to send chat message to ${server.name}: ${e.message}")
            }
        }
    }

    override fun dispose() {
        unregister()
        httpClient.close()
        scheduler.dispose()
    }
}

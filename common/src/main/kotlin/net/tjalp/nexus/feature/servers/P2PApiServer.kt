package net.tjalp.nexus.feature.servers

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.tjalp.nexus.player.P2PPlayerRegistry
import net.tjalp.nexus.player.PlayerInfo
import net.tjalp.nexus.server.P2PServerRegistry
import net.tjalp.nexus.server.ServerInfo

/**
 * P2P API Server for handling server-to-server communication.
 *
 * This server provides endpoints for:
 * - Health checks
 * - Server information
 * - Player queries
 * - Player event notifications
 * - Global chat messages
 * - Profile update notifications
 *
 * @param serverInfo The local server information
 * @param serverRegistry The P2P server registry
 * @param playerRegistry The P2P player registry
 * @param port The HTTP port to listen on
 */
class P2PApiServer(
    private val serverInfo: ServerInfo,
    private val serverRegistry: P2PServerRegistry,
    private val playerRegistry: P2PPlayerRegistry,
    private val port: Int = 8080
) {

    private var server: EmbeddedServer<*, *>? = null
    private var localPlayerCount: Int = 0
    private var globalChatHandler: GlobalChatHandlerInterface? = null

    fun start() {
        server = embeddedServer(CIO, port = port) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = false
                })
            }

            routing {
                // Health check endpoint
                get("/health") {
                    call.respond(
                        HttpStatusCode.OK,
                        HealthResponse(
                            healthy = true,
                            playerCount = localPlayerCount,
                            maxPlayers = serverInfo.maxPlayers
                        )
                    )
                }

                // Server information endpoint
                get("/server-info") {
                    call.respond(HttpStatusCode.OK, serverInfo)
                }

                // Get all online servers
                get("/servers") {
                    val servers = serverRegistry.getOnlineServers()
                    call.respond(HttpStatusCode.OK, servers)
                }

                // Get players on this server
                get("/players") {
                    val players = playerRegistry.getPlayersByServer(serverInfo.id)
                    call.respond(HttpStatusCode.OK, players)
                }

                // Get specific player information
                get("/player/{id}") {
                    val playerId = call.parameters["id"]
                    if (playerId == null) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing player ID"))
                        return@get
                    }

                    try {
                        val uuid = java.util.UUID.fromString(playerId)
                        val player = playerRegistry.getPlayer(uuid)

                        if (player != null) {
                            call.respond(HttpStatusCode.OK, player)
                        } else {
                            call.respond(HttpStatusCode.NotFound, ErrorResponse("Player not found"))
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid UUID format"))
                    }
                }

                // Receive player event from another server
                post("/player-event") {
                    try {
                        val event = call.receive<P2PPlayerRegistry.PlayerEventMessage>()
                        playerRegistry.handlePlayerEvent(event)
                        call.respond(HttpStatusCode.OK, SuccessResponse("Event received"))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid event: ${e.message}"))
                    }
                }

                // Receive global chat message
                post("/chat-message") {
                    try {
                        val message = call.receive<ChatMessage>()

                        val handler = globalChatHandler
                        if (handler != null) {
                            launch {
                                handler.receiveP2PMessage(message)
                            }
                            call.respond(HttpStatusCode.OK, SuccessResponse("Message received"))
                        } else {
                            call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Chat handler not available"))
                        }
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid message: ${e.message}"))
                    }
                }

                // Get server statistics
                get("/stats") {
                    val totalPlayers = serverRegistry.getOnlineServers()
                        .sumOf { server ->
                            try {
                                playerRegistry.getPlayerCountOnServer(server.id).toInt()
                            } catch (e: Exception) {
                                0
                            }
                        }

                    call.respond(
                        HttpStatusCode.OK,
                        ServerStats(
                            totalServers = serverRegistry.getOnlineServers().size,
                            totalPlayers = totalPlayers,
                            localServerPlayers = localPlayerCount
                        )
                    )
                }

                // Profile update notification
                post("/profile-update") {
                    try {
                        val notification = call.receive<ProfileUpdateNotification>()
                        // Will be handled by profile service to invalidate cache
                        call.respond(HttpStatusCode.OK, SuccessResponse("Notification received"))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid notification: ${e.message}"))
                    }
                }

                // Server shutdown notification
                post("/server-offline") {
                    try {
                        val notification = call.receive<ServerOfflineNotification>()
                        launch {
                            serverRegistry.handleServerOffline(notification.serverId)
                        }
                        call.respond(HttpStatusCode.OK, SuccessResponse("Server offline notification received"))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid notification: ${e.message}"))
                    }
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 5000)
    }

    fun updatePlayerCount(count: Int) {
        localPlayerCount = count
    }

    fun setGlobalChatHandler(handler: GlobalChatHandlerInterface) {
        globalChatHandler = handler
    }

    @Serializable
    data class ErrorResponse(
        val error: String
    )

    @Serializable
    data class SuccessResponse(
        val message: String
    )

    @Serializable
    data class HealthResponse(
        val healthy: Boolean,
        val playerCount: Int,
        val maxPlayers: Int
    )

    @Serializable
    data class ServerStats(
        val totalServers: Int,
        val totalPlayers: Int,
        val localServerPlayers: Int
    )

    @Serializable
    data class ChatMessage(
        val senderId: String,
        val senderName: String,
        val senderServer: String,
        val message: String,
        val timestamp: Long
    )

    @Serializable
    data class ProfileUpdateNotification(
        val playerId: String
    )

    @Serializable
    data class ServerOfflineNotification(
        val serverId: String
    )

    /**
     * Interface for chat handler to avoid circular dependencies
     */
    interface GlobalChatHandlerInterface {
        suspend fun receiveP2PMessage(message: ChatMessage)
    }
}

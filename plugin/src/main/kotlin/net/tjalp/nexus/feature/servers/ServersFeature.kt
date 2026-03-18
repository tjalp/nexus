package net.tjalp.nexus.feature.servers

import io.papermc.paper.connection.PlayerLoginConnection
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent
import kotlinx.coroutines.*
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.format.NamedTextColor.RED
import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.FeatureKeys.SERVERS
import net.tjalp.nexus.player.PlayerRegistry
import net.tjalp.nexus.player.PlayerStatus
import net.tjalp.nexus.player.RedisPlayerRegistry
import net.tjalp.nexus.redis.RedisConfig
import net.tjalp.nexus.server.RedisServerRegistry
import net.tjalp.nexus.server.ServerInfo
import net.tjalp.nexus.server.ServerRegistry
import net.tjalp.nexus.server.ServerType
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.*
import kotlin.time.Duration.Companion.seconds

/**
 * Feature for managing multiserver infrastructure with Redis
 */
class ServersFeature : Feature(SERVERS), Listener {

    lateinit var serverRegistry: ServerRegistry
        private set

    lateinit var playerRegistry: PlayerRegistry
        private set

    lateinit var serverInfo: ServerInfo
        private set

    private var heartbeatJob: Job? = null
    private var heartbeatTtl: Long = 60

    var globalChat: GlobalChatHandler? = null
        private set

    override fun onEnable() {
        val config = NexusPlugin.configuration.features.servers

        // Parse server type from config
        val serverType = try {
            ServerType.valueOf(config.serverType.uppercase())
        } catch (_: IllegalArgumentException) {
            NexusPlugin.logger.warning("Invalid server type '${config.serverType}', defaulting to OTHER")
            ServerType.OTHER
        }

        serverInfo = ServerInfo(
            id = config.serverId,
            name = config.serverName,
            type = serverType,
            host = config.host,
            port = config.port,
            maxPlayers = config.maxPlayers
        )

        heartbeatTtl = config.heartbeatTimeoutSeconds

        serverRegistry = RedisServerRegistry(NexusPlugin.redis, scheduler)
        playerRegistry = RedisPlayerRegistry(NexusPlugin.redis, scheduler)

        // Configure Redis for keyspace notifications (needed for crash detection)
        scheduler.launch {
            try {
                NexusPlugin.logger.info("Configuring Redis for Nexus network...")
                RedisConfig.enableKeyspaceNotifications(NexusPlugin.redis)
                RedisConfig.validateConfiguration(NexusPlugin.redis)
            } catch (e: Exception) {
                NexusPlugin.logger.warning("Failed to configure Redis: ${e.message}")
            }
        }

        // Register this server as online
        scheduler.launch {
            try {
                serverRegistry.registerServer(serverInfo)
                NexusPlugin.logger.info("Registered server '${serverInfo.name}' (${serverInfo.id}) as online")
            } catch (e: Exception) {
                NexusPlugin.logger.severe("Failed to register server: ${e.message}")
                e.printStackTrace()
            }
        }

        startHeartbeat(config.heartbeatIntervalSeconds, config.heartbeatTimeoutSeconds)

        this.register()

        // Subscribe to server events for logging
        scheduler.launch {
            serverRegistry.serverOnlineEvents.collect { event ->
                if (event.server.id != serverInfo.id) {
                    NexusPlugin.logger.info("Server '${event.server.name}' (${event.server.id}) came online")
                }
            }
        }

        scheduler.launch {
            serverRegistry.serverOfflineEvents.collect { event ->
                if (event.serverId != serverInfo.id) {
                    NexusPlugin.logger.info("Server '${event.serverId}' went offline")

                    // Clean up players from offline server
                    try {
                        playerRegistry.cleanupServerPlayers(event.serverId)
                    } catch (e: Exception) {
                        NexusPlugin.logger.warning("Failed to cleanup players from server ${event.serverId}: ${e.message}")
                    }
                }
            }
        }

        scheduler.launch {
            playerRegistry.playerOfflineEvents.collect { event ->
                NexusPlugin.logger.info("Player with id ${event.playerId} went offline from server ${event.lastServerId}")
            }
        }

        scheduler.launch {
            playerRegistry.playerOnlineEvents.collect { event ->
                NexusPlugin.logger.info("Player ${event.player.username} came online on server ${event.player.serverId}")
            }
        }

        scheduler.launch {
            playerRegistry.playerChangeServerEvents.collect { event ->
                NexusPlugin.logger.info("Player ${event.playerId} transferred from server ${event.fromServerId} to ${event.toServerId}")
            }
        }

        globalChat = GlobalChatHandler(this)
    }

    override fun onDisposed() {
        globalChat?.dispose()
        this.unregister()

        heartbeatJob?.cancel()

        // Clean up players and unregister this server
        runBlocking {
            try {
                // Clean up all players on this server first
                playerRegistry.cleanupServerPlayers(serverInfo.id)

                serverRegistry.unregisterServer(serverInfo.id)
                NexusPlugin.logger.info("Unregistered server '${serverInfo.name}' (${serverInfo.id})")
            } catch (e: Exception) {
                NexusPlugin.logger.severe("Failed to unregister server: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * Start sending heartbeats to Redis
     */
    private fun startHeartbeat(intervalSeconds: Long, ttl: Long) {
        heartbeatJob = scheduler.launch(Dispatchers.Default) {
            while (true) {
                delay(intervalSeconds.seconds)
                try {
                    val playerCount = NexusPlugin.server.onlinePlayers.size
                    serverRegistry.updateHeartbeat(serverInfo.id, playerCount, ttl)

                    // Refresh TTLs only for players that are genuinely online right now
                    val onlineIds = NexusPlugin.server.onlinePlayers.map { it.uniqueId }.toSet()
                    playerRegistry.refreshServerPlayersTtl(serverInfo.id, ttl, onlineIds)
                } catch (e: Exception) {
                    NexusPlugin.logger.warning("Failed to send heartbeat: ${e.message}")
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        scheduler.launch {
            try {
                playerRegistry.registerPlayer(
                    playerId = event.player.uniqueId,
                    username = event.player.name,
                    serverId = serverInfo.id,
                    ttl = heartbeatTtl
                )
            } catch (e: Exception) {
                NexusPlugin.logger.warning("Failed to track player join: ${e.message}")
            }
        }
    }

    @Suppress("UnstableApiUsage")
    @EventHandler(priority = EventPriority.LOW)
    fun onConnectionValidate(event: PlayerConnectionValidateLoginEvent) {
        val conn = event.connection

        if (conn !is PlayerLoginConnection) return

        // disallow if already online on another server in the network
        runBlocking {
            val id = conn.authenticatedProfile?.id

            if (id == null) {
                event.kickMessage(text("Failed to verify your profile, please try again later", RED))
                return@runBlocking
            }

            val existingPlayer = playerRegistry.getPlayer(id)

            if (existingPlayer != null
                && existingPlayer.status != PlayerStatus.TRANSFERRING
                && existingPlayer.serverId != null
                && existingPlayer.serverId != serverInfo.id
            ) {
                event.kickMessage(translatable("multiserver.kick.already_online", RED))
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        scheduler.launch {
            try {
                // Check if the player is transferring – if so, don't remove them.
                // The receiving server will call registerPlayer() to update their info,
                // or the TTL will expire if the transfer fails.
                val player = playerRegistry.getPlayer(event.player.uniqueId)
                if (player != null && player.status == PlayerStatus.TRANSFERRING) {
                    return@launch
                }

                playerRegistry.removePlayer(event.player.uniqueId)
            } catch (e: Exception) {
                NexusPlugin.logger.warning("Failed to track player quit: ${e.message}")
            }
        }
    }

    /**
     * Transfer a player to another server.
     * Marks the player as TRANSFERRING so they remain in the registry
     * during the transition. If the transfer fails, the entry will
     * auto-expire based on the TTL.
     *
     * @param player The player to transfer
     * @param serverId The ID of the target server
     * @return True if transfer was initiated, false if target server not found or offline
     */
    suspend fun transferPlayer(player: Player, serverId: String): Boolean {
        val targetServer = serverRegistry.getServer(serverId)

        if (targetServer == null || !targetServer.online) {
            return false
        }

        // Mark the player as transferring BEFORE initiating the transfer.
        // This prevents onPlayerQuit from fully removing them from the registry.
        playerRegistry.markTransferring(player.uniqueId, heartbeatTtl)

        player.transfer(targetServer.host, targetServer.port)

        return true
    }

    /**
     * Get all online servers
     *
     * @return Collection of all online servers
     */
    suspend fun getOnlineServers(): Collection<ServerInfo> {
        return serverRegistry.getOnlineServers()
    }

    /**
     * Get the server a player is currently on
     *
     * @param playerId The UUID of the player
     * @return The server info, or null if player is not online or server not found
     */
    suspend fun getPlayerServer(playerId: UUID): ServerInfo? {
        val player = playerRegistry.getPlayer(playerId) ?: return null
        val serverId = player.serverId ?: return null
        return serverRegistry.getServer(serverId)
    }
}


package net.tjalp.nexus.feature.servers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.FeatureKeys.SERVERS
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

    private lateinit var serverInfo: ServerInfo
    private var heartbeatJob: Job? = null

    override fun onEnable() {
        val config = NexusPlugin.configuration.features.servers

        // Parse server type from config
        val serverType = try {
            ServerType.valueOf(config.serverType.uppercase())
        } catch (e: IllegalArgumentException) {
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

        serverRegistry = RedisServerRegistry(NexusPlugin.redis, scheduler)

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

        startHeartbeat(config.heartbeatIntervalSeconds)

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
                }
            }
        }
    }

    override fun onDisposed() {
        this.unregister()

        heartbeatJob?.cancel()

        // Unregister this server
        scheduler.launch {
            try {
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
    private fun startHeartbeat(intervalSeconds: Long) {
        heartbeatJob = scheduler.launch(Dispatchers.Default) {
            while (true) {
                delay(intervalSeconds.seconds)
                try {
                    val playerCount = NexusPlugin.server.onlinePlayers.size
                    serverRegistry.updateHeartbeat(serverInfo.id, playerCount)
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
                serverRegistry.setPlayerServer(event.player.uniqueId, serverInfo.id)
            } catch (e: Exception) {
                NexusPlugin.logger.warning("Failed to track player join: ${e.message}")
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        scheduler.launch {
            try {
                serverRegistry.setPlayerServer(event.player.uniqueId, null)
            } catch (e: Exception) {
                NexusPlugin.logger.warning("Failed to track player quit: ${e.message}")
            }
        }
    }

    /**
     * Transfer a player to another server
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
        val serverId = serverRegistry.getPlayerServer(playerId) ?: return null
        return serverRegistry.getServer(serverId)
    }
}



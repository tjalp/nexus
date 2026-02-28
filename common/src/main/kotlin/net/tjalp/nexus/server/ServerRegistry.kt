package net.tjalp.nexus.server

import kotlinx.coroutines.flow.Flow
import java.util.*

/**
 * Registry for tracking servers in the network
 */
interface ServerRegistry {

    /**
     * Get information about a server by its ID
     *
     * @param serverId The unique identifier of the server
     * @return The server info, or null if not found
     */
    suspend fun getServer(serverId: String): ServerInfo?

    /**
     * Get all online servers
     *
     * @return Collection of all online servers
     */
    suspend fun getOnlineServers(): Collection<ServerInfo>

    /**
     * Get servers by type
     *
     * @param type The type of servers to retrieve
     * @return Collection of servers matching the type
     */
    suspend fun getServersByType(type: ServerType): Collection<ServerInfo>

    /**
     * Register a server as online
     *
     * @param server The server information to register
     */
    suspend fun registerServer(server: ServerInfo)

    /**
     * Unregister a server (mark as offline)
     *
     * @param serverId The ID of the server to unregister
     */
    suspend fun unregisterServer(serverId: String)

    /**
     * Update the heartbeat for a server
     *
     * @param serverId The ID of the server
     * @param playerCount Current number of players
     */
    suspend fun updateHeartbeat(serverId: String, playerCount: Int)

    /**
     * Get the server ID where a player is currently located
     *
     * @param playerId The UUID of the player
     * @return The server ID, or null if player is not online
     */
    suspend fun getPlayerServer(playerId: UUID): String?

    /**
     * Set the server where a player is located
     *
     * @param playerId The UUID of the player
     * @param serverId The ID of the server, or null to remove the player
     */
    suspend fun setPlayerServer(playerId: UUID, serverId: String?)

    /**
     * Get all players on a specific server
     *
     * @param serverId The ID of the server
     * @return Collection of player UUIDs
     */
    suspend fun getPlayersOnServer(serverId: String): Collection<UUID>

    /**
     * Flow of server online events
     */
    val serverOnlineEvents: Flow<ServerOnlineEvent>

    /**
     * Flow of server offline events
     */
    val serverOfflineEvents: Flow<ServerOfflineEvent>

    /**
     * Flow of player join server events
     */
    val playerJoinEvents: Flow<PlayerJoinServerEvent>

    /**
     * Flow of player leave server events
     */
    val playerLeaveEvents: Flow<PlayerLeaveServerEvent>
}


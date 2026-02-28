package net.tjalp.nexus.server

import kotlinx.serialization.Serializable

/**
 * Represents the type of Minecraft server
 */
@Serializable
enum class ServerType {
    CREATIVE,
    SURVIVAL,
    MINIGAMES,
    LOBBY,
    OTHER
}

/**
 * Information about a Minecraft server in the network
 *
 * @property id Unique identifier for this server
 * @property name Display name of the server
 * @property type The type of server (creative, survival, etc.)
 * @property host Hostname or IP address
 * @property port Server port
 * @property maxPlayers Maximum number of players allowed
 * @property online Whether the server is currently online
 */
@Serializable
data class ServerInfo(
    val id: String,
    val name: String,
    val type: ServerType,
    val host: String,
    val port: Int,
    val maxPlayers: Int = -1,
    val online: Boolean = false
)

/**
 * Event indicating a server has come online
 *
 * @property server The server that came online
 */
@Serializable
data class ServerOnlineEvent(
    val server: ServerInfo
)

/**
 * Event indicating a server has gone offline
 *
 * @property serverId The ID of the server that went offline
 */
@Serializable
data class ServerOfflineEvent(
    val serverId: String
)

/**
 * Heartbeat signal sent by servers to indicate they're still alive
 *
 * @property serverId The ID of the server sending the heartbeat
 * @property playerCount Current number of players on the server
 */
@Serializable
data class ServerHeartbeat(
    val serverId: String,
    val playerCount: Int
)

/**
 * Event indicating a player joined a server
 *
 * @property playerId The UUID of the player as a string
 * @property serverId The ID of the server the player joined
 */
@Serializable
data class PlayerJoinServerEvent(
    val playerId: String,
    val serverId: String
)

/**
 * Event indicating a player left a server
 *
 * @property playerId The UUID of the player as a string
 * @property serverId The ID of the server the player left
 */
@Serializable
data class PlayerLeaveServerEvent(
    val playerId: String,
    val serverId: String
)


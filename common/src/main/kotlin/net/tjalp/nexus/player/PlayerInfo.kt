package net.tjalp.nexus.player

import kotlinx.serialization.Serializable
import net.tjalp.nexus.serializer.UUIDAsStringSerializer
import java.util.*
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The status of a player in the network
 */
@Serializable
enum class PlayerStatus {
    /** Player is online and connected to a server */
    ONLINE,
    /** Player is transferring between servers */
    TRANSFERRING
}

/**
 * Information about a player in the network
 *
 * @property id The unique identifier (UUID) of the player
 * @property username The current username of the player
 * @property serverId The ID of the server the player is currently on, or null if offline
 * @property status The current status of the player
 * @property lastSeen The timestamp when the player was last seen online
 */
@OptIn(ExperimentalTime::class)
@Serializable
data class PlayerInfo(
    @Serializable(with = UUIDAsStringSerializer::class)
    val id: UUID,
    val username: String,
    val serverId: String? = null,
    val status: PlayerStatus = PlayerStatus.ONLINE,
    val lastSeen: Instant
)

/**
 * Event indicating a player came online
 *
 * @property player The player that came online
 */
@Serializable
data class PlayerOnlineEvent(
    val player: PlayerInfo
)

/**
 * Event indicating a player went offline
 *
 * @property playerId The UUID of the player as a string
 * @property lastServerId The ID of the last server the player was on
 */
@Serializable
data class PlayerOfflineEvent(
    val playerId: String,
    val lastServerId: String?
)

/**
 * Event indicating a player changed servers
 *
 * @property playerId The UUID of the player as a string
 * @property fromServerId The ID of the server the player left (null if joining network)
 * @property toServerId The ID of the server the player joined (null if leaving network)
 */
@Serializable
data class PlayerChangeServerEvent(
    val playerId: String,
    val fromServerId: String?,
    val toServerId: String?
)


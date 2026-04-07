package net.tjalp.nexus.transfer

import kotlinx.serialization.Serializable

/**
 * Represents a signed transfer token embedded as a connection cookie when a player
 * is transferred between servers. The token proves that the source server initiated
 * the transfer and allows the target server to validate the incoming connection without
 * Redis (cryptographic check) or with Redis (full ticket check).
 *
 * @property transferId Unique identifier for this transfer (UUID string)
 * @property playerId The player being transferred (UUID string)
 * @property fromServerId The originating server
 * @property toServerId The intended destination server
 * @property issuedAtMillis Epoch millis when the token was created
 * @property expiresAtMillis Epoch millis after which the token is invalid
 */
@Serializable
data class TransferToken(
    val transferId: String,
    val playerId: String,
    val fromServerId: String,
    val toServerId: String,
    val issuedAtMillis: Long,
    val expiresAtMillis: Long
)

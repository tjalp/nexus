package net.tjalp.nexus.punishment

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Represents a punishment given to a user.
 *
 * @param type The type of punishment
 * @param severity The severity of the punishment
 * @param duration The duration of the punishment
 * @param reason The reason for the punishment
 * @param timestamp The timestamp when the punishment was issued
 * @param issuedBy The issuer of the punishment
 * @param caseId The unique case ID of the punishment
 */
@OptIn(ExperimentalTime::class)
@Serializable
@SerialName("Punishment")
data class Punishment(
    val type: PunishmentType,
    val severity: PunishmentSeverity,
    val duration: Duration,
    val reason: String,
    val timestamp: Instant,
    val issuedBy: String,
    val caseId: String,
) {

    /**
     * Whether the punishment is still active
     */
    val isActive: Boolean
        get() {
            val now = Clock.System.now()
            val expiryTime = timestamp + duration
            return now < expiryTime
        }

    /**
     * The expiration time of the punishment
     */
    val expiresAt: Instant get() = timestamp + duration

    companion object {

        /**
         * Generate a case identifier
         *
         * @param type The punishment type
         * @return Generated case ID
         */
        fun generateCaseId(type: PunishmentType): String {
            val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
            val id = (1..6)
                .map { allowedChars.random() }
                .joinToString("")

            return type.prefix + "-" + id
        }
    }
}
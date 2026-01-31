package net.tjalp.nexus.feature.punishments

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Represents a punishment given to a user.
 *
 * @param type The type of punishment
 * @param severity The severity of the punishment
 * @param reason The reason for the punishment
 * @param timestamp The timestamp when the punishment was issued
 * @param issuedBy The issuer of the punishment
 * @param caseId The unique case ID of the punishment
 */
@OptIn(ExperimentalTime::class)
data class Punishment(
    val type: PunishmentType,
    val severity: PunishmentSeverity,
    val reason: String,
    val timestamp: Instant,
    val issuedBy: String,
    val caseId: String,
) {

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
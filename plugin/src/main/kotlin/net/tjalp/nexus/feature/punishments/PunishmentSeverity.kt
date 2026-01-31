package net.tjalp.nexus.feature.punishments

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * A punishment severity states how severe a punishment is.
 *
 * @param translationKey The translation key for the severity
 * @param duration The duration of the punishment
 */
enum class PunishmentSeverity(
    val translationKey: String,
    val duration: Duration
) {
    WARNING("punishment.severity.warning", Duration.ZERO),
    MINOR("punishment.severity.minor", 3.hours),
    MODERATE("punishment.severity.moderate", 1.days),
    MAJOR("punishment.severity.major", 7.days),
    SEVERE("punishment.severity.severe", 30.days),
    CRITICAL("punishment.severity.critical", Duration.INFINITE)
}
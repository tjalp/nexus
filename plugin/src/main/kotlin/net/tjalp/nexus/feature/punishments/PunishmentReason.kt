package net.tjalp.nexus.feature.punishments

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.translatable

/**
 * Represents a reason for a punishment.
 *
 * @param key The unique key of the punishment reason.
 * @param title The title of the punishment reason.
 * @param reason The detailed reason of the punishment.
 */
enum class PunishmentReason(val key: String, val title: Component, val reason: Component) {
    INAPPROPRIATE_BEHAVIOR(
        "inappropriate_behavior",
        translatable("punishment.reason.inappropriate_behavior.title"),
        translatable("punishment.reason.inappropriate_behavior.reason")
    ),
    SCAMMING(
        "scamming",
        translatable("punishment.reason.scamming.title"),
        translatable("punishment.reason.scamming.reason")
    ),
    DEATH_THREAT(
        "death_threat",
        translatable("punishment.reason.death_threat.title"),
        translatable("punishment.reason.death_threat.reason")
    ),
    CHEATING(
        "cheating",
        translatable("punishment.reason.cheating.title"),
        translatable("punishment.reason.cheating.reason")
    ),
    DISRESPECT(
        "disrespect",
        translatable("punishment.reason.disrespect.title"),
        translatable("punishment.reason.disrespect.reason")
    ),
    RACISM(
        "racism",
        translatable("punishment.reason.racism.title"),
        translatable("punishment.reason.racism.reason")
    ),
    PROFANITY(
        "profanity",
        translatable("punishment.reason.profanity.title"),
        translatable("punishment.reason.profanity.reason")
    ),
    GRIEFING(
        "griefing",
        translatable("punishment.reason.griefing.title"),
        translatable("punishment.reason.griefing.reason")
    ),
    SPAM(
        "spam",
        translatable("punishment.reason.spam.title"),
        translatable("punishment.reason.spam.reason")
    ),
    NO_REASON(
        "no_reason",
        translatable("punishment.reason.no_reason.title"),
        translatable("punishment.reason.no_reason.reason")
    )
}
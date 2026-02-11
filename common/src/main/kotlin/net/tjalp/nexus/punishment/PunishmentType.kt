package net.tjalp.nexus.punishment

/**
 * Values representing different types of punishments
 * that can be applied to a user.
 *
 * @param prefix The key associated with the punishment type
 */
enum class PunishmentType(val prefix: String) {

    /** A ban punishment prevents a player from joining the server */
    BAN("B"),

    /** A mute punishment prevents a player from sending messages */
    MUTE("M"),

    /** A warning punishment serves as a notice to the player */
    WARNING("W"),

    /** A kick punishment immediately removes the player from the server */
    KICK("K")
}
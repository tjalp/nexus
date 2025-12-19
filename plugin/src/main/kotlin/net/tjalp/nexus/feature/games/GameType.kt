package net.tjalp.nexus.feature.games

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage.miniMessage

/**
 * Types of games available in the Nexus system.
 *
 * @param friendlyName A user-friendly name for the game type.
 */
enum class GameType(val friendlyName: Component) {
    FROSTBALL_FRENZY(miniMessage().deserialize("<gradient:#D4F1F8:#71A6D1>Frostball Frenzy")),
}
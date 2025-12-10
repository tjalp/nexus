package net.tjalp.nexus.feature.games

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text

/**
 * Types of games available in the Nexus system.
 *
 * @param friendlyName A user-friendly name for the game type.
 */
enum class GameType(val friendlyName: Component) {
    SNOWBALL_FIGHT(text("Snowball Fight")),
}
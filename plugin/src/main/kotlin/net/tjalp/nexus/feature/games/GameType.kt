package net.tjalp.nexus.feature.games

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.minimessage.MiniMessage.miniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText
import net.tjalp.nexus.util.translate
import java.util.*

/**
 * Types of games available in the Nexus system.
 *
 * @param friendlyName A user-friendly name for the game type.
 */
enum class GameType(val friendlyName: Component, val formattedName: (Locale) -> Component = { friendlyName }) {
    FROSTBALL_FRENZY(translatable("game.frostball_frenzy.name"), { locale ->
        val plain = plainText().serialize(translatable("game.frostball_frenzy.name").translate(locale))

        miniMessage().deserialize("<gradient:#D4F1F8:#71A6D1>$plain")
    }),
}
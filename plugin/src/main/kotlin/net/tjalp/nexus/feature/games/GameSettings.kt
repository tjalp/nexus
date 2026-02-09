package net.tjalp.nexus.feature.games

import net.kyori.adventure.text.Component

/**
 * Represents a typed key for a game setting.
 */
data class GameSettingKey<T>(val id: String)

data class GameSetting<T>(
    val key: GameSettingKey<T>,
    val displayName: Component,
    val description: Component? = null,
    val isMutable: Boolean = true,
    var value: T
)

class GameSettings(
    private val settings: MutableMap<String, GameSetting<*>>
) {

    fun all(): List<GameSetting<*>> = settings.values.toList()

    fun <T> getSetting(key: GameSettingKey<T>): GameSetting<T>? {
        @Suppress("UNCHECKED_CAST")
        return settings[key.id] as? GameSetting<T>
    }

    fun <T> get(key: GameSettingKey<T>): T {
        return getSetting(key)?.value
            ?: error("Missing setting '${key.id}'")
    }

    fun <T> set(key: GameSettingKey<T>, value: T): Boolean {
        val setting = getSetting(key) ?: return false
        if (!setting.isMutable) return false
        setting.value = value
        return true
    }

    fun copyForGame(): GameSettings {
        val copied = settings.mapValues { (_, setting) ->
            @Suppress("UNCHECKED_CAST")
            (setting as GameSetting<Any?>).copy()
        }.toMutableMap()

        return GameSettings(copied)
    }

    companion object {
        fun of(vararg setting: GameSetting<*>): GameSettings {
            return GameSettings(setting.associateBy { it.key.id }.toMutableMap())
        }
    }
}

object GameSettingKeys {
    val MIN_PLAYERS = GameSettingKey<Int>("minPlayers")
    val MAX_PLAYERS = GameSettingKey<Int>("maxPlayers")
    val RESTART_VOTE_THRESHOLD = GameSettingKey<Double>("restartVoteThreshold")
}

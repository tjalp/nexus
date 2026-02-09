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
        val setting = settings[key.id] ?: return null
        @Suppress("UNCHECKED_CAST")
        return setting as? GameSetting<T>
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
            setting.copy(value = copyValue(setting.value))
        }.toMutableMap()

        return GameSettings(copied)
    }

    /**
     * Performs a shallow copy of common collection types. Nested mutable collections remain shared,
     * so prefer immutable values or pre-deep-copied structures for complex settings.
     */
    private fun copyValue(value: Any?): Any? = when (value) {
        is MutableList<*> -> value.toMutableList()
        is List<*> -> value.toList()
        is MutableSet<*> -> value.toMutableSet()
        is Set<*> -> value.toSet()
        is MutableMap<*, *> -> value.toMutableMap()
        is Map<*, *> -> value.toMap()
        else -> value
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

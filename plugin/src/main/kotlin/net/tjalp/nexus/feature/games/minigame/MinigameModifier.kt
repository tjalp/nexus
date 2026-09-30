package net.tjalp.nexus.feature.games.minigame

import org.bukkit.entity.Entity
import org.bukkit.entity.Projectile

interface MinigameModifier {
    val id: String

    fun onProjectileHit(game: MinigameGame, shooter: Entity, target: Entity, projectile: Projectile) {}
}

object MinigameModifierRegistry {
    private val modifiers = mutableMapOf<String, MinigameModifier>()

    fun register(modifier: MinigameModifier) {
        modifiers[modifier.id] = modifier
    }

    fun get(id: String): MinigameModifier? = modifiers[id]
}

package net.tjalp.nexus.feature.games.frostball_frenzy

import net.tjalp.nexus.feature.games.minigame.MinigameGame
import net.tjalp.nexus.feature.games.minigame.MinigameModifier
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile

object FrostballKnockbackModifier : MinigameModifier {
    override val id: String = "frostball_knockback"

    override fun onProjectileHit(game: MinigameGame, shooter: Entity, target: Entity, projectile: Projectile) {
        if (target is Player) {
            val normalized = projectile.velocity.clone().normalize()
            target.knockback(2.0, -normalized.x, -normalized.z)
            target.damage(0.0, projectile)
        }
    }
}

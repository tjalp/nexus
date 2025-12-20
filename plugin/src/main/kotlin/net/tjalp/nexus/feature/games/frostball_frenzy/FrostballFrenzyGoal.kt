package net.tjalp.nexus.feature.games.frostball_frenzy

import com.destroystokyo.paper.entity.ai.Goal
import com.destroystokyo.paper.entity.ai.GoalKey
import com.destroystokyo.paper.entity.ai.GoalType
import com.destroystokyo.paper.entity.ai.GoalType.*
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.games.currentGame
import org.bukkit.NamespacedKey
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Snowball
import java.util.*
import kotlin.math.pow

class FrostballFrenzyGoal(
    private val mob: Mob
) : Goal<Mob> {

    var cooldown = 0

    override fun shouldActivate(): Boolean {
        val game = mob.currentGame ?: return false

        return game.currentPhase is FrostballFrenzyFightPhase
    }

    override fun stop() {
        mob.target = null
        mob.pathfinder.stopPathfinding()
    }

    override fun tick() {
        if (cooldown > 0) cooldown--
        val game = mob.currentGame ?: run {
            stop()
            return
        }
        val closestPlayer = game.participants.minus(mob)
            .minByOrNull { it.location.distanceSquared(mob.location) }
            ?: run {
                stop()
                return
            }
        val distanceSquared = mob.location.distanceSquared(closestPlayer.location)
        val hasLineOfSight = mob.hasLineOfSight(closestPlayer)

        if (distanceSquared > 10.0.pow(2) || !hasLineOfSight) {
            mob.pathfinder.moveTo(closestPlayer.location)
        } else mob.pathfinder.stopPathfinding()

//        if (closestPlayer is LivingEntity) mob.target = closestPlayer

        if (hasLineOfSight && distanceSquared <= 20.0.pow(2) && cooldown <= 0) {
            val location = if (closestPlayer is LivingEntity) closestPlayer.eyeLocation else closestPlayer.location
            mob.launchProjectile(Snowball::class.java, location.toVector().subtract(mob.eyeLocation.toVector()).normalize().multiply(1.6))
            cooldown = 8
        }
    }

    override fun getKey(): GoalKey<Mob> = KEY

    override fun getTypes(): EnumSet<GoalType> = EnumSet.of(
        MOVE, LOOK, TARGET, UNKNOWN_BEHAVIOR
    )

    companion object {
        val KEY = GoalKey.of(Mob::class.java, NamespacedKey(NexusPlugin, "frostball_frenzy_goal"))
    }
}
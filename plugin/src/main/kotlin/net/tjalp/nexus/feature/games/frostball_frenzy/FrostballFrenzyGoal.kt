package net.tjalp.nexus.feature.games.frostball_frenzy

import com.destroystokyo.paper.entity.ai.Goal
import com.destroystokyo.paper.entity.ai.GoalKey
import com.destroystokyo.paper.entity.ai.GoalType
import com.destroystokyo.paper.entity.ai.GoalType.*
import net.kyori.adventure.key.Key.key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.sound.Sound.sound
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.games.currentGame
import org.bukkit.NamespacedKey
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Snowball
import java.util.*
import java.util.concurrent.ThreadLocalRandom
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

        mob.lookAt(closestPlayer)

        if (distanceSquared > 10.0.pow(2) || !hasLineOfSight) {
            mob.pathfinder.moveTo(closestPlayer.location, 1.25)
        } else mob.pathfinder.stopPathfinding()

        if (hasLineOfSight && distanceSquared <= 20.0.pow(2) && cooldown <= 0) {
            val random = ThreadLocalRandom.current()
            val location = if (closestPlayer is LivingEntity) closestPlayer.eyeLocation.clone().add(0.0, 0.2, 0.0) else closestPlayer.location.clone().add(0.0, 0.2, 0.0)
            val launchVector = location.toVector().subtract(mob.eyeLocation.toVector()).normalize()
            val slightlyRandomizedVector = launchVector.clone().apply {
                val spread = 0.2
                this.x += (random.nextDouble() - 0.5) * spread
                this.y += (random.nextDouble() - 0.5) * spread
                this.z += (random.nextDouble() - 0.5) * spread
                this.normalize()
            }.multiply(1.6)

            mob.launchProjectile(
                Snowball::class.java,
                slightlyRandomizedVector
            )
            mob.world.playSound(
                sound(
                    key("entity.snowball.throw"),
                    Sound.Source.NEUTRAL,
                    1f,
                    0.4f / (random.nextFloat() * 0.4f + 0.8f)
                ), mob
            )
            mob.swingMainHand()
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
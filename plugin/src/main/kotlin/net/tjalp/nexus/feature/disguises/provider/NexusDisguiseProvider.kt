package net.tjalp.nexus.feature.disguises.provider

import net.kyori.adventure.util.TriState
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.NexusServices
import net.tjalp.nexus.feature.disguises.DisguiseProvider
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityTargetEvent

class NexusDisguiseProvider : DisguiseProvider {

    private val disguises = HashMap<Entity, Entity>()
    private val nexus; get() = NexusServices.get<NexusPlugin>()
    private val listener = NexusDisguiseListener().also { it.register() }

    override fun disguise(entity: Entity, entityType: EntityType) {
        undisguise(entity)

        entity.isVisibleByDefault = false

        val disguiseEntity = entity.world.spawnEntity(entity.location, entityType, CreatureSpawnEvent.SpawnReason.CUSTOM) {
            disguises[entity] = it
            it.isPersistent = false
            it.setGravity(false)

            if (entity is Player) entity.hideEntity(nexus, it)
            if (it is Mob) it.server.mobGoals.removeAllGoals(it)
        }

        disguiseEntity.scheduler.runAtFixedRate(nexus, {
            disguiseEntity.apply {
                isSneaking = entity.isSneaking
                visualFire = TriState.byBoolean(entity.fireTicks > 0)
                isGlowing = entity.isGlowing
                isSilent = entity.isSilent
                isInvulnerable = entity.isInvulnerable
                if (entity is LivingEntity && this is LivingEntity) {
                    this.equipment?.armorContents = entity.equipment?.armorContents ?: arrayOf()
                    this.equipment?.setItemInMainHand(entity.equipment?.itemInMainHand)
                    this.equipment?.setItemInOffHand(entity.equipment?.itemInOffHand)
                }
            }
            disguiseEntity.teleport(entity)
        }, null, 1, 1)
    }

    override fun undisguise(entity: Entity) {
        val disguisedEntity = disguises.remove(entity)
        disguisedEntity?.remove()
        entity.isVisibleByDefault = true
    }

    override fun getDisguise(entity: Entity): EntityType? = disguises[entity]?.type

    override fun dispose() {
        disguises.iterator().forEach { undisguise(it.key) }
        listener.unregister()
    }

    inner class NexusDisguiseListener : Listener {

        private var damageMethodWasCalled = false

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        fun on(event: EntityDamageEvent) {
            val entity = event.entity

            if (damageMethodWasCalled) {
                damageMethodWasCalled = false
                return
            }

            if (entity.isInvulnerable) return

            if (getDisguise(entity) != null && event.finalDamage > 0) {
                (disguises[entity] as? LivingEntity)?.let {
                    it.playHurtAnimation(0f)
                    it.hurtSound?.let { sound -> it.world.playSound(it, sound, 1f, 1f) }
                }
            }

            val disguisedEntity = disguises.entries.firstOrNull { it.value == event.entity }?.key ?: return

            if (disguisedEntity is Damageable) {
                damageMethodWasCalled = true
                disguisedEntity.damage(event.finalDamage, event.damageSource)
            }

            event.damage = 0.0
        }

        @EventHandler
        fun on(event: EntityDeathEvent) {
            val entity = event.entity

            if (getDisguise(entity) != null) {
                undisguise(entity)
                return
            }

            val disguisedEntity = disguises.entries.firstOrNull { it.value == entity }?.key ?: return
            undisguise(disguisedEntity)
            event.isCancelled = true
        }

        @EventHandler
        fun on(event: EntityTargetEvent) {
            val entity = event.entity
            val target = event.target
            val mob = entity as? Mob
            val previousTarget = mob?.target
            val disguisedEntity = disguises.entries.firstOrNull { it.value == previousTarget }?.key
            val disguiseEntity = disguises[entity]

            if (target == disguiseEntity) {
                event.isCancelled = true
                return
            }

            if (previousTarget != null && target == null && disguisedEntity != null) {
                event.isCancelled = true
                return
            }

            if (target == null || getDisguise(target) == null) return

            event.isCancelled = true
        }
    }
}


package net.tjalp.nexus.feature.disguises.provider

import net.kyori.adventure.util.TriState
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.NexusServices
import net.tjalp.nexus.feature.disguises.DisguiseProvider
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.bukkit.Bukkit
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

            if (entity is Player) entity.hideEntity(nexus, it)
            if (it is LivingEntity) it.setAI(false)
            if (it is Mob) it.server.mobGoals.removeAllGoals(it)
        }

        entity.scheduler.runAtFixedRate(nexus, {
            disguiseEntity.isSneaking = entity.isSneaking
            disguiseEntity.apply {
                isSneaking = entity.isSneaking
                visualFire = TriState.byBoolean(entity.fireTicks > 0)
                isGlowing = entity.isGlowing
                isSilent = entity.isSilent
                isInvulnerable = entity.isInvulnerable
                if (entity is LivingEntity && it is LivingEntity) {
                    Bukkit.broadcastMessage("is living")
                    it.equipment?.armorContents = entity.equipment?.armorContents ?: arrayOf()
                    it.equipment?.setItemInMainHand(entity.equipment?.itemInMainHand)
                    it.equipment?.setItemInOffHand(entity.equipment?.itemInOffHand)
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

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        fun on(event: EntityDamageEvent) {
            val entity = event.entity

            if (entity.isInvulnerable) return

            val disguisedEntity = disguises.entries.firstOrNull { it.value == event.entity }?.key ?: return

            if (disguisedEntity is Damageable) {
                disguisedEntity.damage(event.finalDamage, event.damageSource)
            }

            if (entity is LivingEntity) entity.playHurtAnimation(0f)

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
//            Bukkit.broadcast(text("received target event, target = ${event.target?.type}, entity = ${event.entity.type}"))
            val target = event.target ?: return

            if (getDisguise(target) == null) return
//            if (target !is Player) return

//            Bukkit.broadcast(text("cancelling target event, target = ${target.type}"))

            event.isCancelled = true
        }
    }
}


package net.tjalp.nexus.feature.disguises.provider

import io.papermc.paper.event.player.PlayerArmSwingEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.Component.translatable
import net.tjalp.nexus.Constants.COMPLEMENTARY_COLOR
import net.tjalp.nexus.Constants.PRIMARY_COLOR
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.disguises.DisguiseFeature
import net.tjalp.nexus.feature.disguises.DisguiseProvider
import net.tjalp.nexus.scheduler.ticks
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.bukkit.EntityEffect
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.damage.DamageSource
import org.bukkit.damage.DamageType
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.*
import org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK
import org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.vehicle.VehicleDamageEvent
import org.bukkit.event.vehicle.VehicleDestroyEvent

class NexusDisguiseProvider : DisguiseProvider {

    private val disguises = HashMap<Entity, Entity>()
    private val listener = NexusDisguiseListener().also { it.register() }

    init {
        DisguiseFeature.scheduler.launch {
            while (isActive) {
                disguises.forEach { (entity, disguise) -> sendStatus(entity, disguise.type) }
                delay(15.ticks)
            }
        }
    }

    override fun disguise(entity: Entity, entityType: EntityType) {
        undisguise(entity, sendMessage = false)

        entity.isVisibleByDefault = false
        entity.isSilent = true

        val disguiseEntity =
            entity.world.spawnEntity(entity.location, entityType, CreatureSpawnEvent.SpawnReason.CUSTOM) {
                disguises[entity] = it
                it.isPersistent = false
                it.setGravity(false)

                if (entity is Player) entity.hideEntity(NexusPlugin, it)
                if (it is LivingEntity) {
                    it.setAI(false)
                    it.isCollidable = false
                }
                if (it is Mob) it.server.mobGoals.removeAllGoals(it)
            }

        disguiseEntity.scheduler.runAtFixedRate(NexusPlugin, {
            if (!entity.isValid) {
                undisguise(entity)
                return@runAtFixedRate
            }
            disguiseEntity.apply {
                isSneaking = entity.isSneaking
                pose = entity.pose
//                visualFire = TriState.byBoolean(entity.fireTicks > 0)
                isGlowing = entity.isGlowing
                isInvulnerable = entity.isInvulnerable
                fireTicks = entity.fireTicks
                if (entity is LivingEntity && this is LivingEntity) {
                    val relativeHealth = entity.health / entity.getAttribute(Attribute.MAX_HEALTH)!!.value
                    health = (relativeHealth * this.getAttribute(Attribute.MAX_HEALTH)!!.value)
                    val entityEquipment = entity.equipment ?: return@apply
                    this.equipment?.armorContents = entity.equipment?.armorContents ?: arrayOf()
                    this.equipment?.apply {
                        setItemInMainHand(entityEquipment.itemInMainHand, true)
                        setItemInOffHand(entityEquipment.itemInOffHand, true)
                        setHelmet(entityEquipment.helmet, true)
                        setChestplate(entityEquipment.chestplate, true)
                        setLeggings(entityEquipment.leggings, true)
                        setBoots(entityEquipment.boots, true)
                    }
                }
            }
            disguiseEntity.teleport(entity)
        }, { undisguise(entity, false) }, 1, 1)

        playSpawnEffects(disguiseEntity)
        sendStatus(entity, entityType)
    }

    override fun undisguise(entity: Entity) = this.undisguise(entity, removeEntity = true)

    /**
     * Undisguises an entity.
     *
     * @param entity The entity to undisguise.
     * @param removeEntity Whether to remove the disguise entity from the world.
     * @param sendMessage Whether to send an action bar message to the entity.
     */
    private fun undisguise(entity: Entity, removeEntity: Boolean = true, sendMessage: Boolean = true) {
        val disguiseEntity = disguises.remove(entity)
        if (removeEntity) disguiseEntity?.remove()
        entity.isSilent = false
        entity.isVisibleByDefault = true

        if (sendMessage && disguiseEntity != null) {
            entity.sendActionBar(text("You've been undisguised", PRIMARY_COLOR))
            playSpawnEffects(entity)
        }
    }

    override fun getDisguise(entity: Entity): EntityType? = disguises[entity]?.type

    override fun dispose() {
        disguises.iterator().forEach { undisguise(it.key) }
        listener.unregister()
    }

    /**
     * Sends an action bar message to the audience indicating their current disguise.
     *
     * @param audience The audience to send the message to.
     * @param disguiseType The type of entity the audience is disguised as.
     */
    private fun sendStatus(audience: Audience, disguiseType: EntityType) {
        audience.sendActionBar(
            text("You are currently disguised as ", PRIMARY_COLOR)
                .append(translatable(disguiseType.translationKey(), COMPLEMENTARY_COLOR))
        )
    }

    private fun playSpawnEffects(entity: Entity) {
        val bb = entity.boundingBox
        val world = entity.world
        val centerLocation = bb.center.toLocation(world)
        val particleCount = (bb.volume.toInt() * 20).coerceIn(20, 500)

        world.spawnParticle(
            Particle.CLOUD,
            centerLocation,
            particleCount,
            bb.widthX / 3,
            bb.height / 3,
            bb.widthZ / 3,
            0.025
        )
        world.playSound(entity.location, Sound.ENTITY_BREEZE_JUMP, .5f, 1f)
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

            if (getDisguise(entity) != null) {
                (disguises[entity] as? LivingEntity)?.let {
                    it.playHurtAnimation(0f)
                    it.hurtSound?.let { sound -> it.world.playSound(it, sound, 1f, 1f) }
                }
            }

            val disguisedEntity = disguises.entries.firstOrNull { it.value == event.entity }?.key ?: return
            val allowedDamageCauses = listOf(ENTITY_ATTACK, ENTITY_SWEEP_ATTACK)

            if (event.cause !in allowedDamageCauses) {
                event.isCancelled = true
                return
            }

            if (disguisedEntity is Damageable) {
                damageMethodWasCalled = true
                disguisedEntity.damage(event.damage, event.damageSource)
            }

            event.damage = 0.0
        }

        @EventHandler
        fun on(event: EntityDeathEvent) {
            val entity = event.entity

            if (getDisguise(entity) != null) {
                undisguise(entity, sendMessage = false)
                return
            }

            val disguisedEntity = disguises.entries.firstOrNull { it.value == entity }?.key ?: return
            undisguise(disguisedEntity)
            event.isCancelled = true
        }

        @EventHandler
        fun on(event: VehicleDestroyEvent) {
            val isDisguise = disguises.values.contains(event.vehicle)

            if (isDisguise) event.isCancelled = true
        }

        @EventHandler
        fun on(event: VehicleDamageEvent) {
            val disguisedEntity = disguises.entries.firstOrNull { it.value == event.vehicle }?.key ?: return
            val attacker = event.attacker

            if (attacker == null) {
                event.isCancelled = true
                return
            }

            if (disguisedEntity is Damageable) {
                val source = DamageSource.builder(DamageType.MOB_ATTACK)
                    .withCausingEntity(attacker)
                    .withDirectEntity(attacker)
                    .build()

                damageMethodWasCalled = true
                disguisedEntity.damage(event.damage, source)
            }

//            event.damage = 0.0
        }

        @EventHandler
        fun on(event: EntityMountEvent) {
            val isDisguise = disguises.values.contains(event.entity) || disguises.values.contains(event.mount)

            if (isDisguise) event.isCancelled = true
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

        @EventHandler
        fun on(event: PlayerInteractEntityEvent) {
            processInteraction(event)
        }

        // because armor stands are special
        @EventHandler
        fun on(event: PlayerInteractAtEntityEvent) {
            processInteraction(event)
        }

        private fun processInteraction(event: PlayerInteractEntityEvent) {
            val clickedEntity = event.rightClicked

            if (disguises.values.contains(clickedEntity)) event.isCancelled =  true
        }

        @EventHandler
        fun on(event: PlayerArmSwingEvent) {
            val disguiseEntity = disguises[event.player] as? LivingEntity ?: return

            disguiseEntity.swingHand(event.hand)
            disguiseEntity.playEffect(EntityEffect.ENTITY_ATTACK)
        }
    }
}


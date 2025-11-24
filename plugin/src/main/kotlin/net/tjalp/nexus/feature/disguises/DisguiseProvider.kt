package net.tjalp.nexus.feature.disguises

import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.spongepowered.configurate.reactive.Disposable

/**
 * Interface for disguise providers.
 */
interface DisguiseProvider : Disposable {

    /**
     * Disguises the given entity as the specified entity type.
     *
     * @param entity The entity to disguise.
     * @param entityType The type to disguise the entity as.
     */
    fun disguise(entity: Entity, entityType: EntityType)

    /**
     * Removes any disguise from the given entity.
     *
     * @param entity The entity to undisguise.
     */
    fun undisguise(entity: Entity)

    /**
     * Gets the current disguise of the given entity, if any.
     *
     * @param entity The entity to check.
     * @return The EntityType the entity is disguised as, or null if not disguised.
     */
    fun getDisguise(entity: Entity): EntityType?

    override fun dispose() {}
}
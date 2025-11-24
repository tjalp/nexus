package net.tjalp.nexus.feature.disguises.provider

import me.libraryaddict.disguise.DisguiseAPI
import me.libraryaddict.disguise.LibsDisguises
import me.libraryaddict.disguise.disguisetypes.DisguiseType
import me.libraryaddict.disguise.disguisetypes.MiscDisguise
import me.libraryaddict.disguise.disguisetypes.MobDisguise
import me.libraryaddict.disguise.disguisetypes.PlayerDisguise
import net.tjalp.nexus.feature.disguises.DisguiseProvider
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType

class LibsDisguisesDisguiseProvider : DisguiseProvider {

    init {
        LibsDisguises.getInstance().unregisterCommands(false)
    }

    override fun disguise(entity: Entity, entityType: EntityType) {
        val disguiseType = DisguiseType.getType(entityType)
        val disguise = if (disguiseType.isMisc) {
            MiscDisguise(disguiseType)
        } else if (disguiseType.isMob) {
            MobDisguise(disguiseType)
        } else {
            PlayerDisguise(entity.name)
        }
        DisguiseAPI.disguiseEntity(entity, disguise)
    }

    override fun undisguise(entity: Entity) {
        DisguiseAPI.undisguiseToAll(entity)
    }

    override fun getDisguise(entity: Entity): EntityType? = DisguiseAPI.getDisguise(entity)?.entity?.type
}
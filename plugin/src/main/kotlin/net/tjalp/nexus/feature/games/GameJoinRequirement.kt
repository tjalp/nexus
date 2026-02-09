package net.tjalp.nexus.feature.games

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor.RED
import org.bukkit.entity.Entity
import org.bukkit.entity.Player

interface GameJoinRequirement {
    val id: String
    val description: Component
    val isMutable: Boolean

    fun isSatisfied(entity: Entity, game: Game): RequirementResult

    fun copyRequirement(): GameJoinRequirement
}

data class RequirementResult(
    val allowed: Boolean,
    val message: Component? = null
)

abstract class ToggleableJoinRequirement(
    override val id: String,
    override val description: Component,
    override val isMutable: Boolean,
    var enabled: Boolean = true
) : GameJoinRequirement {

    final override fun isSatisfied(entity: Entity, game: Game): RequirementResult {
        if (!enabled) return RequirementResult(true)
        return check(entity, game)
    }

    protected abstract fun check(entity: Entity, game: Game): RequirementResult
}

class InventoryEmptyRequirement(
    enabled: Boolean = true,
    override val isMutable: Boolean = true
) : ToggleableJoinRequirement(
    id = "inventoryEmpty",
    description = text("Inventory must be empty"),
    isMutable = isMutable,
    enabled = enabled
) {

    override fun check(entity: Entity, game: Game): RequirementResult {
        if (entity !is Player) return RequirementResult(true)

        val hasItems = entity.inventory.contents.any { it != null && !it.type.isAir }
        val hasArmor = entity.inventory.armorContents.any { it != null && !it.type.isAir }

        return if (hasItems || hasArmor) {
            RequirementResult(false, text("Inventory must be empty to join this game.", RED))
        } else RequirementResult(true)
    }

    override fun copyRequirement(): GameJoinRequirement {
        return InventoryEmptyRequirement(enabled = enabled, isMutable = isMutable)
    }
}

class BoundsRequirement(
    var bounds: GameBounds?,
    enabled: Boolean = true,
    override val isMutable: Boolean = true
) : ToggleableJoinRequirement(
    id = "withinBounds",
    description = text("Must be inside game bounds"),
    isMutable = isMutable,
    enabled = enabled
) {

    override fun check(entity: Entity, game: Game): RequirementResult {
        val activeBounds = bounds ?: game.bounds
        val location = entity.location

        return if (activeBounds == null || activeBounds.contains(location)) {
            RequirementResult(true)
        } else RequirementResult(false, text("You must be inside the game bounds.", RED))
    }

    override fun copyRequirement(): GameJoinRequirement {
        return BoundsRequirement(bounds = bounds, enabled = enabled, isMutable = isMutable)
    }
}

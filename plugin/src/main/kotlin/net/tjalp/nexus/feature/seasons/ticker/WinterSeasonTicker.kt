package net.tjalp.nexus.feature.seasons.ticker

import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.seasons.SeasonTicker
import org.bukkit.GameRule
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockSupport
import org.bukkit.block.data.Levelled
import org.bukkit.block.data.Waterlogged
import org.bukkit.block.data.type.Snow

object WinterSeasonTicker : SeasonTicker {

    override fun condition(world: World): Boolean =
        !world.isClearWeather && (world.getGameRuleValue(GameRule.SNOW_ACCUMULATION_HEIGHT) ?: 1) > 0

    override fun tick(block: Block) {
        val blockAbove = block.getRelative(BlockFace.UP)
        val allowSnowFormation = NexusPlugin.configuration.modules.seasons.winter.allowSnowFormation
        val allowIceFormation = NexusPlugin.configuration.modules.seasons.winter.allowIceFormation

        // todo check whether face is full, instead of just checking whether block is sturdy on top or leaves.
        // unfortunately the api does not expose this, so nms would have to be used
        if (allowSnowFormation && (blockAbove.type == Material.SNOW || blockAbove.type.isAir && !Tag.ICE.isTagged(block.type) && !Tag.SNOW_LAYER_CANNOT_SURVIVE_ON.isTagged(
                block.type
            ) && (block.blockData.isFaceSturdy(
                BlockFace.UP,
                BlockSupport.RIGID
            ) || Tag.LEAVES.isTagged(block.type))
        )) {
            (blockAbove.blockData as? Snow)?.let {
                if ((block.world.getGameRuleValue(GameRule.SNOW_ACCUMULATION_HEIGHT)
                        ?: 1) <= it.layers
                ) return@let
                it.layers += 1
                blockAbove.blockData = it
            } ?: run { blockAbove.type = Material.SNOW }
        }

        if (allowIceFormation && block.type == Material.WATER && (block.blockData as Levelled).level == 0) {
            val requiresWater = NexusPlugin.configuration.modules.seasons.winter.iceFormationRequiresSurroundedByWater

            if (requiresWater) {
                val faces = listOf(
                    BlockFace.NORTH,
                    BlockFace.EAST,
                    BlockFace.SOUTH,
                    BlockFace.WEST
                )
                val surroundedByWater = faces.all { face ->
                    val relative = block.getRelative(face)
//                    val isLiquidBlock = (relative as CraftBlock).handle is LiquidBlockContainer
                    val isWaterLogged = relative is Waterlogged && relative.isWaterlogged
                    val isWaterSource = relative.type == Material.WATER && (relative.blockData as Levelled).level == 0
                    val isIce = Tag.ICE.isTagged(relative.type)

                    isWaterLogged || isWaterSource || isIce
                }
                if (surroundedByWater) {
                    block.type = Material.ICE
                }
            } else block.type = Material.ICE
        }
    }
}
package net.tjalp.nexus.feature.seasons.ticker

import net.tjalp.nexus.feature.seasons.SeasonTicker
import net.tjalp.nexus.util.asBlockPos
import net.tjalp.nexus.util.asNmsBiome
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace

object DefaultSeasonTicker : SeasonTicker {

    override fun condition(world: World): Boolean = true

    @Suppress("DEPRECATION")
    override fun tick(block: Block) {
        val blockAbove = block.getRelative(BlockFace.UP)
        val temperature =
            block.computedBiome.asNmsBiome().getTemperature(block.location.asBlockPos(), block.world.seaLevel)
        val temperatureAbove = blockAbove.computedBiome.asNmsBiome()
            .getTemperature(blockAbove.location.asBlockPos(), blockAbove.world.seaLevel)

        // remove snow if temperature is above freezing point
        if (blockAbove.type == Material.SNOW && temperatureAbove >= 0.15) blockAbove.type = Material.AIR
        if (block.type == Material.ICE && temperature >= 0.15) block.type = Material.FROSTED_ICE
    }
}
package net.tjalp.nexus.feature.seasons

import org.bukkit.World
import org.bukkit.block.Block

interface SeasonTicker {

    fun condition(world: World): Boolean

    fun tick(block: Block)
}
package net.tjalp.nexus.feature.games

import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.WorldCreator

sealed class GameWorldSpec {
    abstract val isTemporary: Boolean

    abstract fun resolveWorld(gameId: String): World

    data class Existing(val worldName: String) : GameWorldSpec() {
        override val isTemporary: Boolean = false

        override fun resolveWorld(gameId: String): World {
            return Bukkit.getWorld(worldName) ?: Bukkit.createWorld(WorldCreator(worldName))
                ?: error("Failed to load world '$worldName'")
        }
    }

    data class Temporary(
        val worldName: String? = null,
        val environment: World.Environment = World.Environment.NORMAL,
        val seed: Long? = null
    ) : GameWorldSpec() {
        override val isTemporary: Boolean = true

        override fun resolveWorld(gameId: String): World {
            val resolvedName = worldName ?: "game-$gameId"
            val creator = WorldCreator(resolvedName).environment(environment)
            seed?.let { creator.seed(it) }

            return Bukkit.createWorld(creator)
                ?: error("Failed to create temporary world '$resolvedName'")
        }
    }

    companion object {
        fun mainWorld(): GameWorldSpec = Existing("world")

        fun nether(): GameWorldSpec = Existing("world_nether")

        fun theEnd(): GameWorldSpec = Existing("world_the_end")

        fun temporary(
            worldName: String? = null,
            environment: World.Environment = World.Environment.NORMAL,
            seed: Long? = null
        ): GameWorldSpec = Temporary(worldName = worldName, environment = environment, seed = seed)
    }
}
